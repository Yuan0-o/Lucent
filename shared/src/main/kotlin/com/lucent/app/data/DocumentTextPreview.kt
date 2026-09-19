package com.lucent.app.data

import android.content.Context
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

object DocumentText {

    const val MAX_PREVIEW_CHARS = 120_000

    data class Result(val text: String, val truncated: Boolean)

    private val PLAIN_TEXT_EXTENSIONS = setOf(
        "txt", "md", "markdown", "mdown", "mkd", "rst", "log", "csv", "tsv", "json", "jsonc",
        "xml", "yaml", "yml", "toml", "ini", "cfg", "conf", "properties", "env", "tex", "bib",
        "srt", "vtt", "diff", "patch", "gitignore", "editorconfig",
        "kt", "kts", "java", "scala", "groovy", "gradle", "py", "pyi", "rb", "php", "pl", "lua",
        "r", "jl", "go", "rs", "swift", "m", "mm", "c", "h", "cc", "cpp", "cxx", "hpp", "hh",
        "cs", "fs", "vb", "dart", "js", "mjs", "cjs", "jsx", "ts", "tsx", "vue", "svelte",
        "html", "htm", "css", "scss", "sass", "less", "sql", "sh", "bash", "zsh", "fish",
        "bat", "cmd", "ps1", "psm1", "makefile", "mk", "cmake", "dockerfile", "proto", "graphql",
        "gql", "asm", "s", "v", "vhd", "sv", "clj", "ex", "exs", "erl", "hs", "ml", "nim", "zig"
    )

    private val OOXML_EXTENSIONS = setOf("docx", "docm", "pptx", "pptm", "xlsx", "xlsm")

    fun canExtract(att: Attachment): Boolean {
        if (att.isImage || att.isVideo || att.isAudio || att.isPdf) return false
        val ext = extensionOf(att.name)
        return ext in PLAIN_TEXT_EXTENSIONS ||
            ext in OOXML_EXTENSIONS ||
            ext == "rtf" ||
            looksTextualByMime(att.mime)
    }

    fun extract(context: Context, att: Attachment): Result? {
        val ext = extensionOf(att.name)
        val bytes = Attachments.readBytes(context, att, maxBytes = 32L * 1024 * 1024) ?: return null
        val raw = when {
            ext == "docx" || ext == "docm" -> extractDocx(bytes)
            ext == "pptx" || ext == "pptm" -> extractPptx(bytes)
            ext == "xlsx" || ext == "xlsm" -> extractXlsx(bytes)
            ext == "rtf" -> stripRtf(decodeText(bytes) ?: return null)
            else -> decodeText(bytes)
        } ?: return null
        val cleaned = raw.trim()
        if (cleaned.isEmpty()) return Result("", truncated = false)
        return if (cleaned.length > MAX_PREVIEW_CHARS) {
            Result(cleaned.take(MAX_PREVIEW_CHARS), truncated = true)
        } else {
            Result(cleaned, truncated = false)
        }
    }


    private fun decodeText(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return ""
        val sample = bytes.take(8000)
        if (sample.any { it.toInt() == 0 }) return null
        return try {
            val start = if (bytes.size >= 3 &&
                bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
            ) 3 else 0
            String(bytes, start, bytes.size - start, Charsets.UTF_8)
        } catch (t: Throwable) {
            null
        }
    }

    private fun looksTextualByMime(mime: String): Boolean =
        mime.startsWith("text/") ||
            mime == "application/json" ||
            mime == "application/xml" ||
            mime == "application/javascript" ||
            mime.endsWith("+json") ||
            mime.endsWith("+xml")


    private fun zipEntries(bytes: ByteArray, wanted: (String) -> Boolean): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory && wanted(entry.name)) {
                        out[entry.name] = zip.readBytes()
                    }
                    zip.closeEntry()
                }
            }
        } catch (t: Throwable) {
        }
        return out
    }

    private fun extractDocx(bytes: ByteArray): String? {
        val xml = zipEntries(bytes) { it == "word/document.xml" }["word/document.xml"]
            ?.toString(Charsets.UTF_8) ?: return null
        val token = Regex("""<w:tab\s*/>|<w:br\s*/>|<w:cr\s*/>|</w:p>|<w:t(?:\s[^>]*)?>(.*?)</w:t>""",
            RegexOption.DOT_MATCHES_ALL)
        val sb = StringBuilder()
        for (m in token.findAll(xml)) {
            when {
                m.groups[1] != null -> sb.append(unescapeXml(m.groupValues[1]))
                m.value.startsWith("<w:tab") -> sb.append('\t')
                else -> sb.append('\n')
            }
        }
        return sb.toString()
    }

    private fun extractPptx(bytes: ByteArray): String? {
        val slidePattern = Regex("""ppt/slides/slide(\d+)\.xml""")
        val slides = zipEntries(bytes) { slidePattern.matches(it) }
        if (slides.isEmpty()) return null
        val ordered = slides.entries.sortedBy {
            slidePattern.find(it.key)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE
        }
        val sb = StringBuilder()
        ordered.forEachIndexed { index, (_, data) ->
            val xml = data.toString(Charsets.UTF_8)
            if (index > 0) sb.append("\n\n")
            sb.append("— ").append(index + 1).append(" —\n")
            val token = Regex("""</a:p>|<a:t(?:\s[^>]*)?>(.*?)</a:t>""", RegexOption.DOT_MATCHES_ALL)
            for (m in token.findAll(xml)) {
                if (m.groups[1] != null) sb.append(unescapeXml(m.groupValues[1])) else sb.append('\n')
            }
        }
        return sb.toString()
    }

    private fun extractXlsx(bytes: ByteArray): String? {
        val wanted = zipEntries(bytes) {
            it == "xl/sharedStrings.xml" || Regex("""xl/worksheets/sheet\d+\.xml""").matches(it)
        }
        val sheetName = wanted.keys.filter { it.startsWith("xl/worksheets/") }.minOrNull() ?: return null
        val sheet = wanted[sheetName]?.toString(Charsets.UTF_8) ?: return null

        val shared = wanted["xl/sharedStrings.xml"]?.toString(Charsets.UTF_8)?.let { xml ->
            Regex("""<si\b.*?</si>""", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { si ->
                Regex("""<t(?:\s[^>]*)?>(.*?)</t>""", RegexOption.DOT_MATCHES_ALL)
                    .findAll(si.value)
                    .joinToString("") { unescapeXml(it.groupValues[1]) }
            }.toList()
        } ?: emptyList()

        val sb = StringBuilder()
        for (row in Regex("""<row\b.*?</row>""", RegexOption.DOT_MATCHES_ALL).findAll(sheet)) {
            val cells = Regex("""<c\b([^>]*)(?:/>|>(.*?)</c>)""", RegexOption.DOT_MATCHES_ALL)
                .findAll(row.value)
                .map { cell ->
                    val attrs = cell.groupValues[1]
                    val body = cell.groupValues[2]
                    when {
                        attrs.contains("t=\"s\"") -> {
                            val idx = Regex("""<v>(\d+)</v>""").find(body)?.groupValues?.get(1)?.toIntOrNull()
                            idx?.let { shared.getOrNull(it) } ?: ""
                        }
                        attrs.contains("t=\"inlineStr\"") ->
                            Regex("""<t(?:\s[^>]*)?>(.*?)</t>""", RegexOption.DOT_MATCHES_ALL)
                                .findAll(body).joinToString("") { unescapeXml(it.groupValues[1]) }
                        else -> Regex("""<v>(.*?)</v>""", RegexOption.DOT_MATCHES_ALL)
                            .find(body)?.groupValues?.get(1)?.let { unescapeXml(it) } ?: ""
                    }
                }
                .toList()
            if (cells.any { it.isNotBlank() }) sb.append(cells.joinToString("\t")).append('\n')
        }
        return sb.toString()
    }


    private fun stripRtf(rtf: String): String = rtf
        .replace(Regex("""\{\\\*.*?}""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""\\par[d]?\b"""), "\n")
        .replace(Regex("""\\line\b"""), "\n")
        .replace(Regex("""\\tab\b"""), "\t")
        .replace(Regex("""\\'[0-9a-fA-F]{2}"""), "")
        .replace(Regex("""\\[a-zA-Z]+-?\d*\s?"""), "")
        .replace(Regex("""[{}]"""), "")


    private fun unescapeXml(s: String): String {
        if ('&' !in s) return s
        return s.replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace(Regex("""&#x([0-9a-fA-F]+);""")) { m ->
                m.groupValues[1].toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
            }
            .replace(Regex("""&#(\d+);""")) { m ->
                m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
            }
            .replace("&amp;", "&")
    }

    fun extensionOf(name: String): String = name.substringAfterLast('.', "").lowercase()
}
