package com.lucent.app.data

import android.content.Context
import java.io.OutputStream
import kotlinx.coroutines.CancellationException

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

    const val FRAME_MAGIC = 0x4C.toByte()
    const val FRAME_VERSION = 1.toByte()

    const val FONT_BLOB_PREFIX = "font:"

    const val MAX_BLOB_NAME_BYTES = 1024

    const val MAX_MANIFEST_BYTES = 1_200_000_000

    data class PayloadScan(
        val manifestJson: String,
        val framed: Boolean,
        val modelCount: Int,
        val modelBytes: Long,
        val fontCount: Int,
        val fontBytes: Long
    )

    fun readFully(input: java.io.InputStream, buffer: ByteArray, len: Int, cancelled: (() -> Boolean)? = null) {
        var read = 0
        while (read < len) {
            throwIfCancelled(cancelled)
            val n = input.read(buffer, read, len - read)
            if (n < 0) throw java.io.EOFException("Backup payload ended early")
            read += n
        }
    }

    fun skipFully(input: java.io.InputStream, count: Long, scratch: ByteArray, cancelled: (() -> Boolean)? = null) {
        var remaining = count
        while (remaining > 0) {
            throwIfCancelled(cancelled)
            val n = input.read(scratch, 0, minOf(remaining, scratch.size.toLong()).toInt())
            if (n < 0) throw java.io.EOFException("Backup payload ended early")
            remaining -= n
        }
    }

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

    fun openDecrypted(source: BackupManager.BackupSource, password: String?): java.io.InputStream {
        val raw = source.open()
        try {
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
            try { raw.close() } catch (_: Throwable) {}
            throw t
        }
    }

    fun scanPayload(
        plain: java.io.InputStream,
        cancelled: (() -> Boolean)? = null,
        onBlob: ((name: String, dataLen: Long, data: java.io.InputStream) -> Unit)? = null
    ): PayloadScan {
        val scratch = ByteArray(1 shl 16)

        val b0 = plain.read()
        if (b0 < 0) {
            return PayloadScan("", framed = false, 0, 0L, 0, 0L)
        }
        val b1 = plain.read()
        val framed = b0 == (FRAME_MAGIC.toInt() and 0xFF) && b1 == (FRAME_VERSION.toInt() and 0xFF)

        if (!framed) {
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
                break
            }
        }
        return PayloadScan(manifestJson, framed = true, modelCount, modelBytes, fontCount, fontBytes)
    }

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
            try { out?.close() } catch (_: Throwable) {}
            tmp?.delete()
            throw eof
        } catch (t: Throwable) {
            if (t is CancellationException) {
                try { out?.close() } catch (_: Throwable) {}
                tmp?.delete()
                throw t
            }
            try { out?.close() } catch (_: Throwable) {}
            tmp?.delete()
            skipFully(data, dataLen - written, scratch)
            return 0 to 0
        }
    }
}
