package com.lucent.app.data
import okio.buffer
import com.lucent.app.platform.filesDir
import com.lucent.app.local.ImportSource

import com.lucent.app.platform.PlatformContext
import kotlinx.serialization.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.encodeToString
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.Source
import okio.buffer
import okio.source

object FontStore {
    private val jsonParser = Json { ignoreUnknownKeys = true }

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

    private fun dir(context: PlatformContext): Path = context.filesDir.toString().toPath() / DIR


    @Synchronized
    fun index(context: PlatformContext): FontIndex {
        val dir = dir(context)
        val indexFile = dir / INDEX_FILE
        if (!FileSystem.SYSTEM.exists(indexFile)) return FontIndex(emptyList())

        return try {
            val raw = FileSystem.SYSTEM.source(indexFile).buffer().readUtf8()
            val decrypted = LocalSecrets.decrypt(raw)
            if (decrypted.isEmpty() && raw.isNotEmpty()) return rebuildFromFiles(context)
            val parsed = jsonParser.decodeFromString<FontIndex>(decrypted)
            val validSlots = parsed.slots.filter { 
                it.id.isNotBlank() && it.fileName.isNotBlank() && FileSystem.SYSTEM.exists(dir / it.fileName) 
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
        val files = runCatching { FileSystem.SYSTEM.list(dir) }.getOrNull()?.filter { f ->
            FileSystem.SYSTEM.metadataOrNull(f)?.isRegularFile == true && (FileSystem.SYSTEM.metadataOrNull(f)?.size ?: 0L) > 0L && listOf(".ttf", ".otf", ".ttc").any {
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
        try { FileSystem.SYSTEM.createDirectories(dir) } catch (_: Exception) {}
        val jsonStr = Json.encodeToString(idx)
        FileSystem.SYSTEM.sink(dir / INDEX_FILE).buffer().use { it.writeUtf8(LocalSecrets.encrypt(jsonStr)) }
    }


    fun fonts(context: PlatformContext): List<FontSlot> = index(context).slots

    fun fontFile(context: PlatformContext, id: String): Path? =
        index(context).slots.firstOrNull { it.id == id }
            ?.let { dir(context) / it.fileName }
            ?.takeIf { FileSystem.SYSTEM.exists(it) && (FileSystem.SYSTEM.metadataOrNull(it)?.size ?: 0L) > 0L }

    fun canImportMore(context: PlatformContext): Boolean = fonts(context).size < MAX_FONTS


    @Throws(IOException::class)
    fun import(context: PlatformContext, source: ImportSource, customName: String? = null): FontSlot {
        val existing = index(context)
        if (existing.slots.size >= MAX_FONTS) throw TooManyFontsException()

        val dir = dir(context)
        if (!FileSystem.SYSTEM.exists(dir)) {
            try { FileSystem.SYSTEM.createDirectories(dir) } catch (_: Exception) { throw IOException("Could not create the font directory") }
        }

        val id = newId()
        val pickedName = source.displayName(context) ?: "font"

        source.openStream(context)?.buffer()?.use { raw ->
            val head = ByteArray(4)
            val headRead = readUpTo(raw, head)
            val ext = classify(head, headRead) ?: throw NotFontException()
            val fileName = "font_$id.$ext"
            val tmp = dir / "$fileName.tmp"
            try {
                copyPrefixed(head, headRead, raw, tmp)
                val target = dir / fileName
                if (FileSystem.SYSTEM.exists(target)) FileSystem.SYSTEM.delete(target)
                try { FileSystem.SYSTEM.atomicMove(tmp, target) } catch (_: Exception) { throw IOException("Could not finalize the font file") }
            } finally {
                if (FileSystem.SYSTEM.exists(tmp)) FileSystem.SYSTEM.delete(tmp)
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
        try { FileSystem.SYSTEM.delete(dir(context) / slot.fileName) } catch (_: Exception) {}
        try { FileSystem.SYSTEM.delete(dir(context) / "${slot.fileName}.tmp") } catch (_: Exception) {}
        writeIndex(context, FontIndex(idx.slots.filter { it.id != id }))
    }

    @Synchronized
    fun deleteAll(context: PlatformContext) {
        val idx = index(context)
        idx.slots.forEach { s ->
            try { FileSystem.SYSTEM.delete(dir(context) / s.fileName) } catch (_: Exception) {}
            try { FileSystem.SYSTEM.delete(dir(context) / "${s.fileName}.tmp") } catch (_: Exception) {}
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
                    put("size", FileSystem.SYSTEM.metadataOrNull(dir(context) / s.fileName)?.size ?: 0L)
                }
            }
        }
        return buildJsonObject { put("fonts", arr) }.toString()
    }

    fun totalFontBytes(context: PlatformContext): Long =
        fonts(context).sumOf { FileSystem.SYSTEM.metadataOrNull(dir(context) / it.fileName)?.size ?: 0L }

    fun fontFileForSlot(context: PlatformContext, slot: FontSlot): Path? =
        (dir(context) / slot.fileName).takeIf { FileSystem.SYSTEM.exists(it) && (FileSystem.SYSTEM.metadataOrNull(it)?.size ?: 0L) > 0L }

    @Synchronized
    fun prepareRestoreTarget(context: PlatformContext, fileName: String): Path {
        val d = dir(context)
        try { FileSystem.SYSTEM.createDirectories(d) } catch (_: Exception) {}
        return d / fileName.toPath().name
    }

    @Synchronized
    fun restoreFromBackup(context: PlatformContext, manifestJson: String): Int {
        val d = dir(context)
        try { FileSystem.SYSTEM.createDirectories(d) } catch (_: Exception) {}
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
            val fileName = (o["file"]?.jsonPrimitive?.content ?: "").toPath().name
            if (!(d / fileName).let { FileSystem.SYSTEM.exists(it) && (FileSystem.SYSTEM.metadataOrNull(it)?.size ?: 0L) > 0L }) continue
            if (existing.slots.any { it.id == id }) continue
            val name = o["name"]?.jsonPrimitive?.content ?: fileName
            kept.add(FontSlot(id = id, name = name, fileName = fileName))
        }
        val merged = (existing.slots + kept).take(MAX_FONTS)
        writeIndex(context, FontIndex(merged))
        return kept.size
    }


    private fun newId(): String = kotlin.uuid.Uuid.random().toString().replace("-", "").take(12)

    private fun classify(head: ByteArray, headLen: Int): String? {
        if (headLen < 4) return null
        return when {
            head.contentEquals(MAGIC_TTF) || head.contentEquals(MAGIC_TRUE) -> "ttf"
            head.contentEquals(MAGIC_OTF) -> "otf"
            head.contentEquals(MAGIC_TTC) -> "ttc"
            else -> null
        }
    }

    private fun readUpTo(source: okio.BufferedSource, buffer: ByteArray): Int {
        var read = 0
        while (read < buffer.size) {
            val n = source.read(buffer, read, buffer.size - read)
            if (n < 0) break
            read += n
        }
        return read
    }

    private fun copyPrefixed(prefix: ByteArray, prefixLen: Int, source: okio.BufferedSource, out: Path) {
        FileSystem.SYSTEM.sink(out).buffer().use { sink ->
            sink.write(prefix, 0, prefixLen)
            sink.writeAll(source)
        }
    }
}
