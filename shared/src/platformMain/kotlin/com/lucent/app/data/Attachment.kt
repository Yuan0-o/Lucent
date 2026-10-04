package com.lucent.app.data

import android.content.Context
import kotlin.io.encoding.Base64
import org.json.JSONArray
import org.json.JSONObject

object Attachments {

    fun parse(json: String?): List<Attachment> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                Attachment(
                    mime = o.optString("mime", "application/octet-stream"),
                    data = o.optString("data", ""),
                    name = o.optString("name", "file")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun serialize(list: List<Attachment>): String {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("mime", it.mime)
                    .put("data", it.data)
                    .put("name", it.name)
            )
        }
        return arr.toString()
    }


    fun byteSize(context: Context, att: Attachment): Long {
        return if (AttachmentStore.looksLikeId(att.data)) {
            AttachmentStore.sizeOf(context, att.data)
        } else {
            estimateDecodedBase64Size(att.data)
        }
    }

    fun readBytes(context: Context, att: Attachment, maxBytes: Long = 32L * 1024 * 1024): ByteArray? {
        return if (AttachmentStore.looksLikeId(att.data)) {
            AttachmentStore.readBytes(context, att.data, maxBytes)
        } else {
            val approx = estimateDecodedBase64Size(att.data)
            if (approx > maxBytes) return null
            try {
                Base64.Mime.decode(att.data)
            } catch (t: Throwable) {
                null
            }
        }
    }

    fun openStream(context: Context, att: Attachment) =
        if (AttachmentStore.looksLikeId(att.data)) AttachmentStore.openInputStream(context, att.data) else null

    fun decodeText(context: Context, att: Attachment): String? {
        if (att.isImage) return null
        val looksTextual = att.mime.startsWith("text/") ||
            att.mime == "application/json" ||
            att.mime == "application/xml" ||
            att.mime.endsWith("+json") ||
            att.mime.endsWith("+xml") ||
            att.mime == "application/octet-stream"
        if (!looksTextual) return null
        val bytes = readBytes(context, att, maxBytes = 2L * 1024 * 1024) ?: return null
        if (bytes.any { it.toInt() == 0 }) return null
        return try {
            String(bytes, Charsets.UTF_8)
        } catch (t: Throwable) {
            null
        }
    }

    fun readAsBase64(context: Context, att: Attachment, maxBytes: Long = 8L * 1024 * 1024): String? {
        val bytes = readBytes(context, att, maxBytes) ?: return null
        return Base64.Default.encode(bytes)
    }

    fun textAttachment(context: Context, name: String, content: String): Attachment? {
        val safeName = if (name.isBlank()) "note.txt" else name
        val id = AttachmentStore.importBytes(context, content.toByteArray(Charsets.UTF_8)) ?: return null
        return Attachment(mime = "text/plain", data = id, name = safeName)
    }

    fun upsert(context: Context, list: List<Attachment>, attachment: Attachment): List<Attachment> {
        val idx = list.indexOfFirst { it.name.equals(attachment.name, ignoreCase = true) }
        return if (idx >= 0) {
            val displaced = list[idx]
            if (AttachmentStore.looksLikeId(displaced.data) && displaced.data != attachment.data) {
                AttachmentStore.delete(context, displaced.data)
            }
            list.toMutableList().also { it[idx] = attachment }
        } else list + attachment
    }

    fun removeByName(context: Context, list: List<Attachment>, name: String): List<Attachment> {
        val toRemove = list.filter { it.name.equals(name, ignoreCase = true) }
        toRemove.forEach { att ->
            if (AttachmentStore.looksLikeId(att.data)) AttachmentStore.delete(context, att.data)
        }
        return list.filterNot { it.name.equals(name, ignoreCase = true) }
    }

    fun idsFromJson(json: String?): Set<String> {
        if (json.isNullOrBlank()) return emptySet()
        return parse(json)
            .map { it.data }
            .filter { AttachmentStore.looksLikeId(it) }
            .toSet()
    }

    fun estimateDecodedBase64Size(base64: String): Long {
        if (base64.isEmpty()) return 0
        val padding = when {
            base64.endsWith("==") -> 2
            base64.endsWith("=") -> 1
            else -> 0
        }
        return (base64.length.toLong() * 3 / 4) - padding
    }
}

object ChatAttachments {

    fun all(
        primaryMime: String?,
        primaryData: String?,
        primaryName: String?,
        listJson: String?
    ): List<Attachment> {
        val stored = Attachments.parse(listJson)
        if (stored.isNotEmpty()) return stored
        if (primaryData.isNullOrBlank()) return emptyList()
        return listOf(Attachment(primaryMime ?: "application/octet-stream", primaryData, primaryName ?: "file"))
    }

    fun listJsonFor(list: List<Attachment>): String? =
        if (list.size > 1) Attachments.serialize(list) else null
}
