package com.lucent.app.local

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Where the imported local model lives, and how it gets there.
 *
 * The whole point of this feature is "pick a file, done" — no paths, no server, no config. So:
 *
 *  - The user picks any file in the system picker. A plain `.gguf` is copied straight in; a `.zip`
 *    is looked through and the first `.gguf` inside is extracted automatically (the promised
 *    auto-decompression), so a model downloaded as an archive needs no manual unpacking.
 *  - The bytes are **verified to actually be GGUF** (magic `GGUF`) before anything is kept — a
 *    wrong file is rejected with a clear message instead of being discovered as a native crash
 *    three screens later.
 *  - The copy lands in a temp file and is renamed into place only when complete, so a cancelled or
 *    failed import can never leave a half-written model that the engine then chokes on.
 *  - Exactly one model is kept ([FILE_NAME]); importing a new one replaces the old, and the engine
 *    is shut down first so the memory of the outgoing model is released immediately.
 */
object LocalModelStore {

    private const val DIR = "local_model"
    private const val FILE_NAME = "model.gguf"
    private const val NAME_FILE = "model.name"

    private val GGUF_MAGIC = byteArrayOf(0x47, 0x47, 0x55, 0x46) // "GGUF"

    class NotGgufException : IOException("Not a GGUF model file")
    class NoGgufInZipException : IOException("No .gguf file inside the zip")

    fun modelFile(context: Context): File = File(File(context.filesDir, DIR), FILE_NAME)

    /** The original file name the user imported (for display), or null when no model is present. */
    fun displayName(context: Context): String? {
        val f = File(File(context.filesDir, DIR), NAME_FILE)
        return try {
            if (f.exists()) f.readText().trim().ifBlank { null } else null
        } catch (_: Throwable) {
            null
        }
    }

    fun modelSizeBytes(context: Context): Long = modelFile(context).takeIf { it.exists() }?.length() ?: 0L

    fun hasModel(context: Context): Boolean = modelSizeBytes(context) > 0L

    /**
     * Import the model at [uri]. Blocking I/O — call from a background dispatcher. On success the
     * previous model (if any) has been replaced and the display name recorded.
     *
     * @throws NotGgufException  the picked file (or the extracted entry) is not GGUF
     * @throws NoGgufInZipException the picked zip holds no `.gguf` entry
     * @throws IOException      any read/write failure
     */
    @Throws(IOException::class)
    fun import(context: Context, uri: Uri) {
        val dir = File(context.filesDir, DIR)
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Could not create model directory")
        val tmp = File(dir, "$FILE_NAME.tmp")
        var pickedName = queryDisplayName(context, uri) ?: "model.gguf"

        try {
            context.contentResolver.openInputStream(uri)?.use { raw ->
                val head = ByteArray(4)
                val headRead = readUpTo(raw, head)

                if (headRead == 4 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()) {
                    // A zip: scan for the first .gguf entry and extract just it. The output path is
                    // fixed ([tmp]) — entry names are never used as paths, so zip-slip cannot apply.
                    val stitched = StitchedInputStream(head, headRead, raw)
                    val zip = ZipInputStream(stitched)
                    var entry = zip.nextEntry
                    var found = false
                    while (entry != null) {
                        if (!entry.isDirectory && entry.name.lowercase().endsWith(".gguf")) {
                            pickedName = entry.name.substringAfterLast('/')
                            copyVerifyingGguf(zip, tmp)
                            found = true
                            break
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                    if (!found) throw NoGgufInZipException()
                } else {
                    // A plain file: must itself start with the GGUF magic.
                    if (headRead < 4 || !head.contentEquals(GGUF_MAGIC)) throw NotGgufException()
                    copyPrefixed(head, headRead, raw, tmp)
                }
            } ?: throw IOException("Could not open the selected file")

            val target = File(dir, FILE_NAME)
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) throw IOException("Could not finalize the model file")
            File(dir, NAME_FILE).writeText(pickedName)
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    /** Remove the stored model (the engine must be shut down by the caller first). */
    fun delete(context: Context) {
        val dir = File(context.filesDir, DIR)
        File(dir, FILE_NAME).delete()
        File(dir, NAME_FILE).delete()
        File(dir, "$FILE_NAME.tmp").delete()
    }

    // ---------------------------------------------------------------------------------------

    private fun queryDisplayName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (_: Throwable) {
        null
    }

    private fun readUpTo(input: InputStream, buffer: ByteArray): Int {
        var read = 0
        while (read < buffer.size) {
            val n = input.read(buffer, read, buffer.size - read)
            if (n < 0) break
            read += n
        }
        return read
    }

    /** Copy [zipEntry] to [out], verifying its first four bytes are the GGUF magic. */
    private fun copyVerifyingGguf(zipEntry: InputStream, out: File) {
        val head = ByteArray(4)
        val n = readUpTo(zipEntry, head)
        if (n < 4 || !head.contentEquals(GGUF_MAGIC)) throw NotGgufException()
        copyPrefixed(head, n, zipEntry, out)
    }

    /** Write [prefixLen] bytes of [prefix] then the rest of [input] into [out]. */
    private fun copyPrefixed(prefix: ByteArray, prefixLen: Int, input: InputStream, out: File) {
        out.outputStream().use { os ->
            os.write(prefix, 0, prefixLen)
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                os.write(buf, 0, n)
            }
            os.flush()
        }
    }

    /** Re-attach already-consumed header bytes in front of the remaining stream. */
    private class StitchedInputStream(
        private val head: ByteArray,
        private val headLen: Int,
        private val rest: InputStream
    ) : InputStream() {
        private var pos = 0
        override fun read(): Int =
            if (pos < headLen) head[pos++].toInt() and 0xFF else rest.read()

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (pos < headLen) {
                val take = minOf(len, headLen - pos)
                System.arraycopy(head, pos, b, off, take)
                pos += take
                return take
            }
            return rest.read(b, off, len)
        }
    }
}
