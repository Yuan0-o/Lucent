package com.lucent.app.data

import android.content.Context
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * Turns an attachment into **readable text**, so it can be previewed inside Lucent instead of only
 * being handed to another app (task A1).
 *
 * ### What this is, and what it deliberately is not
 *
 * This is a *text* extractor, not a document renderer. It will not lay out a Word file, draw a
 * slide, or reproduce a spreadsheet's grid — doing that faithfully needs an engine on the scale of
 * Apache POI plus a renderer, which is a large dependency to carry for a preview. What it does
 * instead is recover the words, in order, which is what a preview is actually for: confirming
 * *which* file this is and roughly what it says before deciding whether to open it properly.
 *
 * Saying so is part of the feature — the viewer prints a one-line "text preview, no layout" note
 * above the result rather than letting a user believe they are looking at the whole document.
 *
 * ### How the formats are read
 *
 *  - **Plain text and code** (`.md`, `.txt`, `.json`, `.kt`, `.py`, … — see [PLAIN_TEXT_EXTENSIONS])
 *    are decoded as UTF-8. Source files get no syntax highlighting; the task asked for code
 *    formats to be *readable as text*, and monospaced UTF-8 is exactly that.
 *  - **OOXML** (`.docx`, `.pptx`, `.xlsx`) are ZIP archives of XML, both of which the JVM and
 *    Android already have. Word's text lives in `<w:t>` runs, PowerPoint's in `<a:t>` runs, and
 *    Excel's in a shared-string table referenced by the sheet — so each is a targeted scan of the
 *    one entry that holds the words, with the structural tags turned into line breaks.
 *  - **RTF** is unwrapped by stripping its control words.
 *
 * Legacy binary `.doc`/`.ppt`/`.xls` (the pre-2007 OLE formats) are **not** supported: they are not
 * XML, not compressed, and not readable without a real parser. They keep the "Open with" hand-off.
 *
 * Everything here is pure Kotlin plus `java.util.zip`, so this file is byte-identical on Android
 * and desktop.
 */
object DocumentText {

    /**
     * The point past which a preview stops being a preview. Large enough that no ordinary document
     * is cut off, small enough that a pathological one cannot hold a phone's UI thread hostage when
     * the result is measured and laid out.
     */
    const val MAX_PREVIEW_CHARS = 120_000

    /** Extracted text plus whether [MAX_PREVIEW_CHARS] clipped it. */
    data class Result(val text: String, val truncated: Boolean)

    /** Files that are already text; the extension decides, because MIME types lie about `.md`. */
    private val PLAIN_TEXT_EXTENSIONS = setOf(
        // Documents and data
        "txt", "md", "markdown", "mdown", "mkd", "rst", "log", "csv", "tsv", "json", "jsonc",
        "xml", "yaml", "yml", "toml", "ini", "cfg", "conf", "properties", "env", "tex", "bib",
        "srt", "vtt", "diff", "patch", "gitignore", "editorconfig",
        // Code — previewed as plain text, which is what the task asked for
        "kt", "kts", "java", "scala", "groovy", "gradle", "py", "pyi", "rb", "php", "pl", "lua",
        "r", "jl", "go", "rs", "swift", "m", "mm", "c", "h", "cc", "cpp", "cxx", "hpp", "hh",
        "cs", "fs", "vb", "dart", "js", "mjs", "cjs", "jsx", "ts", "tsx", "vue", "svelte",
        "html", "htm", "css", "scss", "sass", "less", "sql", "sh", "bash", "zsh", "fish",
        "bat", "cmd", "ps1", "psm1", "makefile", "mk", "cmake", "dockerfile", "proto", "graphql",
        "gql", "asm", "s", "v", "vhd", "sv", "clj", "ex", "exs", "erl", "hs", "ml", "nim", "zig"
    )

    private val OOXML_EXTENSIONS = setOf("docx", "docm", "pptx", "pptm", "xlsx", "xlsm")

    /** True when [extract] has a real chance of producing something worth showing. */
    fun canExtract(att: Attachment): Boolean {
        if (att.isImage || att.isVideo || att.isAudio || att.isPdf) return false
        val ext = extensionOf(att.name)
        return ext in PLAIN_TEXT_EXTENSIONS ||
            ext in OOXML_EXTENSIONS ||
            ext == "rtf" ||
            looksTextualByMime(att.mime)
    }

    /**
     * Read [att] and return its text, or null when there is nothing readable in it. Blocking I/O —
     * call from a background dispatcher.
     */
    fun extract(context: Context, att: Attachment): Result? {
        val ext = extensionOf(att.name)
        // 32MB covers any realistic document; past that a "preview" is the wrong tool anyway.
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

    // -- plain text ----------------------------------------------------------------------------

    /**
     * Decode as UTF-8, refusing anything that looks binary. The NUL-byte test is the same one
     * [Attachments.decodeText] uses: no text encoding Lucent supports produces them, so their
     * presence means this is a binary file whose extension lied.
     */
    private fun decodeText(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return ""
        val sample = bytes.take(8000)
        if (sample.any { it.toInt() == 0 }) return null
        return try {
            // Strip a UTF-8 BOM; left in, it renders as a stray glyph on the first line.
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

    // -- OOXML ---------------------------------------------------------------------------------

    /**
     * Pull named entries out of a ZIP in one pass. Returns the raw bytes of every entry whose name
     * satisfies [wanted]; a corrupt archive yields whatever was readable before it broke rather
     * than nothing at all.
     */
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
            // Fall through with whatever was recovered.
        }
        return out
    }

    /**
     * Word. `word/document.xml` holds the body; `<w:t>` carries the characters and `</w:p>` ends a
     * paragraph. Scanning for both in one pass — rather than harvesting all the text nodes and then
     * guessing where the breaks went — is what keeps paragraphs apart.
     */
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

    /**
     * PowerPoint. One entry per slide, and ZIP order is not slide order, so the entries are sorted
     * by the number in their name — otherwise slide 10 reads before slide 2.
     */
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

    /**
     * Excel. Cell *values* are numbers and indices; the words live in a separate shared-string
     * table that `t="s"` cells point into, which is why this needs two entries rather than one.
     * Only the first worksheet is read — a preview of a workbook is a preview of its first sheet.
     */
    private fun extractXlsx(bytes: ByteArray): String? {
        val wanted = zipEntries(bytes) {
            it == "xl/sharedStrings.xml" || Regex("""xl/worksheets/sheet\d+\.xml""").matches(it)
        }
        val sheetName = wanted.keys.filter { it.startsWith("xl/worksheets/") }.minOrNull() ?: return null
        val sheet = wanted[sheetName]?.toString(Charsets.UTF_8) ?: return null

        val shared = wanted["xl/sharedStrings.xml"]?.toString(Charsets.UTF_8)?.let { xml ->
            Regex("""<si\b.*?</si>""", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { si ->
                // An <si> can be split across several <t> runs (mixed formatting); join them.
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
                        // Inline strings carry their own <t> nodes instead of an index.
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

    // -- RTF -----------------------------------------------------------------------------------

    /**
     * RTF is text wrapped in control words. Dropping the ones that carry no content — and the
     * `{\*\...}` groups, which are metadata by definition — leaves the prose behind. Not a parser;
     * enough for a preview.
     */
    private fun stripRtf(rtf: String): String = rtf
        .replace(Regex("""\{\\\*.*?}""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""\\par[d]?\b"""), "\n")
        .replace(Regex("""\\line\b"""), "\n")
        .replace(Regex("""\\tab\b"""), "\t")
        .replace(Regex("""\\'[0-9a-fA-F]{2}"""), "")
        .replace(Regex("""\\[a-zA-Z]+-?\d*\s?"""), "")
        .replace(Regex("""[{}]"""), "")

    // -- shared helpers ------------------------------------------------------------------------

    /** The five XML predefined entities plus numeric references, which OOXML uses for CJK. */
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
            // Ampersand last, or the replacements above would re-expand text that legitimately
            // contained "&lt;" as an escaped-escape.
            .replace("&amp;", "&")
    }

    fun extensionOf(name: String): String = name.substringAfterLast('.', "").lowercase()
}
