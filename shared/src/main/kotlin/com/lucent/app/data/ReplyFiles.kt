package com.lucent.app.data

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Files that arrive inside an assistant reply as a *reference* rather than as a stored attachment
 * (B-group task 7).
 *
 * ### The bug this exists for
 *
 * A reply that produces a file — an image the model generated, a document some provider hosted —
 * does not come back as [ChatMessage.attachmentData]. It comes back as Markdown in the reply TEXT:
 * `![image](https://…/6ae8139d….png)`. Everything downstream treated that as ordinary prose, so:
 *
 *  - the per-reply download modal listed only "the reply as .txt" and any real attachment, never
 *    the file the user could plainly see in the message;
 *  - saving the reply to a note or task stored the literal Markdown, which is why a saved note
 *    ended up containing nothing but a bare URL;
 *  - the file itself was never fetched at all, so nothing was ever actually downloaded.
 *
 * So the file was visible, referenced, and completely unreachable. [extract] finds those
 * references and [fetch] turns one into bytes, which is all the UI needs to offer it like any
 * other attachment.
 *
 * ### Scope
 *
 * Only `http`/`https` and `data:` references. `file://` and anything else is deliberately ignored:
 * a reply is untrusted text, and a model that emits `file:///data/data/...` must never cause the
 * app to read a local path and hand it back to the user as "your file".
 */
object ReplyFiles {

    /** How much of a remote file to accept. Matches the chat upload cap so the two agree. */
    const val MAX_FETCH_BYTES = 20L * 1024 * 1024

    /**
     * One file referenced by a reply.
     *
     * [name] is the best filename we can derive — the URL's last path segment when it has one, the
     * Markdown label otherwise, and a generated name as a last resort. It is only ever a
     * *suggestion* for the save dialog; nothing is keyed on it.
     */
    data class ReplyFile(
        val name: String,
        val url: String,
        val isImage: Boolean
    ) {
        /** A data: URL carries its own bytes and needs no network. */
        val isInline: Boolean get() = url.startsWith("data:", ignoreCase = true)
    }

    // ![alt](url) and [label](url). The image form is tried first so an image is not mistaken for
    // a plain link, and the URL group stops at whitespace or the closing paren.
    private val MD_LINK = Regex("""(!?)\[([^\]]*)]\(\s*(<?)([^)\s>]+)\3\s*(?:"[^"]*")?\s*\)""")

    // A bare URL that plainly names a file. Deliberately narrow — matching every bare URL would
    // offer to "download" every ordinary link the assistant mentions in passing.
    private val BARE_FILE_URL = Regex(
        """https?://[^\s<>()\[\]"']+\.(?:png|jpe?g|gif|webp|bmp|svg|pdf|txt|md|csv|json|zip|docx?|xlsx?|pptx?|mp3|wav|mp4|mov)(?:\?[^\s<>()\[\]"']*)?""",
        RegexOption.IGNORE_CASE
    )

    private val IMAGE_EXT = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg")

    /**
     * Every downloadable file [text] refers to, in the order they appear, de-duplicated by URL.
     *
     * Never throws and never blocks: this runs on the composition path each time the download modal
     * opens, so it is pure string work over a reply that is already in memory.
     */
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

    /** Derive a sensible filename. Falls back through URL path → Markdown label → generated. */
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

    /**
     * Resolve one reference to bytes, or null on any failure.
     *
     * Blocking; call it off the main thread. Failures are swallowed and reported as null rather
     * than thrown: the caller is a download button, and "couldn't get that file" is a message, not
     * a crash. The size cap is enforced against the declared content length AND the actual body, so
     * a server that lies about its length cannot make the app allocate without bound.
     */
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

    /** Read at most [max] bytes; returns null the moment the stream goes over, rather than truncating. */
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

    /**
     * Save [bytes] onto a brand-new note or task as a real attachment (the "转存到笔记或任务" half
     * of task 7).
     *
     * Goes through [AttachmentStore] exactly like the assistant's own attach tools do, so the file
     * lands encrypted in the same store, counts against the same limits, and is indistinguishable
     * afterwards from one attached any other way. [body] becomes the note body / task notes, so the
     * surrounding reply text is kept alongside the file rather than lost.
     *
     * Returns true on success. A disk failure returns false and writes nothing, rather than leaving
     * a note that claims to have an attachment it does not have.
     */
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
        // No bytes means "save the reply itself, it just has no file" — a legitimate case, and one
        // that must NOT create a zero-length attachment nobody asked for.
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
            // The bytes are already in the store; drop them so a failed save leaves nothing behind.
            storedId?.let { AttachmentStore.delete(context, it) }
            false
        }
    }
}
