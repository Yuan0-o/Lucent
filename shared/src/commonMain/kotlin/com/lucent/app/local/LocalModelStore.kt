package com.lucent.app.local
import com.lucent.app.platform.filesDir

import com.lucent.app.platform.PlatformContext
import com.lucent.app.data.LocalSecrets
import kotlinx.serialization.json.*
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

object LocalModelStore {

    const val MAX_MODELS = 3

    private const val DIR = "local_model"

    private const val INDEX_FILE = "models.json"

    private const val LEGACY_FILE_NAME = "model.gguf"
    private const val LEGACY_NAME_FILE = "model.name"

    private val GGUF_MAGIC = byteArrayOf(0x47, 0x47, 0x55, 0x46)

    class NotGgufException : IOException("Not a GGUF model file")
    class NoGgufInZipException : IOException("No .gguf file inside the zip")
    class TooManyModelsException : IOException("Model slots are full")

    data class ModelSlot(val id: String, val name: String, val fileName: String)

    data class ModelIndex(val slots: List<ModelSlot>, val activeId: String?)

    private fun dir(context: PlatformContext): File = File(context.filesDir, DIR)


    @Synchronized
    fun index(context: PlatformContext): ModelIndex {
        val dir = dir(context)
        val indexFile = File(dir, INDEX_FILE)

        if (!indexFile.exists()) {
            val legacy = File(dir, LEGACY_FILE_NAME)
            if (legacy.exists() && legacy.length() > 0L) {
                val legacyName = File(dir, LEGACY_NAME_FILE).let {
                    if (it.exists()) it.readText().trim().ifBlank { null } else null
                } ?: "model.gguf"
                val slot = ModelSlot(id = newId(), name = legacyName, fileName = LEGACY_FILE_NAME)
                val migrated = ModelIndex(listOf(slot), slot.id)
                writeIndex(context, migrated)
                File(dir, LEGACY_NAME_FILE).delete()
                return migrated
            }
            return ModelIndex(emptyList(), null)
        }

        return try {
            val raw = indexFile.readText()
            val decrypted = LocalSecrets.decrypt(raw)
            if (decrypted.isEmpty() && raw.isNotEmpty()) return rebuildFromFiles(context)
            val root = Json.parseToJsonElement(decrypted).jsonObject
            val arr = root["slots"]?.jsonArray ?: buildJsonArray {}
            val slots = (0 until arr.size).mapNotNull { i ->
                val o = arr[i].jsonObject
                val id = (o["id"]?.jsonPrimitive?.content ?: "").ifBlank { return@mapNotNull null }
                val fileName = (o["file"]?.jsonPrimitive?.content ?: "").ifBlank { return@mapNotNull null }
                if (!File(dir, fileName).exists()) return@mapNotNull null
                ModelSlot(id = id, name = o["name"]?.jsonPrimitive?.content ?: "model.gguf", fileName = fileName)
            }
            val active = (root["active"]?.jsonPrimitive?.content ?: "").ifBlank { null }
                ?.takeIf { a -> slots.any { it.id == a } }
                ?: slots.firstOrNull()?.id
            ModelIndex(slots, active)
        } catch (_: Throwable) {
            rebuildFromFiles(context)
        }
    }

    @Synchronized
    private fun rebuildFromFiles(context: PlatformContext): ModelIndex {
        val dir = dir(context)
        val files = dir.listFiles()?.filter {
            it.isFile && it.name.lowercase().endsWith(".gguf") && it.length() > 0L
        }.orEmpty().sortedBy { it.name }
        if (files.isEmpty()) return ModelIndex(emptyList(), null)
        val slots = files.take(MAX_MODELS).map { f ->
            val id = f.name.removePrefix("model_").removeSuffix(".gguf").ifBlank { newId() }
            ModelSlot(id = id, name = f.name, fileName = f.name)
        }
        val rebuilt = ModelIndex(slots, slots.firstOrNull()?.id)
        try {
            writeIndex(context, rebuilt)
        } catch (_: Throwable) {
        }
        return rebuilt
    }

    @Synchronized
    private fun writeIndex(context: PlatformContext, idx: ModelIndex) {
        val dir = dir(context)
        if (!dir.exists()) dir.mkdirs()
        val arr = buildJsonArray {
            idx.slots.forEach { s ->
                add(buildJsonObject {
                    put("id", s.id)
                    put("name", s.name)
                    put("file", s.fileName)
                })
            }
        }
        val root = buildJsonObject {
            put("slots", arr)
            idx.activeId?.let { put("active", it) }
        }
        File(dir, INDEX_FILE).writeText(LocalSecrets.encrypt(Json.encodeToString(JsonElement.serializer(), root)))
    }


    fun slots(context: PlatformContext): List<ModelSlot> = index(context).slots

    fun activeSlot(context: PlatformContext): ModelSlot? {
        val idx = index(context)
        return idx.slots.firstOrNull { it.id == idx.activeId }
    }

    fun modelFile(context: PlatformContext, id: String): File? =
        index(context).slots.firstOrNull { it.id == id }?.let { File(dir(context), it.fileName) }

    fun activeModelFile(context: PlatformContext): File? {
        val slot = activeSlot(context) ?: return null
        val f = File(dir(context), slot.fileName)
        return if (f.exists() && f.length() > 0L) f else null
    }

    fun hasModel(context: PlatformContext): Boolean = activeModelFile(context) != null

    fun displayName(context: PlatformContext): String? = activeSlot(context)?.name

    fun modelSizeBytes(context: PlatformContext): Long = activeModelFile(context)?.length() ?: 0L

    fun modelSizeBytes(context: PlatformContext, id: String): Long =
        modelFile(context, id)?.takeIf { it.exists() }?.length() ?: 0L

    fun canImportMore(context: PlatformContext): Boolean = slots(context).size < MAX_MODELS


    @Synchronized
    fun setActive(context: PlatformContext, id: String) {
        val idx = index(context)
        if (idx.slots.none { it.id == id }) return
        writeIndex(context, idx.copy(activeId = id))
    }

    @Synchronized
    fun rename(context: PlatformContext, id: String, newName: String) {
        val idx = index(context)
        val clean = newName.trim().take(60)
        val updated = idx.slots.map {
            if (it.id == id) it.copy(name = clean.ifBlank { it.fileName }) else it
        }
        writeIndex(context, idx.copy(slots = updated))
    }

    @Throws(IOException::class)
    fun import(context: PlatformContext, source: ImportSource, customName: String? = null): ModelSlot {
        val existing = index(context)
        if (existing.slots.size >= MAX_MODELS) throw TooManyModelsException()

        val dir = dir(context)
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Could not create model directory")

        val id = newId()
        val fileName = "model_$id.gguf"
        val tmp = File(dir, "$fileName.tmp")
        var pickedName = source.displayName(context) ?: "model.gguf"

        try {
            source.openStream(context)?.use { raw ->
                val head = ByteArray(4)
                val headRead = readUpTo(raw, head)

                if (headRead == 4 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()) {
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
                    if (headRead < 4 || !head.contentEquals(GGUF_MAGIC)) throw NotGgufException()
                    copyPrefixed(head, headRead, raw, tmp)
                }
            } ?: throw IOException("Could not open the selected file")

            val target = File(dir, fileName)
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) throw IOException("Could not finalize the model file")

            val name = (customName?.trim()?.take(60)).let { if (it.isNullOrBlank()) pickedName else it }
            val slot = ModelSlot(id = id, name = name, fileName = fileName)
            writeIndex(context, ModelIndex(existing.slots + slot, slot.id))
            return slot
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    @Synchronized
    fun delete(context: PlatformContext, id: String) {
        val idx = index(context)
        val slot = idx.slots.firstOrNull { it.id == id } ?: return
        File(dir(context), slot.fileName).delete()
        File(dir(context), "${slot.fileName}.tmp").delete()
        deleteMmproj(context, id)
        val remaining = idx.slots.filter { it.id != id }
        val newActive = if (idx.activeId == id) remaining.firstOrNull()?.id else idx.activeId
        writeIndex(context, ModelIndex(remaining, newActive))
    }


    @Synchronized
    fun exportManifestJson(context: PlatformContext): String {
        val idx = index(context)
        val arr = buildJsonArray {
            idx.slots.forEach { s ->
                add(buildJsonObject {
                    put("id", s.id)
                    put("name", s.name)
                    put("file", s.fileName)
                    put("size", File(dir(context), s.fileName).length())
                })
            }
        }
        val root = buildJsonObject {
            put("slots", arr)
            idx.activeId?.let { put("active", it) }
        }
        return Json.encodeToString(JsonElement.serializer(), root)
    }

    fun totalModelBytes(context: PlatformContext): Long =
        slots(context).sumOf { File(dir(context), it.fileName).length() }

    fun modelFileForSlot(context: PlatformContext, slot: ModelSlot): File? =
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
        val arr = root["slots"]?.jsonArray ?: buildJsonArray {}
        val existing = index(context)
        val kept = mutableListOf<ModelSlot>()
        for (i in 0 until arr.size) {
            val o = arr[i].jsonObject
            val id = (o["id"]?.jsonPrimitive?.content ?: "").ifBlank { continue }
            val fileName = File((o["file"]?.jsonPrimitive?.content ?: "").ifBlank { continue }).name
            if (!File(d, fileName).let { it.exists() && it.length() > 0L }) continue
            if (existing.slots.any { it.id == id }) continue
            kept.add(ModelSlot(id = id, name = o["name"]?.jsonPrimitive?.content ?: fileName, fileName = fileName))
        }
        val merged = (existing.slots + kept).take(MAX_MODELS)
        val restoredActive = (root["active"]?.jsonPrimitive?.content ?: "").ifBlank { null }
        val active = restoredActive?.takeIf { a -> merged.any { it.id == a } }
            ?: existing.activeId?.takeIf { a -> merged.any { it.id == a } }
            ?: merged.firstOrNull()?.id
        writeIndex(context, ModelIndex(merged, active))
        return kept.size
    }

    @Synchronized
    fun deleteAll(context: PlatformContext) {
        val idx = index(context)
        idx.slots.forEach { s ->
            File(dir(context), s.fileName).delete()
            File(dir(context), "${s.fileName}.tmp").delete()
        }
        writeIndex(context, ModelIndex(emptyList(), null))
    }


    private fun newId(): String = java.util.UUID.randomUUID().toString().replace("-", "").take(12)

    private fun readUpTo(input: InputStream, buffer: ByteArray): Int {
        var read = 0
        while (read < buffer.size) {
            val n = input.read(buffer, read, buffer.size - read)
            if (n < 0) break
            read += n
        }
        return read
    }

    private fun copyVerifyingGguf(zipEntry: InputStream, out: File) {
        val head = ByteArray(4)
        val n = readUpTo(zipEntry, head)
        if (n < 4 || !head.contentEquals(GGUF_MAGIC)) throw NotGgufException()
        copyPrefixed(head, n, zipEntry, out)
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


    private fun mmprojFileName(id: String) = "mmproj_$id.gguf"

    fun mmprojFile(context: PlatformContext, id: String): File? =
        File(dir(context), mmprojFileName(id)).takeIf { it.exists() && it.length() > 0L }

    fun activeMmprojFile(context: PlatformContext): File? =
        activeSlot(context)?.let { mmprojFile(context, it.id) }

    fun importMmproj(context: PlatformContext, id: String, source: ImportSource): File {
        val dir = dir(context)
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Could not create model directory")
        val target = File(dir, mmprojFileName(id))
        val tmp = File(dir, "${mmprojFileName(id)}.tmp")
        try {
            source.openStream(context)?.use { raw ->
                val head = ByteArray(4)
                val headRead = readUpTo(raw, head)
                if (headRead < 4 || !head.contentEquals(GGUF_MAGIC)) throw NotGgufException()
                copyPrefixed(head, headRead, raw, tmp)
            } ?: throw IOException("Could not open the selected file")
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) throw IOException("Could not finalize the projector file")
            return target
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    fun deleteMmproj(context: PlatformContext, id: String) {
        File(dir(context), mmprojFileName(id)).delete()
        File(dir(context), "${mmprojFileName(id)}.tmp").delete()
    }
}
