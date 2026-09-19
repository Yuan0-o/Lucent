package com.lucent.app.data

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object ReplyFiles {

    const val MAX_FETCH_BYTES = 20L * 1024 * 1024

    data class ReplyFile(
        val name: String,
        val url: String,
        val isImage: Boolean
    ) {
        val isInline: Boolean get() = url.startsWith("data:", ignoreCase = true)
    }

    private val MD_LINK = Regex("""(!?)\[([^\]]*)]\(\s*(<?)([^)\s>]+)\3\s*(?:"[^"]*")?\s*\)""")

    private val BARE_FILE_URL = Regex(
        """https?://[^\s<>()\[\]"']+\.(?:png|jpe?g|gif|webp|bmp|svg|pdf|txt|md|csv|json|zip|docx?|xlsx?|pptx?|mp3|wav|mp4|mov)(?:\?[^\s<>()\[\]"']*)?""",
        RegexOption.IGNORE_CASE
    )

    private val IMAGE_EXT = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg")

    fun extract(text: String): List<ReplyFile> {
        if (text.isBlank()) return emptyList()
        val found = LinkedHashMap<String, ReplyFile>()

        for (m in MD_LINK.findAll(text)) {
            val bang = m.groupValues[1] == "!"
            val label = m.groupValues[2].trim()
            val url = m.groupValues[4].trim()
            if (!isSupported(url)) continue
            val name = fileNameFor(url, label)
            found.putIfAbsent(url, ReplyFile(name, url, bang || looksLikeImage(url, name)))
        }

        for (m in BARE_FILE_URL.findAll(text)) {
            val url = m.value
            if (found.containsKey(url)) continue
            val name = fileNameFor(url, "")
            found[url] = ReplyFile(name, url, looksLikeImage(url, name))
        }

        return found.values.toList()
    }

    private fun isSupported(url: String): Boolean =
        url.startsWith("http://", true) || url.startsWith("https://", true) ||
            url.startsWith("data:", true)

    private fun looksLikeImage(url: String, name: String): Boolean {
        if (url.startsWith("data:image", ignoreCase = true)) return true
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in IMAGE_EXT
    }

    private fun fileNameFor(url: String, label: String): String {
        if (url.startsWith("data:", ignoreCase = true)) {
            val mime = url.substringAfter("data:", "").substringBefore(";").substringBefore(",")
            val ext = mime.substringAfter('/', "bin").take(8).ifBlank { "bin" }
            return sanitize(label.ifBlank { "file" }) + "." + ext
        }
        val path = url.substringBefore('?').substringBefore('#')
        val last = path.substringAfterLast('/', "")
        if (last.isNotBlank() && last.contains('.')) return sanitize(last)
        if (label.isNotBlank()) return sanitize(label)
        return "file-" + path.hashCode().toUInt().toString(16)
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().take(100).ifBlank { "file" }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    fun fetch(file: ReplyFile): ByteArray? {
        return try {
            if (file.isInline) {
                val payload = file.url.substringAfter(',', "")
                if (payload.isBlank()) return null
                if (file.url.substringBefore(',').contains("base64", ignoreCase = true)) {
                    android.util.Base64.decode(payload, android.util.Base64.DEFAULT)
                } else {
                    java.net.URLDecoder.decode(payload, "UTF-8").toByteArray()
                }
            } else {
                val request = Request.Builder().url(file.url).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return null
                    val body = response.body ?: return null
                    if (body.contentLength() > MAX_FETCH_BYTES) return null
                    val bytes = body.byteStream().readBytesCapped(MAX_FETCH_BYTES)
                    bytes
                }
            }
        } catch (t: Throwable) {
            null
        }
    }

    private fun java.io.InputStream.readBytesCapped(max: Long): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val r = read(buf)
            if (r == -1) break
            total += r
            if (total > max) return null
            out.write(buf, 0, r)
        }
        return out.toByteArray()
    }

    suspend fun saveToNewItem(
        context: Context,
        db: AppDatabase,
        asTask: Boolean,
        title: String,
        body: String,
        fileName: String,
        mime: String,
        bytes: ByteArray
    ): Boolean {
        val json = if (bytes.isEmpty() || fileName.isBlank()) "[]" else {
            val check = AttachmentLimits.checkSingle(bytes.size.toLong())
            if (!check.allowed) return false
            val id = AttachmentStore.importBytes(context, bytes) ?: return false
            Attachments.serialize(listOf(Attachment(mime = mime, data = id, name = fileName)))
        }
        val storedId = if (json == "[]") null else Attachments.parse(json).firstOrNull()?.data
        return try {
            if (asTask) {
                db.taskDao().insert(Task(title = title, notes = body, attachments = json))
            } else {
                db.noteDao().insert(Note(title = title, body = body, attachments = json))
            }
            true
        } catch (t: Throwable) {
            storedId?.let { AttachmentStore.delete(context, it) }
            false
        }
    }
}
