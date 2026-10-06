package com.lucent.app.data

import okio.Path.Companion.toPath
import okio.buffer

import com.lucent.app.platform.PlatformContext

import kotlinx.coroutines.CancellationException

object BackupFrames {

    fun throwIfCancelled(cancelled: (() -> Boolean)?) {
        if (cancelled != null && cancelled()) {
            throw CancellationException("Backup operation cancelled")
        }
    }

    fun writeInt(out: okio.Sink, value: Int) {
        val buffer = okio.Buffer()
        buffer.writeByte((value ushr 24) and 0xFF); buffer.writeByte((value ushr 16) and 0xFF)
        buffer.writeByte((value ushr 8) and 0xFF); buffer.writeByte(value and 0xFF)
        out.write(buffer, 4)
    }

    fun writeLong(out: okio.Sink, value: Long) {
        val buffer = okio.Buffer()
        for (shift in 56 downTo 0 step 8) buffer.writeByte(((value ushr shift) and 0xFF).toInt())
        out.write(buffer, 8)
    }

    const val FRAME_MAGIC = 0x4C.toByte()
    const val FRAME_VERSION = 1.toByte()

    const val FONT_BLOB_PREFIX = "font:"

    const val HARNESS_BLOB_PREFIX = "harness:"

    const val MAX_BLOB_NAME_BYTES = 1024

    const val MAX_MANIFEST_BYTES = 1_200_000_000

    enum class BlobKind { MODEL, FONT, HARNESS }

    fun blobKind(name: String): BlobKind = when {
        name.startsWith(FONT_BLOB_PREFIX) -> BlobKind.FONT
        name.startsWith(HARNESS_BLOB_PREFIX) -> BlobKind.HARNESS
        else -> BlobKind.MODEL
    }

    data class PayloadScan(
        val manifestJson: String,
        val framed: Boolean,
        val modelCount: Int,
        val modelBytes: Long,
        val fontCount: Int,
        val fontBytes: Long,
        val harnessCount: Int = 0,
        val harnessBytes: Long = 0L
    )

    fun readFully(input: okio.Source, buffer: ByteArray, len: Int, cancelled: (() -> Boolean)? = null) {
        var read = 0
        while (read < len) {
            throwIfCancelled(cancelled)
            val n = input.read(buffer, read, len - read)
            if (n < 0) throw okio.EOFException("Backup payload ended early")
            read += n
        }
    }

    fun skipFully(input: okio.Source, count: Long, scratch: ByteArray, cancelled: (() -> Boolean)? = null) {
        var remaining = count
        while (remaining > 0) {
            throwIfCancelled(cancelled)
            val n = input.read(scratch, 0, minOf(remaining, scratch.size.toLong()).toInt())
            if (n < 0) throw okio.EOFException("Backup payload ended early")
            remaining -= n
        }
    }

    fun readIntOrEnd(input: okio.Source): Int? {
        val first = input.read()
        if (first < 0) return null
        var v = first and 0xFF
        for (i in 0 until 3) {
            val b = input.read()
            if (b < 0) throw okio.EOFException("Backup payload ended early")
            v = (v shl 8) or (b and 0xFF)
        }
        return v
    }

    fun readLongFrom(input: okio.Source): Long {
        var v = 0L
        for (i in 0 until 8) {
            val b = input.read()
            if (b < 0) throw okio.EOFException("Backup payload ended early")
            v = (v shl 8) or (b.toLong() and 0xFF)
        }
        return v
    }

    fun openDecrypted(source: BackupManager.BackupSource, password: String?): okio.Source {
        val raw = source.open()
        try {
            val head = ByteArray(30)
            try {
                readFully(raw, head, head.size)
            } catch (_: okio.EOFException) {
                throw IllegalArgumentException(com.lucent.app.i18n.S.notLcbBackup)
            }
            val header = BackupCrypto.readHeader(head)
                ?: throw IllegalArgumentException(com.lucent.app.i18n.S.notLcbBackup)
            return BackupCrypto.decryptingSource(raw, header, password)
        } catch (t: Throwable) {
            try { raw.close() } catch (_: Throwable) {}
            throw t
        }
    }

    fun scanPayload(
        plain: okio.Source,
        cancelled: (() -> Boolean)? = null,
        onBlob: ((name: String, dataLen: Long, data: okio.Source) -> Unit)? = null
    ): PayloadScan {
        val scratch = ByteArray(1 shl 16)

        val b0 = plain.read()
        if (b0 < 0) {
            return PayloadScan("", framed = false, 0, 0L, 0, 0L)
        }
        val b1 = plain.read()
        val framed = b0 == (FRAME_MAGIC.toInt() and 0xFF) && b1 == (FRAME_VERSION.toInt() and 0xFF)

        if (!framed) {
            val buffer = okio.Buffer()
            buffer.writeByte(b0)
            if (b1 >= 0) buffer.writeByte(b1)
            while (true) {
                throwIfCancelled(cancelled)
                val n = plain.read(scratch)
                if (n < 0) break
                buffer.write(scratch, 0, n)
            }
            return PayloadScan(buffer.readUtf8(), framed = false, 0, 0L, 0, 0L)
        }

        val jsonLen = readIntOrEnd(plain)
            ?: throw IllegalArgumentException("That backup couldn't be read — the file may be damaged.")
        if (jsonLen <= 0 || jsonLen > MAX_MANIFEST_BYTES) {
            throw IllegalArgumentException("That backup couldn't be read — the file may be damaged.")
        }
        val manifestBytes = ByteArray(jsonLen)
        try {
            readFully(plain, manifestBytes, jsonLen, cancelled)
        } catch (_: okio.EOFException) {
            throw IllegalArgumentException("That backup couldn't be read — the file may be damaged.")
        }
        val manifestJson = String(manifestBytes, Charsets.UTF_8)

        var modelCount = 0
        var modelBytes = 0L
        var fontCount = 0
        var fontBytes = 0L
        var harnessCount = 0
        var harnessBytes = 0L
        while (true) {
            throwIfCancelled(cancelled)
            val nameLen = try {
                readIntOrEnd(plain) ?: break
            } catch (_: okio.EOFException) {
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
            } catch (_: okio.EOFException) {
                break
            }
            if (dataLen < 0) break
            when (blobKind(name)) {
                BlobKind.FONT -> {
                    fontCount++
                    fontBytes += dataLen
                }
                BlobKind.HARNESS -> {
                    harnessCount++
                    harnessBytes += dataLen
                }
                BlobKind.MODEL -> {
                    modelCount++
                    modelBytes += dataLen
                }
            }
            try {
                if (onBlob != null) onBlob(name, dataLen, plain) else skipFully(plain, dataLen, scratch, cancelled)
            } catch (_: okio.EOFException) {
                break
            }
        }
        return PayloadScan(
            manifestJson, framed = true, modelCount, modelBytes, fontCount, fontBytes, harnessCount, harnessBytes
        )
    }

    fun restoreOneBlob(
        context: PlatformContext,
        name: String,
        dataLen: Long,
        data: okio.Source,
        wantModels: Boolean,
        wantFonts: Boolean,
        scratch: ByteArray,
        cancelled: (() -> Boolean)? = null
    ): Pair<Int, Int> {
        val isFont = blobKind(name) == BlobKind.FONT
        if ((isFont && !wantFonts) || (!isFont && !wantModels)) {
            skipFully(data, dataLen, scratch, cancelled)
            return 0 to 0
        }
        var tmp: okio.Path? = null
        var out: okio.Sink? = null
        var written = 0L
        try {
            val target = if (isFont) {
                FontStore.prepareRestoreTarget(context, name.removePrefix(FONT_BLOB_PREFIX))
            } else {
                com.lucent.app.local.LocalModelStore.prepareRestoreTarget(context, name)
            }
            val tmpFile = (target.toString() + ".tmp").toPath()
            tmp = tmpFile
            val os = okio.FileSystem.SYSTEM.sink(tmpFile)
            val bufferedOs = os.buffer()
            out = os
            while (written < dataLen) {
                throwIfCancelled(cancelled)
                val n = data.read(scratch, 0, minOf(dataLen - written, scratch.size.toLong()).toInt())
                if (n < 0) throw okio.EOFException("Backup payload ended early")
                bufferedOs.write(scratch, 0, n)
                written += n
            }
            bufferedOs.flush()
            os.close()
            out = null
            if (okio.FileSystem.SYSTEM.exists(target)) okio.FileSystem.SYSTEM.delete(target)
            okio.FileSystem.SYSTEM.atomicMove(tmpFile, target)
            return if (isFont) 0 to 1 else 1 to 0
        } catch (eof: okio.EOFException) {
            try { out?.close() } catch (_: Throwable) {}
            tmp?.let { okio.FileSystem.SYSTEM.delete(it) }
            throw eof
        } catch (t: Throwable) {
            if (t is CancellationException) {
                try { out?.close() } catch (_: Throwable) {}
                tmp?.let { okio.FileSystem.SYSTEM.delete(it) }
                throw t
            }
            try { out?.close() } catch (_: Throwable) {}
            tmp?.let { okio.FileSystem.SYSTEM.delete(it) }
            skipFully(data, dataLen - written, scratch)
            return 0 to 0
        }
    }
}

private fun okio.Source.read(b: ByteArray, off: Int, len: Int): Int {
    val buf = okio.Buffer()
    val n = this.read(buf, len.toLong())
    if (n == -1L) return -1
    buf.read(b, off, n.toInt())
    return n.toInt()
}

private fun okio.Source.read(b: ByteArray): Int = read(b, 0, b.size)

private fun okio.Source.read(): Int {
    val buf = okio.Buffer()
    val n = this.read(buf, 1)
    if (n == -1L) return -1
    return buf.readByte().toInt() and 0xFF
}

private fun okio.Sink.write(b: Int) {
    val buf = okio.Buffer()
    buf.writeByte(b)
    this.write(buf, 1)
}

private fun okio.Sink.write(b: ByteArray, off: Int, len: Int) {
    val buf = okio.Buffer()
    buf.write(b, off, len)
    this.write(buf, len.toLong())
}

private fun okio.Sink.write(b: ByteArray) = write(b, 0, b.size)
