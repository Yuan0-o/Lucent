package com.lucent.app.data
import com.lucent.app.platform.filesDir
import com.lucent.app.local.ImportSource

import com.lucent.app.platform.PlatformContext
import kotlinx.serialization.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.encodeToString
import java.io.File
import java.io.IOException
import java.io.InputStream

object FontStore {

    const val MAX_FONTS = 12

    private const val DIR = "fonts"

    private const val INDEX_FILE = "fonts.json"

    private val MAGIC_TTF = byteArrayOf(0x00, 0x01, 0x00, 0x00)
    private val MAGIC_TRUE = byteArrayOf(0x74, 0x72, 0x75, 0x65)
    private val MAGIC_OTF = byteArrayOf(0x4F, 0x54, 0x54, 0x4F)
    private val MAGIC_TTC = byteArrayOf(0x74, 0x74, 0x63, 0x66)

    class NotFontException : IOException("Not a usable font file")
    class TooManyFontsException : IOException("Font slots are full")

    @Serializable
    data class FontSlot(val id: String, val name: String, @SerialName("file") val fileName: String)

    @Serializable
    data class FontIndex(@SerialName("fonts") val slots: List<FontSlot> = emptyList())

    private fun dir(context: PlatformContext): File = File(context.filesDir, DIR)


    @Synchronized
    fun index(context: PlatformContext): FontIndex {
        val dir = dir(context)
        val indexFile = File(dir, INDEX_FILE)
        if (!indexFile.exists()) return FontIndex(emptyList())

        return try {
            val raw = indexFile.readText()
            val decrypted = LocalSecrets.decrypt(raw)
            if (decrypted.isEmpty() && raw.isNotEmpty()) return rebuildFromFiles(context)
            val parsed = Json { ignoreUnknownKeys = true }.decodeFromString<FontIndex>(decrypted)
            val validSlots = parsed.slots.filter { 
                it.id.isNotBlank() && it.fileName.isNotBlank() && File(dir, it.fileName).exists() 
            }.map {
                if (it.name.isBlank()) it.copy(name = it.fileName) else it
            }
            FontIndex(validSlots)
        } catch (_: Throwable) {
            rebuildFromFiles(context)
        }
    }

    @Synchronized
    private fun rebuildFromFiles(context: PlatformContext): FontIndex {
        val dir = dir(context)
        val files = dir.listFiles()?.filter { f ->
            f.isFile && f.length() > 0L && listOf(".ttf", ".otf", ".ttc").any {
                f.name.lowercase().endsWith(it)
            }
        }.orEmpty().sortedBy { it.name }
        if (files.isEmpty()) return FontIndex(emptyList())
        val slots = files.take(MAX_FONTS).map { f ->
            val id = f.name.removePrefix("font_").substringBeforeLast('.').ifBlank { newId() }
            FontSlot(id = id, name = f.name, fileName = f.name)
        }
        val rebuilt = FontIndex(slots)
        try {
            writeIndex(context, rebuilt)
        } catch (_: Throwable) {
        }
        return rebuilt
    }

    @Synchronized
    private fun writeIndex(context: PlatformContext, idx: FontIndex) {
        val dir = dir(context)
        if (!dir.exists()) dir.mkdirs()
        val jsonStr = Json.encodeToString(idx)
        File(dir, INDEX_FILE).writeText(LocalSecrets.encrypt(jsonStr))
    }


    fun fonts(context: PlatformContext): List<FontSlot> = index(context).slots

    fun fontFile(context: PlatformContext, id: String): File? =
        index(context).slots.firstOrNull { it.id == id }
            ?.let { File(dir(context), it.fileName) }
            ?.takeIf { it.exists() && it.length() > 0L }

    fun canImportMore(context: PlatformContext): Boolean = fonts(context).size < MAX_FONTS


    @Throws(IOException::class)
    fun import(context: PlatformContext, source: ImportSource, customName: String? = null): FontSlot {
        val existing = index(context)
        if (existing.slots.size >= MAX_FONTS) throw TooManyFontsException()

        val dir = dir(context)
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Could not create the font directory")

        val id = newId()
        val pickedName = source.displayName(context) ?: "font"

        source.openStream(context)?.use { raw ->
            val head = ByteArray(4)
            val headRead = readUpTo(raw, head)
            val ext = classify(head, headRead) ?: throw NotFontException()
            val fileName = "font_$id.$ext"
            val tmp = File(dir, "$fileName.tmp")
            try {
                copyPrefixed(head, headRead, raw, tmp)
                val target = File(dir, fileName)
                if (target.exists()) target.delete()
                if (!tmp.renameTo(target)) throw IOException("Could not finalize the font file")
            } finally {
                if (tmp.exists()) tmp.delete()
            }
            val name = (customName?.trim()?.take(60)).let {
                if (it.isNullOrBlank()) pickedName.substringBeforeLast('.') else it
            }
            val slot = FontSlot(id = id, name = name, fileName = fileName)
            writeIndex(context, FontIndex(existing.slots + slot))
            return slot
        } ?: throw IOException("Could not open the selected file")
    }

    @Synchronized
    fun delete(context: PlatformContext, id: String) {
        val idx = index(context)
        val slot = idx.slots.firstOrNull { it.id == id } ?: return
        File(dir(context), slot.fileName).delete()
        File(dir(context), "${slot.fileName}.tmp").delete()
        writeIndex(context, FontIndex(idx.slots.filter { it.id != id }))
    }

    @Synchronized
    fun deleteAll(context: PlatformContext) {
        val idx = index(context)
        idx.slots.forEach { s ->
            File(dir(context), s.fileName).delete()
            File(dir(context), "${s.fileName}.tmp").delete()
        }
        writeIndex(context, FontIndex(emptyList()))
    }


    @Synchronized
    fun exportManifestJson(context: PlatformContext): String {
        val idx = index(context)
        val arr = buildJsonArray {
            idx.slots.forEach { s ->
                addJsonObject {
                    put("id", s.id)
                    put("name", s.name)
                    put("file", s.fileName)
                    put("size", File(dir(context), s.fileName).length())
                }
            }
        }
        return buildJsonObject { put("fonts", arr) }.toString()
    }

    fun totalFontBytes(context: PlatformContext): Long =
        fonts(context).sumOf { File(dir(context), it.fileName).length() }

    fun fontFileForSlot(context: PlatformContext, slot: FontSlot): File? =
        File(dir(context), slot.fileName).takeIf { it.exists() && it.length() > 0L }

    @Synchronized
    fun prepareRestoreTarget(context: PlatformContext, fileName: String): File {
        val d = dir(context)
        if (!d.exists()) d.mkdirs()
        return File(d, File(fileName).name)
    }

    @Synchronized
    fun restoreFromBackup(context: PlatformContext, manifestJson: String): Int {
        val d = dir(context)
        if (!d.exists()) d.mkdirs()
        val root = try {
            Json.parseToJsonElement(manifestJson).jsonObject
        } catch (_: Throwable) {
            return 0
        }
        val arr = root["fonts"]?.jsonArray ?: emptyList()
        val existing = index(context)
        val kept = mutableListOf<FontSlot>()
        for (i in arr.indices) {
            val o = arr[i].jsonObject
            val id = o["id"]?.jsonPrimitive?.content ?: ""
            if (id.isBlank()) continue
            val fileName = File(o["file"]?.jsonPrimitive?.content ?: "").name
            if (!File(d, fileName).let { it.exists() && it.length() > 0L }) continue
            if (existing.slots.any { it.id == id }) continue
            val name = o["name"]?.jsonPrimitive?.content ?: fileName
            kept.add(FontSlot(id = id, name = name, fileName = fileName))
        }
        val merged = (existing.slots + kept).take(MAX_FONTS)
        writeIndex(context, FontIndex(merged))
        return kept.size
    }


    private fun newId(): String = java.util.UUID.randomUUID().toString().replace("-", "").take(12)

    private fun classify(head: ByteArray, headLen: Int): String? {
        if (headLen < 4) return null
        return when {
            head.contentEquals(MAGIC_TTF) || head.contentEquals(MAGIC_TRUE) -> "ttf"
            head.contentEquals(MAGIC_OTF) -> "otf"
            head.contentEquals(MAGIC_TTC) -> "ttc"
            else -> null
        }
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
}
