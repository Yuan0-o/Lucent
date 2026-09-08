package com.lucent.app.data

import android.content.Context
import java.io.OutputStream
import kotlinx.coroutines.CancellationException

/**
 * The `.lcb` payload framing shared by export and import (extracted from BackupManager so the
 * stream protocol is one self-contained, reviewable unit — P0-7).
 *
 * A framed payload is: FRAME_MAGIC + FRAME_VERSION, a length-prefixed manifest JSON, then zero or
 * more blob frames (length-prefixed name + 8-byte big-endian length + raw bytes). A legacy payload
 * is bare JSON and begins with '{'. All framing is streamed so multi-gigabyte model files never
 * exist in memory whole; every length field is bounded so a corrupt or hostile file cannot demand
 * a huge allocation.
 */
internal object BackupFrames {

    fun throwIfCancelled(cancelled: (() -> Boolean)?) {
        if (cancelled != null && cancelled()) {
            throw CancellationException("Backup operation cancelled")
        }
    }

    fun writeInt(out: OutputStream, value: Int) {
        out.write((value ushr 24) and 0xFF); out.write((value ushr 16) and 0xFF)
        out.write((value ushr 8) and 0xFF); out.write(value and 0xFF)
    }

    fun writeLong(out: OutputStream, value: Long) {
        for (shift in 56 downTo 0 step 8) out.write(((value ushr shift) and 0xFF).toInt())
    }

    const val FRAME_MAGIC = 0x4C.toByte()   // 'L'
    const val FRAME_VERSION = 1.toByte()

    // Framed blob names are name-spaced by destination: an imported font travels as
    // "font:<fileName>" and is routed to FontStore on restore; any other name is a local model
    // file. The prefix never reaches the filesystem (restore strips it before resolving a target).
    const val FONT_BLOB_PREFIX = "font:"

    // A blob name can only be a slot file name ("model_<id>.gguf", legacy "model.gguf") or a
    // font name behind FONT_BLOB_PREFIX — all short. Anything longer means a corrupt or hostile
    // length field, and rejecting it early stops that field from provoking a huge allocation.
    const val MAX_BLOB_NAME_BYTES = 1024

    // Upper bound for the framed manifest. Real manifests are dominated by inlined attachments,
    // which the app itself caps well below this; the bound exists so a corrupt or hand-forged
    // length can never demand a multi-gigabyte allocation (or one beyond what a JVM array can
    // even hold) before JSON parsing gets a chance to reject the file.
    const val MAX_MANIFEST_BYTES = 1_200_000_000

    /**
     * What one streaming pass over a decrypted payload found. [manifestJson] is always present;
     * [framed] says whether blob frames follow the manifest (even if zero of them do), which is
     * what commit needs to know to bother with a second pass.
     */
    data class PayloadScan(
        val manifestJson: String,
        val framed: Boolean,
        val modelCount: Int,
        val modelBytes: Long,
        val fontCount: Int,
        val fontBytes: Long
    )

    /** Read exactly [len] bytes or throw — the framing has no optional fields. */
    fun readFully(input: java.io.InputStream, buffer: ByteArray, len: Int, cancelled: (() -> Boolean)? = null) {
        var read = 0
        while (read < len) {
            throwIfCancelled(cancelled)
            val n = input.read(buffer, read, len - read)
            if (n < 0) throw java.io.EOFException("Backup payload ended early")
            read += n
        }
    }

    /**
     * Discard exactly [count] plaintext bytes by READING them. Never `InputStream.skip()`: on the
     * decrypting stream a skip would be delegated to the underlying ciphertext stream, silently
     * jumping the frame parser into the middle of a GCM frame. Reading through keeps every skipped
     * byte authenticated, which for a backup is a feature, not a cost.
     */
    fun skipFully(input: java.io.InputStream, count: Long, scratch: ByteArray, cancelled: (() -> Boolean)? = null) {
        var remaining = count
        while (remaining > 0) {
            throwIfCancelled(cancelled)
            val n = input.read(scratch, 0, minOf(remaining, scratch.size.toLong()).toInt())
            if (n < 0) throw java.io.EOFException("Backup payload ended early")
            remaining -= n
        }
    }

    /**
     * Read one big-endian Int, or null on a clean end-of-stream **before any byte** — which is how
     * the blob list legitimately ends. EOF in the middle of the four bytes is a truncated file.
     */
    fun readIntOrEnd(input: java.io.InputStream): Int? {
        val first = input.read()
        if (first < 0) return null
        var v = first and 0xFF
        for (i in 0 until 3) {
            val b = input.read()
            if (b < 0) throw java.io.EOFException("Backup payload ended early")
            v = (v shl 8) or (b and 0xFF)
        }
        return v
    }

    fun readLongFrom(input: java.io.InputStream): Long {
        var v = 0L
        for (i in 0 until 8) {
            val b = input.read()
            if (b < 0) throw java.io.EOFException("Backup payload ended early")
            v = (v shl 8) or (b.toLong() and 0xFF)
        }
        return v
    }

    /** Open [source] and hand back the decrypted plaintext stream, header already consumed. */
    fun openDecrypted(source: BackupManager.BackupSource, password: String?): java.io.InputStream {
        val raw = source.open()
        try {
            // The plaintext envelope header. Read (not skipped) so a source that cannot seek still
            // works, and re-parsed so key derivation matches this very stream.
            val head = ByteArray(30)
            try {
                readFully(raw, head, head.size)
            } catch (_: java.io.EOFException) {
                throw IllegalArgumentException(com.lucent.app.i18n.S.notLcbBackup)
            }
            val header = BackupCrypto.readHeader(head)
                ?: throw IllegalArgumentException(com.lucent.app.i18n.S.notLcbBackup)
            return BackupCrypto.decryptingStream(raw, header, password)
        } catch (t: Throwable) {
            // The decrypting wrapper owns the raw stream only once it exists; on any earlier
            // failure the stream would otherwise leak.
            try { raw.close() } catch (_: Throwable) {}
            throw t
        }
    }

    /**
     * One streaming pass over a decrypted payload: the manifest is read into memory (it is the one
     * part that has to be — JSON is parsed whole), and every blob frame after it is measured and
     * skipped. Nothing blob-sized is ever buffered, which is the entire crash fix: the old reader
     * held the full payload — model files included — as a ByteArray, and a multi-gigabyte backup
     * died in OutOfMemoryError before the preview dialog ever appeared.
     *
     * When [onBlob] is non-null it is offered each blob's (name, length, stream) and must consume
     * EXACTLY that many bytes (or throw); when null the blob is skipped here. Either way the walk
     * stays aligned on frame boundaries.
     */
    fun scanPayload(
        plain: java.io.InputStream,
        // Optional cooperative-cancel poll (see throwIfCancelled); checked between frames and
        // threaded into the byte-moving helpers so a cancel aborts within one 64 KB piece.
        cancelled: (() -> Boolean)? = null,
        onBlob: ((name: String, dataLen: Long, data: java.io.InputStream) -> Unit)? = null
    ): PayloadScan {
        val scratch = ByteArray(1 shl 16)

        // One byte decides framed vs legacy. A legacy payload is bare JSON and begins with '{'
        // (or whitespace); a framed one begins with FRAME_MAGIC. The consumed prefix is carried
        // into the legacy read below, so nothing is lost either way.
        val b0 = plain.read()
        if (b0 < 0) {
            // An empty payload. Let the JSON parser produce the one "damaged file" complaint the
            // callers already translate, instead of inventing a second error path here.
            return PayloadScan("", framed = false, 0, 0L, 0, 0L)
        }
        val b1 = plain.read()
        val framed = b0 == (FRAME_MAGIC.toInt() and 0xFF) && b1 == (FRAME_VERSION.toInt() and 0xFF)

        if (!framed) {
            // Legacy plain-JSON payload: the manifest is the whole stream, prefix included.
            val buffer = java.io.ByteArrayOutputStream(64 * 1024)
            buffer.write(b0)
            if (b1 >= 0) buffer.write(b1)
            while (true) {
                throwIfCancelled(cancelled)
                val n = plain.read(scratch)
                if (n < 0) break
                buffer.write(scratch, 0, n)
            }
            return PayloadScan(buffer.toString("UTF-8"), framed = false, 0, 0L, 0, 0L)
        }

        val jsonLen = readIntOrEnd(plain)
            ?: throw IllegalArgumentException("That backup couldn't be read — the file may be damaged.")
        if (jsonLen <= 0 || jsonLen > MAX_MANIFEST_BYTES) {
            throw IllegalArgumentException("That backup couldn't be read — the file may be damaged.")
        }
        val manifestBytes = ByteArray(jsonLen)
        try {
            readFully(plain, manifestBytes, jsonLen, cancelled)
        } catch (_: java.io.EOFException) {
            throw IllegalArgumentException("That backup couldn't be read — the file may be damaged.")
        }
        val manifestJson = String(manifestBytes, Charsets.UTF_8)

        // Walk the blob frames. The loop mirrors the old in-memory reader's tolerance exactly: a
        // clean end between frames is the normal stop, and a frame that cannot be read whole ends
        // the walk quietly — best-effort, so a truncated tail can't take down a restore whose
        // manifest (the user's actual data) already parsed.
        var modelCount = 0
        var modelBytes = 0L
        var fontCount = 0
        var fontBytes = 0L
        while (true) {
            throwIfCancelled(cancelled)
            val nameLen = try {
                readIntOrEnd(plain) ?: break
            } catch (_: java.io.EOFException) {
                break
            }
            if (nameLen <= 0 || nameLen > MAX_BLOB_NAME_BYTES) break
            val name: String
            val dataLen: Long
            try {
                val nameBytes = ByteArray(nameLen)
                readFully(plain, nameBytes, nameLen)
                name = String(nameBytes, Charsets.UTF_8)
                dataLen = readLongFrom(plain)
            } catch (_: java.io.EOFException) {
                break
            }
            if (dataLen < 0) break
            if (name.startsWith(FONT_BLOB_PREFIX)) {
                fontCount++
                fontBytes += dataLen
            } else {
                modelCount++
                modelBytes += dataLen
            }
            try {
                if (onBlob != null) onBlob(name, dataLen, plain) else skipFully(plain, dataLen, scratch, cancelled)
            } catch (_: java.io.EOFException) {
                // The advertised bytes weren't all there: a truncated file. Everything counted or
                // written so far stands; there is simply nothing further to walk.
                break
            }
        }
        return PayloadScan(manifestJson, framed = true, modelCount, modelBytes, fontCount, fontBytes)
    }

    /**
     * Copy one blob from the decrypted stream to its destination, returning (models, fonts)
     * written as 0/1 pairs. Called from inside the scan walk, so on ANY outcome it must leave the
     * stream advanced exactly [dataLen] bytes past where it started — a blob that fails to land on
     * disk is drained, not abandoned mid-frame, or every blob after it would be misread.
     *
     * A blob's name carries its destination: a [FONT_BLOB_PREFIX]-prefixed name is an imported
     * font (routed to FontStore with the prefix stripped), anything else is a local model file —
     * which is also what every pre-v11 backup contains, so old files keep restoring without a
     * version check. Each write is deliberately best-effort: one unwritable file (out of space, a
     * name the filesystem rejects) must not abort a restore that has already put the user's notes
     * back. Blob sizes are Long end to end, so a single model past 2 GB — impossible for the old
     * ByteArray reader even in principle — streams through correctly.
     */
    fun restoreOneBlob(
        context: Context,
        name: String,
        dataLen: Long,
        data: java.io.InputStream,
        wantModels: Boolean,
        wantFonts: Boolean,
        scratch: ByteArray,
        cancelled: (() -> Boolean)? = null
    ): Pair<Int, Int> {
        val isFont = name.startsWith(FONT_BLOB_PREFIX)
        if ((isFont && !wantFonts) || (!isFont && !wantModels)) {
            skipFully(data, dataLen, scratch, cancelled)
            return 0 to 0
        }
        var tmp: java.io.File? = null
        var out: java.io.OutputStream? = null
        var written = 0L
        try {
            val target = if (isFont) {
                FontStore.prepareRestoreTarget(context, name.removePrefix(FONT_BLOB_PREFIX))
            } else {
                com.lucent.app.local.LocalModelStore.prepareRestoreTarget(context, name)
            }
            // Straight to a temp file and renamed into place, so an interrupted restore can never
            // leave a half-written payload that the app would then try to load.
            val tmpFile = java.io.File(target.absolutePath + ".tmp")
            tmp = tmpFile
            val os = tmpFile.outputStream()
            out = os
            while (written < dataLen) {
                throwIfCancelled(cancelled)
                val n = data.read(scratch, 0, minOf(dataLen - written, scratch.size.toLong()).toInt())
                if (n < 0) throw java.io.EOFException("Backup payload ended early")
                os.write(scratch, 0, n)
                written += n
            }
            os.close()
            out = null
            if (target.exists()) target.delete()
            return if (tmpFile.renameTo(target)) {
                if (isFont) 0 to 1 else 1 to 0
            } else {
                tmpFile.delete()
                0 to 0
            }
        } catch (eof: java.io.EOFException) {
            // The stream itself ran dry — nothing more can be read, so tell the walk to stop.
            try { out?.close() } catch (_: Throwable) {}
            tmp?.delete()
            throw eof
        } catch (t: Throwable) {
            if (t is CancellationException) {
                // A user cancel, not a fault: clean up the half-written temp file and abort the
                // whole walk — do NOT drain and continue, the operation is being abandoned.
                try { out?.close() } catch (_: Throwable) {}
                tmp?.delete()
                throw t
            }
            // A destination-side failure (disk full, an unwritable name). The stream is fine, so
            // drain this blob's remaining bytes to stay frame-aligned, then carry on to the next.
            try { out?.close() } catch (_: Throwable) {}
            tmp?.delete()
            skipFully(data, dataLen - written, scratch)
            return 0 to 0
        }
    }
}
