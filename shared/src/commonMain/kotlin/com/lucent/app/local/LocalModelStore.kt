package com.lucent.app.local
import com.lucent.app.platform.filesDir
import okio.Path.Companion.toPath


import com.lucent.app.platform.PlatformContext
import com.lucent.app.data.LocalSecrets
import kotlinx.serialization.json.*
import okio.Path
import okio.FileSystem
import okio.use
import okio.buffer
import okio.IOException

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

    private fun dir(context: PlatformContext): Path = context.filesDir / DIR


    @Synchronized
    fun index(context: PlatformContext): ModelIndex {
        val dir = dir(context)
        val indexFile = (dir / INDEX_FILE)

        if (!FileSystem.SYSTEM.exists(indexFile)) {
            val legacy = (dir / LEGACY_FILE_NAME)
            if (FileSystem.SYSTEM.exists(legacy) && (FileSystem.SYSTEM.metadata(legacy).size ?: 0L) > 0L) {
                val legacyName = (dir / LEGACY_NAME_FILE).let {
                    if (FileSystem.SYSTEM.exists(it)) FileSystem.SYSTEM.read(it) { readUtf8() }.trim().ifBlank { null } else null
                } ?: "model.gguf"
                val slot = ModelSlot(id = newId(), name = legacyName, fileName = LEGACY_FILE_NAME)
                val migrated = ModelIndex(listOf(slot), slot.id)
                writeIndex(context, migrated)
                okio.FileSystem.SYSTEM.delete(dir / LEGACY_NAME_FILE)
                return migrated
            }
            return ModelIndex(emptyList(), null)
        }

        return try {
            val raw = FileSystem.SYSTEM.read(indexFile) { readUtf8() }
            val decrypted = LocalSecrets.decrypt(raw)
            if (decrypted.isEmpty() && raw.isNotEmpty()) return rebuildFromFiles(context)
            val root = Json.parseToJsonElement(decrypted).jsonObject
            val arr = root["slots"]?.jsonArray ?: buildJsonArray {}
            val slots = (0 until arr.size).mapNotNull { i ->
                val o = arr[i].jsonObject
                val id = (o["id"]?.jsonPrimitive?.content ?: "").ifBlank { return@mapNotNull null }
                val fileName = (o["file"]?.jsonPrimitive?.content ?: "").ifBlank { return@mapNotNull null }
                if (!okio.FileSystem.SYSTEM.exists(dir / fileName)) return@mapNotNull null
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
        val files = FileSystem.SYSTEM.listOrNull(dir)?.filter {
            (FileSystem.SYSTEM.metadataOrNull(it)?.isRegularFile == true) && it.name.lowercase().endsWith(".gguf") && (FileSystem.SYSTEM.metadata(it).size ?: 0L) > 0L
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
        if (!FileSystem.SYSTEM.exists(dir)) FileSystem.SYSTEM.createDirectories(dir)
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
        FileSystem.SYSTEM.write(dir / INDEX_FILE) { writeUtf8(LocalSecrets.encrypt(Json.encodeToString(JsonElement.serializer(), root))) }
    }


    fun slots(context: PlatformContext): List<ModelSlot> = index(context).slots

    fun activeSlot(context: PlatformContext): ModelSlot? {
        val idx = index(context)
        return idx.slots.firstOrNull { it.id == idx.activeId }
    }

    fun modelFile(context: PlatformContext, id: String): Path? =
        index(context).slots.firstOrNull { it.id == id }?.let { (dir(context) / it.fileName) }

    fun activeModelFile(context: PlatformContext): Path? {
        val slot = activeSlot(context) ?: return null
        val f = (dir(context) / slot.fileName)
        return if (FileSystem.SYSTEM.exists(f) && (FileSystem.SYSTEM.metadata(f).size ?: 0L) > 0L) f else null
    }

    fun hasModel(context: PlatformContext): Boolean = activeModelFile(context) != null

    fun displayName(context: PlatformContext): String? = activeSlot(context)?.name

    fun modelSizeBytes(context: PlatformContext): Long = activeModelFile(context)?.let { FileSystem.SYSTEM.metadataOrNull(it)?.size } ?: 0L

    fun modelSizeBytes(context: PlatformContext, id: String): Long =
        modelFile(context, id)?.takeIf { FileSystem.SYSTEM.exists(it) }?.let { FileSystem.SYSTEM.metadataOrNull(it)?.size } ?: 0L

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
        if (!FileSystem.SYSTEM.exists(dir)) FileSystem.SYSTEM.createDirectories(dir)

        val id = newId()
        val fileName = "model_$id.gguf"
        val tmp = (dir / "$fileName.tmp")
        var pickedName = source.displayName(context) ?: "model.gguf"

        try {
            source.openStream(context)?.use { raw ->
                val head = ByteArray(4)
                val headRead = readUpTo(raw, head)

                if (headRead == 4 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()) {
                    val zipTemp = dir / "temp_zip_${kotlin.random.Random.nextInt()}.zip"
                    FileSystem.SYSTEM.write(zipTemp) {
                        write(head, 0, headRead)
                        val buf = ByteArray(1 shl 16)
                        while (true) {
                            val n = raw.read(buf)
                            if (n < 0) break
                            write(buf, 0, n)
                        }
                    }
                    try {
                        var found = false
                        val bytes = FileSystem.SYSTEM.read(zipTemp) { readByteArray() }
                        val entries = com.lucent.app.harness.ZipReader.readEntries(bytes) { it.lowercase().endsWith(".gguf") }
                        for ((childName, data) in entries) {
                            pickedName = childName.substringAfterLast("/")
                            FileSystem.SYSTEM.write(tmp) {
                                write(data)
                            }
                            val head2 = ByteArray(4)
                            val n = FileSystem.SYSTEM.read(tmp) { read(head2) }
                            if (n < 4 || !head2.contentEquals(GGUF_MAGIC)) throw NotGgufException()
                            found = true
                            break
                        }
                        if (!found) throw NoGgufInZipException()
                    } finally {
                        FileSystem.SYSTEM.delete(zipTemp)
                    }
                } else {
                    if (headRead < 4 || !head.contentEquals(GGUF_MAGIC)) throw NotGgufException()
                    copyPrefixed(head, headRead, raw, tmp)
                }
            } ?: throw IOException("Could not open the selected file")

            val target = (dir / fileName)
            if (FileSystem.SYSTEM.exists(target)) FileSystem.SYSTEM.delete(target)
            if (!(try { FileSystem.SYSTEM.atomicMove(tmp, target); true } catch (e: Exception) { false })) throw IOException("Could not finalize the model file")

            val name = (customName?.trim()?.take(60)).let { if (it.isNullOrBlank()) pickedName else it }
            val slot = ModelSlot(id = id, name = name, fileName = fileName)
            writeIndex(context, ModelIndex(existing.slots + slot, slot.id))
            return slot
        } finally {
            if (FileSystem.SYSTEM.exists(tmp)) FileSystem.SYSTEM.delete(tmp)
        }
    }

    @Synchronized
    fun delete(context: PlatformContext, id: String) {
        val idx = index(context)
        val slot = idx.slots.firstOrNull { it.id == id } ?: return
        okio.FileSystem.SYSTEM.delete(dir(context) / slot.fileName)
        okio.FileSystem.SYSTEM.delete(dir(context) / "${slot.fileName}.tmp")
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
                    put("size", okio.FileSystem.SYSTEM.metadataOrNull(dir(context) / s.fileName)?.size ?: 0L)
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
        slots(context).sumOf { FileSystem.SYSTEM.metadataOrNull(dir(context) / it.fileName)?.size ?: 0L }

    fun modelFileForSlot(context: PlatformContext, slot: ModelSlot): Path? =
        (dir(context) / slot.fileName).takeIf { FileSystem.SYSTEM.exists(it) && (FileSystem.SYSTEM.metadata(it).size ?: 0L) > 0L }

    @Synchronized
    fun prepareRestoreTarget(context: PlatformContext, fileName: String): Path {
        val d = dir(context)
        if (!FileSystem.SYSTEM.exists(d)) FileSystem.SYSTEM.createDirectories(d)
        return d / fileName.toPath().name
    }

    @Synchronized
    fun restoreFromBackup(context: PlatformContext, manifestJson: String): Int {
        val d = dir(context)
        if (!FileSystem.SYSTEM.exists(d)) FileSystem.SYSTEM.createDirectories(d)
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
            val fileName = (o["file"]?.jsonPrimitive?.content ?: "").ifBlank { continue }.toPath().name
            if (!(d / fileName).let { FileSystem.SYSTEM.exists(it) && (FileSystem.SYSTEM.metadata(it).size ?: 0L) > 0L }) continue
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
            FileSystem.SYSTEM.delete(dir(context) / s.fileName)
            FileSystem.SYSTEM.delete(dir(context) / "${s.fileName}.tmp")
        }
        writeIndex(context, ModelIndex(emptyList(), null))
    }


    private fun newId(): String = kotlin.uuid.Uuid.random().toString().replace("-", "").take(12)

    private fun readUpTo(input: okio.Source, buffer: ByteArray): Int {
        var read = 0
        while (read < buffer.size) {
            val n = input.read(buffer, read, buffer.size - read)
            if (n < 0) break
            read += n
        }
        return read
    }

    private fun copyVerifyingGguf(zipEntry: okio.Source, out: Path) {
        val head = ByteArray(4)
        val n = readUpTo(zipEntry, head)
        if (n < 4 || !head.contentEquals(GGUF_MAGIC)) throw NotGgufException()
        copyPrefixed(head, n, zipEntry, out)
    }

    private fun copyPrefixed(prefix: ByteArray, prefixLen: Int, input: okio.Source, out: Path) {
        FileSystem.SYSTEM.write(out) {
            write(prefix, 0, prefixLen)
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                write(buf, 0, n)
            }
            flush()
        }
    }


    private fun mmprojFileName(id: String) = "mmproj_$id.gguf"

    fun mmprojFile(context: PlatformContext, id: String): Path? =
        (dir(context) / mmprojFileName(id)).takeIf { FileSystem.SYSTEM.exists(it) && (FileSystem.SYSTEM.metadata(it).size ?: 0L) > 0L }

    fun activeMmprojFile(context: PlatformContext): Path? =
        activeSlot(context)?.let { mmprojFile(context, it.id) }

    fun importMmproj(context: PlatformContext, id: String, source: ImportSource): Path {
        val dir = dir(context)
        if (!FileSystem.SYSTEM.exists(dir)) FileSystem.SYSTEM.createDirectories(dir)
        val target = (dir / mmprojFileName(id))
        val tmp = (dir / "${mmprojFileName(id)}.tmp")
        try {
            source.openStream(context)?.use { raw ->
                val head = ByteArray(4)
                val headRead = readUpTo(raw, head)
                if (headRead < 4 || !head.contentEquals(GGUF_MAGIC)) throw NotGgufException()
                copyPrefixed(head, headRead, raw, tmp)
            } ?: throw IOException("Could not open the selected file")
            if (FileSystem.SYSTEM.exists(target)) FileSystem.SYSTEM.delete(target)
            if (!(try { FileSystem.SYSTEM.atomicMove(tmp, target); true } catch (e: Exception) { false })) throw IOException("Could not finalize the projector file")
            return target
        } finally {
            if (FileSystem.SYSTEM.exists(tmp)) FileSystem.SYSTEM.delete(tmp)
        }
    }

    fun deleteMmproj(context: PlatformContext, id: String) {
        FileSystem.SYSTEM.delete(dir(context) / mmprojFileName(id))
        FileSystem.SYSTEM.delete(dir(context) / "${mmprojFileName(id)}.tmp")
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
