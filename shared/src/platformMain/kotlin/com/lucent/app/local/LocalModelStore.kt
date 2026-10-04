package com.lucent.app.local

import android.content.Context
import com.lucent.app.data.LocalSecrets
import org.json.JSONArray
import org.json.JSONObject
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

    private fun dir(context: Context): File = File(context.filesDir, DIR)


    @Synchronized
    fun index(context: Context): ModelIndex {
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
            val root = JSONObject(decrypted)
            val arr = root.optJSONArray("slots") ?: JSONArray()
            val slots = (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id", "").ifBlank { return@mapNotNull null }
                val fileName = o.optString("file", "").ifBlank { return@mapNotNull null }
                if (!File(dir, fileName).exists()) return@mapNotNull null
                ModelSlot(id = id, name = o.optString("name", "model.gguf"), fileName = fileName)
            }
            val active = root.optString("active", "").ifBlank { null }
                ?.takeIf { a -> slots.any { it.id == a } }
                ?: slots.firstOrNull()?.id
            ModelIndex(slots, active)
        } catch (_: Throwable) {
            rebuildFromFiles(context)
        }
    }

    @Synchronized
    private fun rebuildFromFiles(context: Context): ModelIndex {
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
    private fun writeIndex(context: Context, idx: ModelIndex) {
        val dir = dir(context)
        if (!dir.exists()) dir.mkdirs()
        val arr = JSONArray()
        idx.slots.forEach { s ->
            arr.put(JSONObject().put("id", s.id).put("name", s.name).put("file", s.fileName))
        }
        val root = JSONObject().put("slots", arr)
        idx.activeId?.let { root.put("active", it) }
        File(dir, INDEX_FILE).writeText(LocalSecrets.encrypt(root.toString()))
    }


    fun slots(context: Context): List<ModelSlot> = index(context).slots

    fun activeSlot(context: Context): ModelSlot? {
        val idx = index(context)
        return idx.slots.firstOrNull { it.id == idx.activeId }
    }

    fun modelFile(context: Context, id: String): File? =
        index(context).slots.firstOrNull { it.id == id }?.let { File(dir(context), it.fileName) }

    fun activeModelFile(context: Context): File? {
        val slot = activeSlot(context) ?: return null
        val f = File(dir(context), slot.fileName)
        return if (f.exists() && f.length() > 0L) f else null
    }

    fun hasModel(context: Context): Boolean = activeModelFile(context) != null

    fun displayName(context: Context): String? = activeSlot(context)?.name

    fun modelSizeBytes(context: Context): Long = activeModelFile(context)?.length() ?: 0L

    fun modelSizeBytes(context: Context, id: String): Long =
        modelFile(context, id)?.takeIf { it.exists() }?.length() ?: 0L

    fun canImportMore(context: Context): Boolean = slots(context).size < MAX_MODELS


    @Synchronized
    fun setActive(context: Context, id: String) {
        val idx = index(context)
        if (idx.slots.none { it.id == id }) return
        writeIndex(context, idx.copy(activeId = id))
    }

    @Synchronized
    fun rename(context: Context, id: String, newName: String) {
        val idx = index(context)
        val clean = newName.trim().take(60)
        val updated = idx.slots.map {
            if (it.id == id) it.copy(name = clean.ifBlank { it.fileName }) else it
        }
        writeIndex(context, idx.copy(slots = updated))
    }

    @Throws(IOException::class)
    fun import(context: Context, source: PlatformModelSource, customName: String? = null): ModelSlot {
        val existing = index(context)
        if (existing.slots.size >= MAX_MODELS) throw TooManyModelsException()

        val dir = dir(context)
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Could not create model directory")

        val id = newId()
        val fileName = "model_$id.gguf"
        val tmp = File(dir, "$fileName.tmp")
        var pickedName = modelSourceDisplayName(context, source) ?: "model.gguf"

        try {
            openModelSource(context, source)?.use { raw ->
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
    fun delete(context: Context, id: String) {
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
    fun exportManifestJson(context: Context): String {
        val idx = index(context)
        val arr = JSONArray()
        idx.slots.forEach { s ->
            arr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("name", s.name)
                    .put("file", s.fileName)
                    .put("size", File(dir(context), s.fileName).length())
            )
        }
        val root = JSONObject().put("slots", arr)
        idx.activeId?.let { root.put("active", it) }
        return root.toString()
    }

    fun totalModelBytes(context: Context): Long =
        slots(context).sumOf { File(dir(context), it.fileName).length() }

    fun modelFileForSlot(context: Context, slot: ModelSlot): File? =
        File(dir(context), slot.fileName).takeIf { it.exists() && it.length() > 0L }

    @Synchronized
    fun prepareRestoreTarget(context: Context, fileName: String): File {
        val d = dir(context)
        if (!d.exists()) d.mkdirs()
        return File(d, File(fileName).name)
    }

    @Synchronized
    fun restoreFromBackup(context: Context, manifestJson: String): Int {
        val d = dir(context)
        if (!d.exists()) d.mkdirs()
        val root = try {
            JSONObject(manifestJson)
        } catch (_: Throwable) {
            return 0
        }
        val arr = root.optJSONArray("slots") ?: JSONArray()
        val existing = index(context)
        val kept = mutableListOf<ModelSlot>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id", "").ifBlank { continue }
            val fileName = File(o.optString("file", "").ifBlank { continue }).name
            if (!File(d, fileName).let { it.exists() && it.length() > 0L }) continue
            if (existing.slots.any { it.id == id }) continue
            kept.add(ModelSlot(id = id, name = o.optString("name", fileName), fileName = fileName))
        }
        val merged = (existing.slots + kept).take(MAX_MODELS)
        val restoredActive = root.optString("active", "").ifBlank { null }
        val active = restoredActive?.takeIf { a -> merged.any { it.id == a } }
            ?: existing.activeId?.takeIf { a -> merged.any { it.id == a } }
            ?: merged.firstOrNull()?.id
        writeIndex(context, ModelIndex(merged, active))
        return kept.size
    }

    @Synchronized
    fun deleteAll(context: Context) {
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

    fun mmprojFile(context: Context, id: String): File? =
        File(dir(context), mmprojFileName(id)).takeIf { it.exists() && it.length() > 0L }

    fun activeMmprojFile(context: Context): File? =
        activeSlot(context)?.let { mmprojFile(context, it.id) }

    fun importMmproj(context: Context, id: String, source: PlatformModelSource): File {
        val dir = dir(context)
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Could not create model directory")
        val target = File(dir, mmprojFileName(id))
        val tmp = File(dir, "${mmprojFileName(id)}.tmp")
        try {
            openModelSource(context, source)?.use { raw ->
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

    fun deleteMmproj(context: Context, id: String) {
        File(dir(context), mmprojFileName(id)).delete()
        File(dir(context), "${mmprojFileName(id)}.tmp").delete()
    }
}
