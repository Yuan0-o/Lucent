package com.lucent.app.harness.ooxml

import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File

private const val DOCUMENT_PART = "word/document.xml"
private const val DOCUMENT_RELS_PART = "word/_rels/document.xml.rels"
private const val STYLES_PART = "word/styles.xml"
private const val NUMBERING_PART = "word/numbering.xml"
private const val SETTINGS_PART = "word/settings.xml"
private const val HEADER_PART = "word/header1.xml"
private const val FOOTER_PART = "word/footer1.xml"
private const val CORE_PART = "docProps/core.xml"
private const val APP_PART = "docProps/app.xml"

private const val REL_STYLES = NS_OFFICE_RELATIONSHIPS + "/styles"
private const val REL_NUMBERING = NS_OFFICE_RELATIONSHIPS + "/numbering"
private const val REL_SETTINGS = NS_OFFICE_RELATIONSHIPS + "/settings"
private const val REL_HEADER = NS_OFFICE_RELATIONSHIPS + "/header"
private const val REL_FOOTER = NS_OFFICE_RELATIONSHIPS + "/footer"
private const val REL_IMAGE = NS_OFFICE_RELATIONSHIPS + "/image"
private const val REL_HYPERLINK = NS_OFFICE_RELATIONSHIPS + "/hyperlink"

private const val CONTENT_TYPE_DOCUMENT = "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"
private const val CONTENT_TYPE_STYLES = "application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"
private const val CONTENT_TYPE_SETTINGS = "application/vnd.openxmlformats-officedocument.wordprocessingml.settings+xml"
private const val CONTENT_TYPE_NUMBERING = "application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml"
private const val CONTENT_TYPE_HEADER = "application/vnd.openxmlformats-officedocument.wordprocessingml.header+xml"
private const val CONTENT_TYPE_FOOTER = "application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"
private const val CONTENT_TYPE_CORE = "application/vnd.openxmlformats-package.core-properties+xml"
private const val CONTENT_TYPE_APP = "application/vnd.openxmlformats-officedocument.extended-properties+xml"

private const val BODY_NAMESPACES =
    "xmlns:w=\"$NS_WORDPROCESSING\" xmlns:r=\"$NS_OFFICE_RELATIONSHIPS\" xmlns:wp=\"$NS_WORD_DRAWING\" " +
        "xmlns:a=\"$NS_DRAWING\" xmlns:pic=\"$NS_PICTURE\" xmlns:mc=\"$NS_MARKUP_COMPATIBILITY\""

object Docx {

    fun create(specJson: String, out: File): String {
        val spec = jsonObject(specJson, "document spec")
        val title = stringOf(spec, "title").trim()
        val author = stringOf(spec, "author").trim()
        val header = stringOf(spec, "header").trim()
        val footer = stringOf(spec, "footer").trim()
        val pageNumbers = spec.optBoolean("page_numbers", false)
        val toc = spec.optBoolean("toc", false)
        val blocks = readBlocks(spec)
        if (blocks.isEmpty() && title.isEmpty()) {
            throw IllegalArgumentException("The document spec needs a title, a content string or a blocks array")
        }
        val sink = CreateSink()
        val writer = DocBlocks(sink, out.parentFile ?: File("."))
        val body = mutableListOf<XmlBuilder>()
        if (title.isNotEmpty()) body.add(writer.title(title))
        if (toc) {
            body.add(writer.tocHeading("Contents"))
            body.add(writer.tocField())
        }
        body.addAll(writer.render(blocks))
        val headerId = if (header.isNotEmpty()) sink.relationship(REL_HEADER, "header1.xml", false) else ""
        val footerId = if (footer.isNotEmpty() || pageNumbers) sink.relationship(REL_FOOTER, "footer1.xml", false) else ""
        sink.relationship(REL_STYLES, "styles.xml", false)
        sink.relationship(REL_NUMBERING, "numbering.xml", false)
        sink.relationship(REL_SETTINGS, "settings.xml", false)
        val hasHeader = headerId.isNotEmpty()
        val hasFooter = footerId.isNotEmpty()
        val entries = mutableListOf<Pair<String, ByteArray>>()
        entries.add(CONTENT_TYPES_PART to documentBytes(contentTypes(hasHeader, hasFooter)))
        entries.add("_rels/.rels" to documentBytes(packageRels()))
        entries.add(CORE_PART to documentBytes(coreProperties(title, author)))
        entries.add(APP_PART to APP_XML.toByteArray(Charsets.UTF_8))
        entries.add(DOCUMENT_PART to documentBytes(documentRoot(body, writer.section(headerId, footerId))))
        entries.add(STYLES_PART to STYLES_XML.toByteArray(Charsets.UTF_8))
        entries.add(NUMBERING_PART to NUMBERING_XML.toByteArray(Charsets.UTF_8))
        entries.add(SETTINGS_PART to SETTINGS_XML.toByteArray(Charsets.UTF_8))
        if (hasHeader) entries.add(HEADER_PART to headerFooterPart(false, header, false))
        if (hasFooter) entries.add(FOOTER_PART to headerFooterPart(true, footer, pageNumbers))
        entries.add(DOCUMENT_RELS_PART to documentBytes(documentRelationships(sink.rels.values)))
        sink.media.forEach { item -> entries.add("word/${item.key}" to item.value) }
        writZip(out, entries)
        return describe(blocks)
    }

    fun read(file: File, maxChars: Int = 20000): String {
        val parts = readZip(file)
        val bytes = parts[DOCUMENT_PART]
            ?: throw IllegalArgumentException("${file.name} is not a Word document: word/document.xml is missing")
        val document = parse(bytes)
        val targets = relationshipTargets(parts)
        val formats = numberingFormats(parts)
        val body = children(document.documentElement, "body").firstOrNull() ?: document.documentElement
        val out = StringBuilder()
        val counters = HashMap<String, Int>()
        var paragraphs = 0
        var tables = 0
        var images = 0
        directChildren(body).forEach { element ->
            when (localName(element)) {
                "p" -> {
                    paragraphs++
                    images += descendants(element, "blip").size
                    val line = paragraphText(element, targets, formats, counters)
                    if (line.isNotEmpty()) out.append(line).append('\n')
                }
                "tbl" -> {
                    tables++
                    out.append(tableText(element)).append('\n')
                }
                "sdt" -> {
                    children(element, "sdtContent").forEach { content ->
                        directChildren(content).forEach { nested ->
                            when (localName(nested)) {
                                "p" -> {
                                    paragraphs++
                                    images += descendants(nested, "blip").size
                                    val line = paragraphText(nested, targets, formats, counters)
                                    if (line.isNotEmpty()) out.append(line).append('\n')
                                }
                                "tbl" -> {
                                    tables++
                                    out.append(tableText(nested)).append('\n')
                                }
                                else -> {
                                }
                            }
                        }
                    }
                }
                else -> {
                }
            }
        }
        val head = StringBuilder()
        val title = coreProperty(parts, "title")
        if (title.isNotEmpty()) head.append("Title: ").append(title).append('\n')
        head.append("Word document: ").append(paragraphs).append(' ').append(word(paragraphs, "paragraph"))
        head.append(", ").append(tables).append(' ').append(word(tables, "table"))
        if (images > 0) head.append(", ").append(images).append(' ').append(word(images, "image"))
        head.append('\n')
        var result = head.toString() + "\n" + out.toString().trimEnd() + "\n"
        if (maxChars > 0 && result.length > maxChars) {
            result = result.take(maxChars) + "\n… truncated at $maxChars characters"
        }
        return result
    }

    fun edit(file: File, opsJson: String): String {
        if (!file.exists()) throw IllegalArgumentException("${file.name} does not exist")
        val ops = jsonArray(opsJson, "document edit operations")
        if (ops.length() == 0) throw IllegalArgumentException("Give at least one edit operation")
        val parts = readZip(file).toMutableMap()
        val bytes = parts[DOCUMENT_PART]
            ?: throw IllegalArgumentException("${file.name} is not a Word document: word/document.xml is missing")
        val document = parse(bytes)
        val rels = parse(parts[DOCUMENT_RELS_PART] ?: emptyRelationships())
        val sink = EditSink(parts, rels)
        val writer = DocBlocks(sink, file.parentFile ?: File("."))
        val body = children(document.documentElement, "body").firstOrNull()
            ?: throw IllegalArgumentException("word/document.xml has no body")
        val applied = LinkedHashSet<String>()
        for (index in 0 until ops.length()) {
            val op = ops.optJSONObject(index)
                ?: throw IllegalArgumentException("Operation ${index + 1} is not a JSON object")
            val kind = stringOf(op, "op").trim().lowercase()
            when (kind) {
                "append" -> {
                    val blocks = markdownBlocks(stringOf(op, "content"))
                    if (blocks.isEmpty()) throw IllegalArgumentException("The append op needs some markdown in \"content\"")
                    insert(document, body, writer.render(blocks))
                }
                "append_blocks" -> {
                    val blocks = readBlocks(op)
                    if (blocks.isEmpty()) throw IllegalArgumentException("The append_blocks op needs a non-empty \"blocks\" array")
                    insert(document, body, writer.render(blocks))
                }
                "replace" -> replaceText(document, op)
                "set_header" -> setHeaderFooter(parts, rels, document, false, stringOf(op, "text"), false)
                "set_footer" -> setHeaderFooter(
                    parts, rels, document, true, stringOf(op, "text"), op.optBoolean("page_numbers", false)
                )
                "insert_image" -> insert(
                    document, body, writer.image(stringOf(op, "path"), op.optInt("width", 480), stringOf(op, "caption"))
                )
                "delete_paragraph" -> deleteParagraph(document, stringOf(op, "find"))
                "set_title" -> setTitle(parts, document, stringOf(op, "text"))
                else -> throw IllegalArgumentException("Unknown document op: $kind")
            }
            applied.add(kind)
        }
        parts[DOCUMENT_PART] = serialize(document)
        parts[DOCUMENT_RELS_PART] = serialize(rels)
        if (sink.addedMedia) ensureImageDefaults(parts)
        writZip(file, parts.entries.map { it.key to it.value })
        return "Applied ${ops.length()} operation(s): ${applied.joinToString(", ")}"
    }

    private fun jsonObject(text: String, what: String): JSONObject {
        if (text.isBlank()) throw IllegalArgumentException("The $what is empty")
        return try {
            JSONObject(text)
        } catch (e: Exception) {
            throw IllegalArgumentException("The $what is not valid JSON: ${e.message ?: "parse error"}")
        }
    }

    private fun jsonArray(text: String, what: String): JSONArray {
        if (text.isBlank()) throw IllegalArgumentException("The $what is empty")
        return try {
            JSONArray(text)
        } catch (e: Exception) {
            throw IllegalArgumentException("The $what is not valid JSON: ${e.message ?: "parse error"}")
        }
    }

    private fun readBlocks(spec: JSONObject): List<DocBlock> {
        val array = spec.optJSONArray("blocks")
        if (array != null) {
            val out = mutableListOf<DocBlock>()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index)
                    ?: throw IllegalArgumentException("Block ${index + 1} is not a JSON object")
                out.add(block(item))
            }
            return out
        }
        return markdownBlocks(stringOf(spec, "content"))
    }

    private fun markdownBlocks(text: String): List<DocBlock> {
        if (text.isBlank()) return emptyList()
        return SimpleMarkdown.blocks(text).mapNotNull { block ->
            when (block.type) {
                "heading" -> DocBlock.Heading(block.level.coerceIn(1, 4), block.text)
                "paragraph" -> DocBlock.Paragraph(block.text, "", null)
                "bullets" -> DocBlock.Listing(block.items, false)
                "numbers" -> DocBlock.Listing(block.items, true)
                "quote" -> DocBlock.Quote(block.text)
                "code" -> DocBlock.Code(block.text)
                "rule" -> DocBlock.Rule
                "table" -> {
                    val header = block.rows.firstOrNull() ?: emptyList()
                    DocBlock.Table(header, block.rows.drop(1), emptyList())
                }
                else -> null
            }
        }
    }

    private fun block(spec: JSONObject): DocBlock {
        val type = stringOf(spec, "type", "paragraph").trim().lowercase()
        return when (type) {
            "heading", "title" -> DocBlock.Heading(spec.optInt("level", 1).coerceIn(1, 4), stringOf(spec, "text"))
            "paragraph", "para", "text" -> DocBlock.Paragraph(
                stringOf(spec, "text"), stringOf(spec, "align"), spec.optJSONObject("style")
            )
            "bullets", "bullet", "ul", "list" -> DocBlock.Listing(stringList(spec.optJSONArray("items")), false)
            "numbers", "numbered", "ol" -> DocBlock.Listing(stringList(spec.optJSONArray("items")), true)
            "table" -> DocBlock.Table(
                stringList(spec.optJSONArray("header")),
                rowList(spec.optJSONArray("rows")),
                intList(spec.optJSONArray("widths"))
            )
            "image", "picture" -> DocBlock.Picture(
                stringOf(spec, "path"), spec.optInt("width", 480), stringOf(spec, "caption")
            )
            "pagebreak", "page_break", "break" -> DocBlock.Break
            "quote" -> DocBlock.Quote(stringOf(spec, "text"))
            "code" -> DocBlock.Code(stringOf(spec, "text"))
            "link" -> DocBlock.Link(stringOf(spec, "text"), stringOf(spec, "url"))
            "rule", "divider", "hr" -> DocBlock.Rule
            else -> throw IllegalArgumentException("Unknown block type: $type")
        }
    }

    private fun stringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val out = mutableListOf<String>()
        for (index in 0 until array.length()) out.add(array.optString(index, ""))
        return out
    }

    private fun intList(array: JSONArray?): List<Int> {
        if (array == null) return emptyList()
        val out = mutableListOf<Int>()
        for (index in 0 until array.length()) out.add(array.optInt(index, 0))
        return out
    }

    private fun rowList(array: JSONArray?): List<List<String>> {
        if (array == null) return emptyList()
        val out = mutableListOf<List<String>>()
        for (index in 0 until array.length()) {
            val row = array.optJSONArray(index) ?: continue
            out.add(stringList(row))
        }
        return out
    }

    private fun describe(blocks: List<DocBlock>): String {
        var paragraphs = 0
        var tables = 0
        var images = 0
        blocks.forEach { block ->
            when (block) {
                is DocBlock.Table -> tables++
                is DocBlock.Picture -> images++
                is DocBlock.Listing -> paragraphs += block.items.size
                else -> paragraphs++
            }
        }
        val parts = mutableListOf<String>()
        parts.add("$paragraphs ${word(paragraphs, "paragraph")}")
        if (tables > 0) parts.add("$tables ${word(tables, "table")}")
        if (images > 0) parts.add("$images ${word(images, "image")}")
        return parts.joinToString(", ")
    }

    private fun word(count: Int, name: String): String = if (count == 1) name else "${name}s"

    private fun contentTypes(hasHeader: Boolean, hasFooter: Boolean): XmlBuilder {
        val root = node("Types").attr("xmlns", NS_CONTENT_TYPES)
        root.child("Default").attr("Extension", "rels")
            .attr("ContentType", "application/vnd.openxmlformats-package.relationships+xml")
        root.child("Default").attr("Extension", "xml").attr("ContentType", "application/xml")
        root.child("Default").attr("Extension", "png").attr("ContentType", "image/png")
        root.child("Default").attr("Extension", "jpeg").attr("ContentType", "image/jpeg")
        root.child("Default").attr("Extension", "jpg").attr("ContentType", "image/jpeg")
        root.child("Default").attr("Extension", "gif").attr("ContentType", "image/gif")
        overridePart(root, "/word/document.xml", CONTENT_TYPE_DOCUMENT)
        overridePart(root, "/word/styles.xml", CONTENT_TYPE_STYLES)
        overridePart(root, "/word/settings.xml", CONTENT_TYPE_SETTINGS)
        overridePart(root, "/word/numbering.xml", CONTENT_TYPE_NUMBERING)
        if (hasHeader) overridePart(root, "/word/header1.xml", CONTENT_TYPE_HEADER)
        if (hasFooter) overridePart(root, "/word/footer1.xml", CONTENT_TYPE_FOOTER)
        overridePart(root, "/docProps/core.xml", CONTENT_TYPE_CORE)
        overridePart(root, "/docProps/app.xml", CONTENT_TYPE_APP)
        return root
    }

    private fun overridePart(root: XmlBuilder, part: String, contentType: String) {
        root.child("Override").attr("PartName", part).attr("ContentType", contentType)
    }

    private fun packageRels(): XmlBuilder {
        val root = node("Relationships").attr("xmlns", NS_PACKAGE_RELATIONSHIPS)
        root.child("Relationship").attr("Id", "rId1").attr("Type", REL_OFFICE_DOCUMENT).attr("Target", "word/document.xml")
        root.child("Relationship").attr("Id", "rId2").attr("Type", REL_CORE_PROPERTIES).attr("Target", CORE_PART)
        root.child("Relationship").attr("Id", "rId3").attr("Type", REL_EXTENDED_PROPERTIES).attr("Target", APP_PART)
        return root
    }

    private fun documentRelationships(rels: Collection<DocxRel>): XmlBuilder {
        val root = node("Relationships").attr("xmlns", NS_PACKAGE_RELATIONSHIPS)
        rels.forEach { rel ->
            root.child("Relationship").attr("Id", rel.id).attr("Type", rel.type).attr("Target", rel.target)
                .attrIf(rel.external, "TargetMode", "External")
        }
        return root
    }

    private fun emptyRelationships(): ByteArray =
        documentBytes(node("Relationships").attr("xmlns", NS_PACKAGE_RELATIONSHIPS))

    private fun coreProperties(title: String, author: String): XmlBuilder {
        val root = node("cp:coreProperties")
        root.attr("xmlns:cp", NS_CORE_PROPERTIES)
            .attr("xmlns:dc", NS_DUBLIN_CORE)
            .attr("xmlns:dcterms", NS_DCTERMS)
            .attr("xmlns:dcmitype", "http://purl.org/dc/dcmitype/")
            .attr("xmlns:xsi", NS_XSI)
        if (title.isNotEmpty()) root.child("dc:title").text(title)
        val name = if (author.isEmpty()) "Lucent" else author
        root.child("dc:creator").text(name)
        root.child("cp:lastModifiedBy").text(name)
        root.child("cp:revision").text("1")
        val stamp = utcStamp()
        root.child("dcterms:created").attr("xsi:type", "dcterms:W3CDTF").text(stamp)
        root.child("dcterms:modified").attr("xsi:type", "dcterms:W3CDTF").text(stamp)
        return root
    }

    private fun documentRoot(body: List<XmlBuilder>, section: XmlBuilder): XmlBuilder {
        val root = node("w:document")
        root.attr("xmlns:wpc", "http://schemas.microsoft.com/office/word/2010/wordprocessingCanvas")
            .attr("xmlns:mc", NS_MARKUP_COMPATIBILITY)
            .attr("xmlns:o", "urn:schemas-microsoft-com:office:office")
            .attr("xmlns:r", NS_OFFICE_RELATIONSHIPS)
            .attr("xmlns:m", "http://schemas.openxmlformats.org/officeDocument/2006/math")
            .attr("xmlns:v", "urn:schemas-microsoft-com:vml")
            .attr("xmlns:wp14", "http://schemas.microsoft.com/office/word/2010/wordprocessingDrawing")
            .attr("xmlns:wp", NS_WORD_DRAWING)
            .attr("xmlns:w10", "urn:schemas-microsoft-com:office:word")
            .attr("xmlns:w", NS_WORDPROCESSING)
            .attr("xmlns:w14", "http://schemas.microsoft.com/office/word/2010/wordml")
            .attr("xmlns:w15", "http://schemas.microsoft.com/office/word/2012/wordml")
            .attr("xmlns:wpg", "http://schemas.microsoft.com/office/word/2010/wordprocessingGroup")
            .attr("xmlns:wpi", "http://schemas.microsoft.com/office/word/2010/wordprocessingInk")
            .attr("xmlns:wne", "http://schemas.microsoft.com/office/word/2006/wordml")
            .attr("xmlns:wps", "http://schemas.microsoft.com/office/word/2010/wordprocessingShape")
            .attr("xmlns:a", NS_DRAWING)
            .attr("xmlns:pic", NS_PICTURE)
            .attr("mc:Ignorable", "w14 w15 wp14")
        val bodyNode = root.child("w:body")
        bodyNode.addAll(body)
        bodyNode.add(section)
        return root
    }

    private fun headerFooterPart(footer: Boolean, text: String, pageNumbers: Boolean): ByteArray {
        val root = node(if (footer) "w:ftr" else "w:hdr")
        root.attr("xmlns:w", NS_WORDPROCESSING)
            .attr("xmlns:r", NS_OFFICE_RELATIONSHIPS)
            .attr("xmlns:wp", NS_WORD_DRAWING)
            .attr("xmlns:a", NS_DRAWING)
            .attr("xmlns:pic", NS_PICTURE)
            .attr("xmlns:mc", NS_MARKUP_COMPATIBILITY)
        root.add(headerFooterParagraph(text, pageNumbers))
        return documentBytes(root)
    }

    private fun headerFooterParagraph(text: String, pageNumbers: Boolean): XmlBuilder {
        val paragraph = node("w:p")
        paragraph.child("w:pPr").child("w:jc").attr("w:val", "center")
        if (text.isNotEmpty()) paragraph.add(simpleRun(text))
        if (pageNumbers) {
            if (text.isNotEmpty()) paragraph.add(simpleRun(" "))
            paragraph.child("w:r").child("w:fldChar").attr("w:fldCharType", "begin")
            paragraph.child("w:r").child("w:instrText").attr("xml:space", "preserve").text(" PAGE ")
            paragraph.child("w:r").child("w:fldChar").attr("w:fldCharType", "separate")
            paragraph.child("w:r").child("w:t").text("1")
            paragraph.child("w:r").child("w:fldChar").attr("w:fldCharType", "end")
        }
        return paragraph
    }

    private fun simpleRun(text: String): XmlBuilder {
        val run = node("w:r")
        run.child("w:t").attr("xml:space", "preserve").text(text)
        return run
    }

    private fun paragraphStyle(paragraph: Element): String {
        val props = children(paragraph, "pPr").firstOrNull() ?: return ""
        return attr(children(props, "pStyle").firstOrNull(), "w:val")
    }

    private fun numberingId(paragraph: Element): String {
        val props = children(paragraph, "pPr").firstOrNull() ?: return ""
        val numbering = children(props, "numPr").firstOrNull() ?: return ""
        return attr(children(numbering, "numId").firstOrNull(), "w:val")
    }

    private fun headingLevel(paragraph: Element, style: String): Int {
        val match = Regex("""(?i)^heading([1-9])$""").matchEntire(style)
        if (match != null) return match.groupValues[1].toInt()
        if (style.equals("Title", ignoreCase = true)) return 1
        if (style.equals("Subtitle", ignoreCase = true)) return 2
        val props = children(paragraph, "pPr").firstOrNull()
        if (props != null) {
            val outline = attr(children(props, "outlineLvl").firstOrNull(), "w:val").toIntOrNull()
            if (outline != null && outline in 0..5) return outline + 1
        }
        return 0
    }

    private fun paragraphText(
        paragraph: Element,
        targets: Map<String, String>,
        formats: Map<String, String>,
        counters: MutableMap<String, Int>
    ): String {
        if (descendants(paragraph, "fldChar").isNotEmpty()) return ""
        val style = paragraphStyle(paragraph)
        val raw = textOf(paragraph)
        val text = raw.trim()
        val markers = imageMarkers(paragraph, targets)
        if (text.isEmpty() && markers.isEmpty()) return ""
        val level = headingLevel(paragraph, style)
        val out = StringBuilder()
        if (level > 0) {
            out.append("#".repeat(level.coerceAtMost(6))).append(' ')
        } else {
            val numbering = numberingId(paragraph)
            when {
                numbering.isEmpty() -> if (style.equals("Quote", ignoreCase = true)) out.append("> ")
                formats[numbering] == "bullet" -> out.append("- ")
                else -> {
                    val next = (counters[numbering] ?: 0) + 1
                    counters[numbering] = next
                    out.append(next).append(". ")
                }
            }
        }
        if (style.equals("Code", ignoreCase = true)) {
            out.append("```\n").append(raw.trimEnd()).append("\n```")
        } else {
            out.append(text)
        }
        markers.forEach { marker ->
            if (out.isNotEmpty()) out.append(' ')
            out.append(marker)
        }
        return out.toString()
    }

    private fun imageMarkers(paragraph: Element, targets: Map<String, String>): List<String> =
        descendants(paragraph, "blip").mapNotNull { blip ->
            val id = attr(blip, "r:embed").ifEmpty { attr(blip, "r:link") }
            if (id.isEmpty()) null else "[image ${targets[id] ?: id}]"
        }

    private fun tableText(table: Element): String {
        val rows = children(table, "tr").map { row ->
            children(row, "tc").map { cell -> textOf(cell).trim().replace('\n', ' ') }
        }
        if (rows.isEmpty()) return ""
        val columns = rows.maxOf { it.size }.coerceAtLeast(1)
        val out = StringBuilder()
        rows.forEachIndexed { index, cells ->
            out.append("| ")
            for (column in 0 until columns) {
                out.append(cells.getOrNull(column) ?: "")
                if (column < columns - 1) out.append(" | ")
            }
            out.append(" |\n")
            if (index == 0) {
                out.append("| ")
                for (column in 0 until columns) {
                    out.append("---")
                    if (column < columns - 1) out.append(" | ")
                }
                out.append(" |\n")
            }
        }
        return out.toString().trimEnd()
    }

    private fun numberingFormats(parts: Map<String, ByteArray>): Map<String, String> {
        val bytes = parts[NUMBERING_PART] ?: return emptyMap()
        val document = try {
            parse(bytes)
        } catch (e: Exception) {
            return emptyMap()
        }
        val abstracts = HashMap<String, Map<String, String>>()
        children(document.documentElement, "abstractNum").forEach { abstract ->
            val levels = HashMap<String, String>()
            children(abstract, "lvl").forEach { level ->
                levels[attr(level, "w:ilvl")] = attr(children(level, "numFmt").firstOrNull(), "w:val")
            }
            abstracts[attr(abstract, "w:abstractNumId")] = levels
        }
        val out = HashMap<String, String>()
        children(document.documentElement, "num").forEach { num ->
            val abstractId = attr(children(num, "abstractNumId").firstOrNull(), "w:val")
            val levels = abstracts[abstractId] ?: return@forEach
            out[attr(num, "w:numId")] = levels["0"] ?: levels.values.firstOrNull() ?: ""
        }
        return out
    }

    private fun relationshipTargets(parts: Map<String, ByteArray>): Map<String, String> {
        val bytes = parts[DOCUMENT_RELS_PART] ?: return emptyMap()
        val document = try {
            parse(bytes)
        } catch (e: Exception) {
            return emptyMap()
        }
        val out = HashMap<String, String>()
        children(document.documentElement, "Relationship").forEach { rel ->
            val target = attr(rel, "Target")
            val external = attr(rel, "TargetMode").equals("External", ignoreCase = true)
            out[attr(rel, "Id")] = if (external) target else normalizePart(target)
        }
        return out
    }

    private fun normalizePart(target: String): String {
        val clean = target.trim().replace('\\', '/')
        if (clean.startsWith("/")) return clean.removePrefix("/")
        if (clean.startsWith("word/")) return clean
        return "word/$clean"
    }

    private fun insert(document: Document, body: Element, nodes: List<XmlBuilder>) {
        val section = children(body, "sectPr").firstOrNull()
        nodes.forEach { builder ->
            val element = appendXml(document, body, builder.render(), BODY_NAMESPACES)
            if (element != null && section != null) body.insertBefore(element, section)
        }
    }

    private fun replaceText(document: Document, op: JSONObject) {
        val find = stringOf(op, "find")
        if (find.isEmpty()) throw IllegalArgumentException("The replace op needs a non-empty \"find\" value")
        val replacement = stringOf(op, "replace")
        val all = op.optBoolean("all", false)
        var done = 0
        val texts = descendants(document.documentElement, "t")
        for (element in texts) {
            if (!all && done > 0) break
            val node = element.firstChild ?: continue
            val value = node.nodeValue ?: continue
            if (!value.contains(find)) continue
            node.nodeValue = if (all) value.replace(find, replacement) else value.replaceFirst(find, replacement)
            done++
        }
        if (done > 0) return
        val paragraphs = descendants(document.documentElement, "p")
        for (paragraph in paragraphs) {
            if (!all && done > 0) break
            val full = textOf(paragraph)
            if (!full.contains(find)) continue
            val replaced = if (all) full.replace(find, replacement) else full.replaceFirst(find, replacement)
            setParagraphText(document, paragraph, replaced)
            done++
        }
        if (done == 0) throw IllegalArgumentException("No text matching \"$find\" was found")
    }

    private fun deleteParagraph(document: Document, find: String) {
        if (find.isBlank()) throw IllegalArgumentException("The delete_paragraph op needs a \"find\" value")
        val paragraphs = descendants(document.documentElement, "p")
        val target = paragraphs.firstOrNull { textOf(it).trim() == find.trim() }
            ?: throw IllegalArgumentException("No paragraph matches \"$find\"")
        target.parentNode?.removeChild(target)
    }

    private fun setParagraphText(document: Document, paragraph: Element, text: String) {
        val nodes = descendants(paragraph, "t")
        if (nodes.isEmpty()) {
            val run = appendXml(document, paragraph, "<w:r><w:t xml:space=\"preserve\"></w:t></w:r>", BODY_NAMESPACES)
            val created = run?.let { children(it, "t").firstOrNull() }
            if (created != null) {
                created.appendChild(document.createTextNode(text))
                return
            }
            return
        }
        val first = nodes.first()
        while (first.firstChild != null) first.removeChild(first.firstChild)
        first.appendChild(document.createTextNode(text))
        nodes.drop(1).forEach { node ->
            while (node.firstChild != null) node.removeChild(node.firstChild)
        }
    }

    private fun setTitle(parts: MutableMap<String, ByteArray>, document: Document, text: String) {
        if (text.isBlank()) throw IllegalArgumentException("The set_title op needs a \"text\" value")
        val body = children(document.documentElement, "body").firstOrNull() ?: return
        val existing = children(body, "p").firstOrNull { paragraphStyle(it) == "Title" }
        if (existing != null) {
            setParagraphText(document, existing, text)
        } else {
            val section = children(body, "sectPr").firstOrNull()
            val created = parseFragment(
                "<w:p><w:pPr><w:pStyle w:val=\"Title\"/></w:pPr><w:r><w:t xml:space=\"preserve\">" +
                    escapeXml(text) + "</w:t></w:r></w:p>",
                BODY_NAMESPACES
            ).firstOrNull()
            if (created != null) {
                val imported = document.importNode(created, true) as Element
                if (section != null) body.insertBefore(imported, section) else body.insertBefore(imported, body.firstChild)
            }
        }
        val bytes = parts[CORE_PART] ?: return
        val core = try {
            parse(bytes)
        } catch (e: Exception) {
            return
        }
        val title = children(core.documentElement, "title").firstOrNull()
        if (title == null) {
            appendXml(core, core.documentElement, "<dc:title>${escapeXml(text)}</dc:title>", "xmlns:dc=\"$NS_DUBLIN_CORE\"")
        } else {
            while (title.firstChild != null) title.removeChild(title.firstChild)
            title.appendChild(core.createTextNode(text))
        }
        parts[CORE_PART] = serialize(core)
    }

    private fun setHeaderFooter(
        parts: MutableMap<String, ByteArray>,
        rels: Document,
        document: Document,
        footer: Boolean,
        text: String,
        pageNumbers: Boolean
    ) {
        val body = children(document.documentElement, "body").firstOrNull() ?: return
        val section = ensureSection(document, body)
        val tag = if (footer) "footerReference" else "headerReference"
        val type = if (footer) REL_FOOTER else REL_HEADER
        val existing = children(section, tag).firstOrNull { referenceType(it) == "default" }
        var relId = if (existing == null) "" else attr(existing, "r:id")
        var partName = if (relId.isEmpty()) "" else relationshipPart(rels, relId)
        if (partName.isEmpty()) {
            partName = nextPartName(parts, if (footer) "word/footer" else "word/header")
            parts[partName] = headerFooterPart(footer, text, pageNumbers)
            addContentType(parts, "/$partName", if (footer) CONTENT_TYPE_FOOTER else CONTENT_TYPE_HEADER)
            if (relId.isEmpty()) relId = addRelationship(rels, type, partName.removePrefix("word/"), false)
            if (existing == null) addSectionReference(document, section, tag, relId)
            return
        }
        val bytes = parts[partName]
        if (bytes == null) {
            parts[partName] = headerFooterPart(footer, text, pageNumbers)
        } else {
            val partDocument = try {
                parse(bytes)
            } catch (e: Exception) {
                null
            }
            if (partDocument == null) {
                parts[partName] = headerFooterPart(footer, text, pageNumbers)
            } else {
                val paragraphs = children(partDocument.documentElement, "p")
                if (paragraphs.isEmpty()) {
                    appendXml(
                        partDocument,
                        partDocument.documentElement,
                        headerFooterParagraph(text, pageNumbers).render(),
                        BODY_NAMESPACES
                    )
                } else {
                    setParagraphText(partDocument, paragraphs.first(), text)
                    paragraphs.drop(1).forEach { partDocument.documentElement.removeChild(it) }
                }
                parts[partName] = serialize(partDocument)
            }
        }
        addContentType(parts, "/$partName", if (footer) CONTENT_TYPE_FOOTER else CONTENT_TYPE_HEADER)
    }

    private fun referenceType(reference: Element): String {
        val declared = attr(reference, "w:type")
        return if (declared.isEmpty()) "default" else declared
    }

    private fun ensureSection(document: Document, body: Element): Element {
        val existing = children(body, "sectPr").firstOrNull()
        if (existing != null) return existing
        val section = node("w:sectPr")
        section.child("w:pgSz").attr("w:w", 11906).attr("w:h", 16838)
        section.child("w:pgMar").attr("w:top", 1440).attr("w:right", 1440).attr("w:bottom", 1440).attr("w:left", 1440)
            .attr("w:header", 708).attr("w:footer", 708).attr("w:gutter", 0)
        section.child("w:cols").attr("w:space", 708)
        section.child("w:docGrid").attr("w:linePitch", 360)
        val element = appendXml(document, body, section.render(), BODY_NAMESPACES)
        return element ?: body
    }

    private fun addSectionReference(document: Document, section: Element, tag: String, relId: String) {
        val reference = document.createElement("w:$tag")
        reference.setAttribute("w:type", "default")
        reference.setAttribute("r:id", relId)
        val allowed = if (tag == "headerReference") setOf("headerReference") else setOf("headerReference", "footerReference")
        var anchor: Node? = null
        var child = section.firstChild
        while (child != null) {
            if (child is Element && localName(child) !in allowed) {
                anchor = child
                break
            }
            child = child.nextSibling
        }
        if (anchor != null) section.insertBefore(reference, anchor) else section.appendChild(reference)
    }

    private fun relationshipPart(rels: Document, relId: String): String {
        val rel = children(rels.documentElement, "Relationship").firstOrNull { attr(it, "Id") == relId } ?: return ""
        if (attr(rel, "TargetMode").equals("External", ignoreCase = true)) return ""
        return normalizePart(attr(rel, "Target"))
    }

    private fun addRelationship(rels: Document, type: String, target: String, external: Boolean): String {
        val existing = children(rels.documentElement, "Relationship").firstOrNull {
            attr(it, "Type") == type && attr(it, "Target") == target
        }
        if (existing != null) return attr(existing, "Id")
        val id = nextRelId(rels)
        val element = rels.createElement("Relationship")
        element.setAttribute("Id", id)
        element.setAttribute("Type", type)
        element.setAttribute("Target", target)
        if (external) element.setAttribute("TargetMode", "External")
        rels.documentElement.appendChild(element)
        return id
    }

    private fun nextRelId(rels: Document): String {
        val used = children(rels.documentElement, "Relationship").map { attr(it, "Id") }.toMutableSet()
        var index = used.size + 1
        while (used.contains("rId$index")) index++
        return "rId$index"
    }

    private fun nextPartName(parts: Map<String, ByteArray>, prefix: String): String {
        var index = 1
        while (parts.containsKey("$prefix$index.xml")) index++
        return "$prefix$index.xml"
    }

    private fun addContentType(parts: MutableMap<String, ByteArray>, partName: String, contentType: String) {
        val bytes = parts[CONTENT_TYPES_PART] ?: return
        val document = try {
            parse(bytes)
        } catch (e: Exception) {
            return
        }
        val exists = children(document.documentElement, "Override").any { attr(it, "PartName") == partName }
        if (exists) return
        appendXml(
            document,
            document.documentElement,
            "<Override PartName=\"${escapeXml(partName)}\" ContentType=\"${escapeXml(contentType)}\"/>",
            "xmlns=\"$NS_CONTENT_TYPES\""
        )
        parts[CONTENT_TYPES_PART] = serialize(document)
    }

    private fun ensureImageDefaults(parts: MutableMap<String, ByteArray>) {
        val bytes = parts[CONTENT_TYPES_PART] ?: return
        val document = try {
            parse(bytes)
        } catch (e: Exception) {
            return
        }
        val defaults = mapOf("png" to "image/png", "jpeg" to "image/jpeg", "jpg" to "image/jpeg", "gif" to "image/gif")
        var changed = false
        defaults.forEach { entry ->
            val exists = children(document.documentElement, "Default").any {
                attr(it, "Extension").equals(entry.key, ignoreCase = true)
            }
            if (!exists) {
                val created = document.createElement("Default")
                created.setAttribute("Extension", entry.key)
                created.setAttribute("ContentType", entry.value)
                document.documentElement.insertBefore(created, document.documentElement.firstChild)
                changed = true
            }
        }
        if (changed) parts[CONTENT_TYPES_PART] = serialize(document)
    }
}

private sealed class DocBlock {
    class Heading(val level: Int, val text: String) : DocBlock()
    class Paragraph(val text: String, val align: String, val style: JSONObject?) : DocBlock()
    class Listing(val items: List<String>, val ordered: Boolean) : DocBlock()
    class Table(val header: List<String>, val rows: List<List<String>>, val widths: List<Int>) : DocBlock()
    class Picture(val path: String, val width: Int, val caption: String) : DocBlock()
    class Quote(val text: String) : DocBlock()
    class Code(val text: String) : DocBlock()
    class Link(val text: String, val url: String) : DocBlock()
    object Break : DocBlock()
    object Rule : DocBlock()
}

private data class DocxRel(val id: String, val type: String, val target: String, val external: Boolean)

private interface DocxRelSink {
    fun relationship(type: String, target: String, external: Boolean): String
    fun media(bytes: ByteArray, extension: String): String
}

private class CreateSink : DocxRelSink {

    val rels = LinkedHashMap<String, DocxRel>()
    val media = LinkedHashMap<String, ByteArray>()

    private var imageCount = 0

    override fun relationship(type: String, target: String, external: Boolean): String {
        val existing = rels.values.firstOrNull { it.type == type && it.target == target && it.external == external }
        if (existing != null) return existing.id
        val id = "rId${rels.size + 1}"
        rels[id] = DocxRel(id, type, target, external)
        return id
    }

    override fun media(bytes: ByteArray, extension: String): String {
        imageCount++
        val name = "media/image$imageCount.$extension"
        media[name] = bytes
        return name
    }
}

private class EditSink(private val parts: MutableMap<String, ByteArray>, private val rels: Document) : DocxRelSink {

    var addedMedia = false

    override fun relationship(type: String, target: String, external: Boolean): String {
        val existing = children(rels.documentElement, "Relationship").firstOrNull {
            attr(it, "Type") == type && attr(it, "Target") == target
        }
        if (existing != null) return attr(existing, "Id")
        val used = children(rels.documentElement, "Relationship").map { attr(it, "Id") }.toMutableSet()
        var index = used.size + 1
        while (used.contains("rId$index")) index++
        val id = "rId$index"
        val element = rels.createElement("Relationship")
        element.setAttribute("Id", id)
        element.setAttribute("Type", type)
        element.setAttribute("Target", target)
        if (external) element.setAttribute("TargetMode", "External")
        rels.documentElement.appendChild(element)
        return id
    }

    override fun media(bytes: ByteArray, extension: String): String {
        var index = 1
        while (parts.containsKey("word/media/image$index.$extension")) index++
        val name = "media/image$index.$extension"
        parts["word/$name"] = bytes
        addedMedia = true
        return name
    }
}

private class DocBlocks(private val sink: DocxRelSink, private val base: File) {

    private var drawingId = 0

    fun render(blocks: List<DocBlock>): List<XmlBuilder> {
        val out = mutableListOf<XmlBuilder>()
        blocks.forEach { out.addAll(renderBlock(it)) }
        return out
    }

    fun title(text: String): XmlBuilder = styledParagraph("Title", text)

    fun tocHeading(text: String): XmlBuilder {
        val paragraph = node("w:p")
        paragraph.child("w:pPr").apply {
            child("w:pStyle").attr("w:val", "TOCHeading")
            child("w:outlineLvl").attr("w:val", 9)
        }
        paragraph.addAll(runs(text, null))
        return paragraph
    }

    fun tocField(): XmlBuilder {
        val paragraph = node("w:p")
        paragraph.child("w:r").child("w:fldChar").attr("w:fldCharType", "begin")
        paragraph.child("w:r").child("w:instrText").attr("xml:space", "preserve")
            .text(" TOC \\o \"1-3\" \\h \\z \\u ")
        paragraph.child("w:r").child("w:fldChar").attr("w:fldCharType", "separate")
        paragraph.child("w:r").child("w:t").attr("xml:space", "preserve")
            .text("Right-click and choose Update Field to build the table of contents.")
        paragraph.child("w:r").child("w:fldChar").attr("w:fldCharType", "end")
        return paragraph
    }

    fun section(headerId: String, footerId: String): XmlBuilder {
        val section = node("w:sectPr")
        if (headerId.isNotEmpty()) {
            section.child("w:headerReference").attr("w:type", "default").attr("r:id", headerId)
        }
        if (footerId.isNotEmpty()) {
            section.child("w:footerReference").attr("w:type", "default").attr("r:id", footerId)
        }
        section.child("w:pgSz").attr("w:w", 11906).attr("w:h", 16838)
        section.child("w:pgMar").attr("w:top", 1440).attr("w:right", 1440).attr("w:bottom", 1440)
            .attr("w:left", 1440).attr("w:header", 708).attr("w:footer", 708).attr("w:gutter", 0)
        section.child("w:cols").attr("w:space", 708)
        section.child("w:docGrid").attr("w:linePitch", 360)
        return section
    }

    fun image(path: String, width: Int, caption: String): List<XmlBuilder> {
        if (path.isBlank()) throw IllegalArgumentException("The image block needs a \"path\"")
        val file = resolve(path)
        if (!file.exists()) throw IllegalArgumentException("Image ${file.name} was not found")
        val bytes = try {
            file.readBytes()
        } catch (e: Exception) {
            throw IllegalArgumentException("Cannot read image ${file.name}: ${e.message ?: "read error"}")
        }
        val kind = imageKind(bytes)
            ?: throw IllegalArgumentException("${file.name} is not a supported image (use png, jpeg or gif)")
        val target = sink.media(bytes, kind.first)
        val relId = sink.relationship(REL_IMAGE, target, false)
        val size = imageSize(bytes)
        val cx = width.coerceIn(16, 2400) * 12700
        val cy = if (size != null && size.first > 0) {
            (cx.toLong() * size.second.toLong() / size.first.toLong()).toInt().coerceAtLeast(12700)
        } else {
            cx * 3 / 4
        }
        drawingId++
        val id = drawingId
        val drawing = node("w:drawing")
        val inline = node("wp:inline").attr("distT", 0).attr("distB", 0).attr("distL", 0).attr("distR", 0)
        inline.child("wp:extent").attr("cx", cx).attr("cy", cy)
        inline.child("wp:effectExtent").attr("l", 0).attr("t", 0).attr("r", 0).attr("b", 0)
        inline.child("wp:docPr").attr("id", id).attr("name", "Picture $id")
        inline.child("wp:cNvGraphicFramePr").child("a:graphicFrameLocks").attr("noChangeAspect", 1)
        val graphic = node("a:graphic")
        val data = node("a:graphicData").attr("uri", NS_PICTURE)
        val picture = node("pic:pic")
        picture.child("pic:nvPicPr").apply {
            child("pic:cNvPr").attr("id", 0).attr("name", "Picture $id")
            child("pic:cNvPicPr")
        }
        picture.child("pic:blipFill").apply {
            child("a:blip").attr("r:embed", relId)
            child("a:stretch").child("a:fillRect")
        }
        picture.child("pic:spPr").apply {
            child("a:xfrm").apply {
                child("a:off").attr("x", 0).attr("y", 0)
                child("a:ext").attr("cx", cx).attr("cy", cy)
            }
            child("a:prstGeom").attr("prst", "rect").child("a:avLst")
        }
        data.add(picture)
        graphic.add(data)
        inline.add(graphic)
        drawing.add(inline)
        val paragraph = node("w:p")
        paragraph.child("w:pPr").child("w:jc").attr("w:val", "center")
        paragraph.child("w:r").add(drawing)
        val out = mutableListOf(paragraph)
        if (caption.isNotBlank()) out.add(captionParagraph(caption, id))
        return out
    }

    private fun renderBlock(block: DocBlock): List<XmlBuilder> = when (block) {
        is DocBlock.Heading -> listOf(heading(block.level, block.text))
        is DocBlock.Paragraph -> listOf(paragraph(block.text, block.align, block.style))
        is DocBlock.Listing -> block.items.map { listItem(it, if (block.ordered) 2 else 1) }
        is DocBlock.Table -> table(block)
        is DocBlock.Picture -> image(block.path, block.width, block.caption)
        is DocBlock.Quote -> listOf(paragraph(block.text, "", null, "Quote"))
        is DocBlock.Code -> listOf(codeParagraph(block.text))
        is DocBlock.Link -> listOf(linkParagraph(block.text, block.url))
        DocBlock.Break -> listOf(pageBreak())
        DocBlock.Rule -> listOf(rule())
    }

    private fun heading(level: Int, text: String): XmlBuilder {
        val safe = level.coerceIn(1, 4)
        val paragraph = node("w:p")
        paragraph.child("w:pPr").apply {
            child("w:pStyle").attr("w:val", "Heading$safe")
            child("w:outlineLvl").attr("w:val", safe - 1)
        }
        paragraph.addAll(runs(text, null))
        return paragraph
    }

    private fun styledParagraph(styleId: String, text: String): XmlBuilder {
        val paragraph = node("w:p")
        paragraph.child("w:pPr").child("w:pStyle").attr("w:val", styleId)
        paragraph.addAll(runs(text, null))
        return paragraph
    }

    private fun captionParagraph(text: String, id: Int): XmlBuilder {
        val paragraph = node("w:p")
        paragraph.child("w:pPr").child("w:pStyle").attr("w:val", "Caption")
        paragraph.addAll(runs("Figure $id: $text", null))
        return paragraph
    }

    private fun paragraph(text: String, align: String, style: JSONObject?, styleId: String = ""): XmlBuilder {
        val paragraph = node("w:p")
        val props = node("w:pPr")
        var used = false
        if (styleId.isNotEmpty()) {
            props.child("w:pStyle").attr("w:val", styleId)
            used = true
        }
        val spacing = style?.optDouble("spacing", 0.0) ?: 0.0
        if (spacing > 0.0) {
            props.child("w:spacing").attr("w:line", (spacing * 240.0).toInt()).attr("w:lineRule", "auto")
            used = true
        }
        if (align.isNotEmpty()) {
            props.child("w:jc").attr("w:val", if (align == "justify") "both" else align)
            used = true
        }
        if (used) paragraph.add(props)
        paragraph.addAll(runs(text, style))
        return paragraph
    }

    private fun listItem(text: String, numId: Int): XmlBuilder {
        val paragraph = node("w:p")
        paragraph.child("w:pPr").apply {
            child("w:pStyle").attr("w:val", "ListParagraph")
            child("w:numPr").apply {
                child("w:ilvl").attr("w:val", 0)
                child("w:numId").attr("w:val", numId)
            }
        }
        paragraph.addAll(runs(text, null))
        return paragraph
    }

    private fun codeParagraph(text: String): XmlBuilder {
        val paragraph = node("w:p")
        paragraph.child("w:pPr").child("w:pStyle").attr("w:val", "Code")
        val run = node("w:r")
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n")
        lines.forEachIndexed { index, line ->
            if (index > 0) run.child("w:br")
            run.child("w:t").attr("xml:space", "preserve").text(line)
        }
        paragraph.add(run)
        return paragraph
    }

    private fun linkParagraph(text: String, url: String): XmlBuilder {
        if (url.isBlank()) throw IllegalArgumentException("The link block needs a \"url\"")
        val paragraph = node("w:p")
        val link = node("w:hyperlink").attr("r:id", sink.relationship(REL_HYPERLINK, url, true))
        link.add(hyperlinkRun(text, null))
        paragraph.add(link)
        return paragraph
    }

    private fun pageBreak(): XmlBuilder {
        val paragraph = node("w:p")
        paragraph.child("w:r").child("w:br").attr("w:type", "page")
        return paragraph
    }

    private fun rule(): XmlBuilder {
        val paragraph = node("w:p")
        paragraph.child("w:pPr").child("w:pBdr").child("w:bottom")
            .attr("w:val", "single").attr("w:sz", 6).attr("w:space", 1).attr("w:color", "auto")
        return paragraph
    }

    private fun table(block: DocBlock.Table): List<XmlBuilder> {
        val columns = maxOf(block.header.size, block.rows.maxOfOrNull { it.size } ?: 0).coerceAtLeast(1)
        val widths = (0 until columns).map { index ->
            val declared = block.widths.getOrNull(index) ?: 0
            if (declared > 0) declared else 9000 / columns
        }
        val table = node("w:tbl")
        table.child("w:tblPr").apply {
            child("w:tblStyle").attr("w:val", "TableGrid")
            child("w:tblW").attr("w:w", 0).attr("w:type", "auto")
            child("w:tblLook").attr("w:val", "04A0").attr("w:firstRow", 1).attr("w:lastRow", 0)
                .attr("w:firstColumn", 1).attr("w:lastColumn", 0).attr("w:noHBand", 0).attr("w:noVBand", 1)
        }
        table.child("w:tblGrid").apply { widths.forEach { width -> child("w:gridCol").attr("w:w", width) } }
        if (block.header.isNotEmpty()) table.add(tableRow(block.header, widths, true))
        block.rows.forEach { row -> table.add(tableRow(row, widths, false)) }
        return listOf(table, node("w:p"))
    }

    private fun tableRow(cells: List<String>, widths: List<Int>, header: Boolean): XmlBuilder {
        val row = node("w:tr")
        val props = node("w:trPr")
        if (header) props.child("w:tblHeader")
        row.add(props)
        widths.indices.forEach { index ->
            val cell = node("w:tc")
            cell.child("w:tcPr").child("w:tcW").attr("w:w", widths[index]).attr("w:type", "dxa")
            val text = cells.getOrNull(index) ?: ""
            val paragraph = node("w:p")
            if (text.isEmpty()) {
                paragraph.add(simpleRun(""))
            } else {
                val base = if (header) headerStyle() else null
                paragraph.addAll(runs(text, base))
            }
            cell.add(paragraph)
            row.add(cell)
        }
        return row
    }

    private fun headerStyle(): JSONObject = JSONObject().put("bold", true)

    private fun runs(text: String, base: JSONObject?): List<XmlBuilder> {
        val spans = SimpleMarkdown.spans(text)
        if (spans.isEmpty()) return emptyList()
        return spans.map { span ->
            if (span.url.isNotEmpty()) {
                val link = node("w:hyperlink").attr("r:id", sink.relationship(REL_HYPERLINK, span.url, true))
                link.add(hyperlinkRun(span.text, base))
                link
            } else {
                run(span.text, base, span.bold, span.italic, span.code)
            }
        }
    }

    private fun hyperlinkRun(text: String, base: JSONObject?): XmlBuilder {
        val run = node("w:r")
        val props = node("w:rPr")
        props.child("w:rStyle").attr("w:val", "Hyperlink")
        applyRunProperties(props, base, false, false)
        run.add(props)
        run.child("w:t").attr("xml:space", "preserve").text(text)
        return run
    }

    private fun run(text: String, base: JSONObject?, bold: Boolean, italic: Boolean, code: Boolean): XmlBuilder {
        val run = node("w:r")
        val props = node("w:rPr")
        var used = false
        if (code) {
            props.child("w:rStyle").attr("w:val", "CodeChar")
            used = true
        }
        if (applyRunProperties(props, base, bold, italic)) used = true
        if (used) run.add(props)
        run.child("w:t").attr("xml:space", "preserve").text(text)
        return run
    }

    private fun applyRunProperties(props: XmlBuilder, base: JSONObject?, bold: Boolean, italic: Boolean): Boolean {
        var used = false
        val font = stringOf(base, "font")
        if (font.isNotBlank()) {
            props.child("w:rFonts").attr("w:ascii", font).attr("w:hAnsi", font).attr("w:cs", font)
            used = true
        }
        val strong = bold || (base?.optBoolean("bold", false) ?: false)
        val emphasis = italic || (base?.optBoolean("italic", false) ?: false)
        if (strong) {
            props.child("w:b")
            props.child("w:bCs")
            used = true
        }
        if (emphasis) {
            props.child("w:i")
            props.child("w:iCs")
            used = true
        }
        if (base?.optBoolean("strike", false) == true) {
            props.child("w:strike")
            used = true
        }
        val colour = colourValue(stringOf(base, "colour").ifEmpty { stringOf(base, "color") })
        if (colour.isNotEmpty()) {
            props.child("w:color").attr("w:val", colour)
            used = true
        }
        val size = base?.optDouble("size", 0.0) ?: 0.0
        if (size > 0.0) {
            val half = (size * 2).toInt().coerceIn(2, 400)
            props.child("w:sz").attr("w:val", half)
            props.child("w:szCs").attr("w:val", half)
            used = true
        }
        val highlight = stringOf(base, "highlight")
        if (highlight.isNotBlank()) {
            props.child("w:highlight").attr("w:val", highlightName(highlight))
            used = true
        }
        if (base?.optBoolean("underline", false) == true) {
            props.child("w:u").attr("w:val", "single")
            used = true
        }
        return used
    }

    private fun highlightName(value: String): String {
        val clean = value.trim().lowercase()
        return if (clean in HIGHLIGHTS) clean else "yellow"
    }

    private fun colourValue(value: String): String {
        val clean = value.trim().removePrefix("#").uppercase()
        if (clean.length != 6) return ""
        if (!clean.all { it in "0123456789ABCDEF" }) return ""
        return clean
    }

    private fun resolve(path: String): File {
        val file = File(path)
        return if (file.isAbsolute) file else File(base, path)
    }
}

private val HIGHLIGHTS = setOf(
    "black", "blue", "cyan", "green", "magenta", "red", "yellow", "white",
    "darkblue", "darkcyan", "darkgreen", "darkmagenta", "darkred", "darkyellow",
    "darkgray", "lightgray", "none"
)

private fun imageKind(bytes: ByteArray): Pair<String, String>? {
    if (bytes.size < 12) return null
    val png = bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte()
    if (png) return "png" to "image/png"
    if (bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte()) return "gif" to "image/gif"
    if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) return "jpeg" to "image/jpeg"
    return null
}



private fun imageSize(bytes: ByteArray): Pair<Int, Int>? {
    if (bytes.size < 24) return null
    if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte()) {
        val width = readInt(bytes, 16)
        val height = readInt(bytes, 20)
        if (width > 0 && height > 0) return width to height
        return null
    }
    if (bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte()) {
        val width = (bytes[6].toInt() and 0xFF) or ((bytes[7].toInt() and 0xFF) shl 8)
        val height = (bytes[8].toInt() and 0xFF) or ((bytes[9].toInt() and 0xFF) shl 8)
        if (width > 0 && height > 0) return width to height
        return null
    }
    if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) return jpegSize(bytes)
    return null
}

private fun readInt(bytes: ByteArray, offset: Int): Int {
    if (offset + 4 > bytes.size) return 0
    return ((bytes[offset].toInt() and 0xFF) shl 24) or ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
        ((bytes[offset + 2].toInt() and 0xFF) shl 8) or (bytes[offset + 3].toInt() and 0xFF)
}

private fun jpegSize(bytes: ByteArray): Pair<Int, Int>? {
    var index = 2
    while (index + 9 < bytes.size) {
        if (bytes[index].toInt() and 0xFF != 0xFF) {
            index++
            continue
        }
        val marker = bytes[index + 1].toInt() and 0xFF
        if (marker == 0xD8 || marker == 0x01 || (marker in 0xD0..0xD7)) {
            index += 2
            continue
        }
        val length = ((bytes[index + 2].toInt() and 0xFF) shl 8) or (bytes[index + 3].toInt() and 0xFF)
        if (length < 2) return null
        val frame = marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC
        if (frame) {
            val height = ((bytes[index + 5].toInt() and 0xFF) shl 8) or (bytes[index + 6].toInt() and 0xFF)
            val width = ((bytes[index + 7].toInt() and 0xFF) shl 8) or (bytes[index + 8].toInt() and 0xFF)
            if (width > 0 && height > 0) return width to height
            return null
        }
        index += 2 + length
    }
    return null
}

private const val APP_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties" xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes">
    <Application>Lucent</Application>
    <DocSecurity>0</DocSecurity>
    <ScaleCrop>false</ScaleCrop>
    <Company></Company>
    <LinksUpToDate>false</LinksUpToDate>
    <SharedDoc>false</SharedDoc>
    <HyperlinksChanged>false</HyperlinksChanged>
    <AppVersion>16.0000</AppVersion>
</Properties>
"""

private const val SETTINGS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:settings xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
    <w:zoom w:percent="100"/>
    <w:defaultTabStop w:val="708"/>
    <w:characterSpacingControl w:val="doNotCompress"/>
    <w:updateFields w:val="true"/>
    <w:compat>
        <w:compatSetting w:name="compatibilityMode" w:uri="http://schemas.microsoft.com/office/word" w:val="15"/>
    </w:compat>
</w:settings>
"""

private const val NUMBERING_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:numbering xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
    <w:abstractNum w:abstractNumId="0">
        <w:multiLevelType w:val="hybridMultilevel"/>
        <w:lvl w:ilvl="0">
            <w:start w:val="1"/>
            <w:numFmt w:val="bullet"/>
            <w:lvlText w:val="&#8226;"/>
            <w:lvlJc w:val="left"/>
            <w:pPr><w:ind w:left="720" w:hanging="360"/></w:pPr>
        </w:lvl>
        <w:lvl w:ilvl="1">
            <w:start w:val="1"/>
            <w:numFmt w:val="bullet"/>
            <w:lvlText w:val="o"/>
            <w:lvlJc w:val="left"/>
            <w:pPr><w:ind w:left="1440" w:hanging="360"/></w:pPr>
        </w:lvl>
        <w:lvl w:ilvl="2">
            <w:start w:val="1"/>
            <w:numFmt w:val="bullet"/>
            <w:lvlText w:val="&#9642;"/>
            <w:lvlJc w:val="left"/>
            <w:pPr><w:ind w:left="2160" w:hanging="360"/></w:pPr>
        </w:lvl>
    </w:abstractNum>
    <w:abstractNum w:abstractNumId="1">
        <w:multiLevelType w:val="hybridMultilevel"/>
        <w:lvl w:ilvl="0">
            <w:start w:val="1"/>
            <w:numFmt w:val="decimal"/>
            <w:lvlText w:val="%1."/>
            <w:lvlJc w:val="left"/>
            <w:pPr><w:ind w:left="720" w:hanging="360"/></w:pPr>
        </w:lvl>
        <w:lvl w:ilvl="1">
            <w:start w:val="1"/>
            <w:numFmt w:val="lowerLetter"/>
            <w:lvlText w:val="%2."/>
            <w:lvlJc w:val="left"/>
            <w:pPr><w:ind w:left="1440" w:hanging="360"/></w:pPr>
        </w:lvl>
        <w:lvl w:ilvl="2">
            <w:start w:val="1"/>
            <w:numFmt w:val="lowerRoman"/>
            <w:lvlText w:val="%3."/>
            <w:lvlJc w:val="left"/>
            <w:pPr><w:ind w:left="2160" w:hanging="360"/></w:pPr>
        </w:lvl>
    </w:abstractNum>
    <w:num w:numId="1"><w:abstractNumId w:val="0"/></w:num>
    <w:num w:numId="2"><w:abstractNumId w:val="1"/></w:num>
</w:numbering>
"""

private const val STYLES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
    <w:docDefaults>
        <w:rPrDefault>
            <w:rPr>
                <w:rFonts w:ascii="Calibri" w:hAnsi="Calibri" w:cs="Calibri"/>
                <w:sz w:val="22"/>
                <w:szCs w:val="22"/>
                <w:lang w:val="en-US" w:eastAsia="en-US" w:bidi="ar-SA"/>
            </w:rPr>
        </w:rPrDefault>
        <w:pPrDefault>
            <w:pPr><w:spacing w:after="160" w:line="259" w:lineRule="auto"/></w:pPr>
        </w:pPrDefault>
    </w:docDefaults>
    <w:style w:type="paragraph" w:default="1" w:styleId="Normal">
        <w:name w:val="Normal"/>
        <w:qFormat/>
    </w:style>
    <w:style w:type="character" w:default="1" w:styleId="DefaultParagraphFont">
        <w:name w:val="Default Paragraph Font"/>
        <w:uiPriority w:val="1"/>
        <w:semiHidden/>
        <w:unhideWhenUsed/>
    </w:style>
    <w:style w:type="table" w:default="1" w:styleId="TableNormal">
        <w:name w:val="Normal Table"/>
        <w:uiPriority w:val="99"/>
        <w:semiHidden/>
        <w:unhideWhenUsed/>
        <w:tblPr>
            <w:tblInd w:w="0" w:type="dxa"/>
            <w:tblCellMar>
                <w:top w:w="0" w:type="dxa"/>
                <w:left w:w="108" w:type="dxa"/>
                <w:bottom w:w="0" w:type="dxa"/>
                <w:right w:w="108" w:type="dxa"/>
            </w:tblCellMar>
        </w:tblPr>
    </w:style>
    <w:style w:type="paragraph" w:styleId="Title">
        <w:name w:val="Title"/>
        <w:basedOn w:val="Normal"/>
        <w:next w:val="Normal"/>
        <w:qFormat/>
        <w:pPr><w:spacing w:after="300" w:line="240" w:lineRule="auto"/><w:contextualSpacing/></w:pPr>
        <w:rPr>
            <w:rFonts w:ascii="Calibri Light" w:hAnsi="Calibri Light" w:cs="Calibri Light"/>
            <w:color w:val="17365D"/>
            <w:sz w:val="56"/>
            <w:szCs w:val="56"/>
        </w:rPr>
    </w:style>
    <w:style w:type="paragraph" w:styleId="Heading1">
        <w:name w:val="heading 1"/>
        <w:basedOn w:val="Normal"/>
        <w:next w:val="Normal"/>
        <w:qFormat/>
        <w:pPr>
            <w:keepNext/>
            <w:spacing w:before="360" w:after="120"/>
            <w:outlineLvl w:val="0"/>
        </w:pPr>
        <w:rPr>
            <w:b/>
            <w:color w:val="1F3864"/>
            <w:sz w:val="32"/>
            <w:szCs w:val="32"/>
        </w:rPr>
    </w:style>
    <w:style w:type="paragraph" w:styleId="Heading2">
        <w:name w:val="heading 2"/>
        <w:basedOn w:val="Normal"/>
        <w:next w:val="Normal"/>
        <w:qFormat/>
        <w:pPr>
            <w:keepNext/>
            <w:spacing w:before="280" w:after="100"/>
            <w:outlineLvl w:val="1"/>
        </w:pPr>
        <w:rPr>
            <w:b/>
            <w:color w:val="2E5496"/>
            <w:sz w:val="28"/>
            <w:szCs w:val="28"/>
        </w:rPr>
    </w:style>
    <w:style w:type="paragraph" w:styleId="Heading3">
        <w:name w:val="heading 3"/>
        <w:basedOn w:val="Normal"/>
        <w:next w:val="Normal"/>
        <w:qFormat/>
        <w:pPr>
            <w:keepNext/>
            <w:spacing w:before="240" w:after="80"/>
            <w:outlineLvl w:val="2"/>
        </w:pPr>
        <w:rPr>
            <w:b/>
            <w:color w:val="2E5496"/>
            <w:sz w:val="26"/>
            <w:szCs w:val="26"/>
        </w:rPr>
    </w:style>
    <w:style w:type="paragraph" w:styleId="Heading4">
        <w:name w:val="heading 4"/>
        <w:basedOn w:val="Normal"/>
        <w:next w:val="Normal"/>
        <w:qFormat/>
        <w:pPr>
            <w:keepNext/>
            <w:spacing w:before="200" w:after="80"/>
            <w:outlineLvl w:val="3"/>
        </w:pPr>
        <w:rPr>
            <w:b/>
            <w:i/>
            <w:color w:val="2E5496"/>
            <w:sz w:val="24"/>
            <w:szCs w:val="24"/>
        </w:rPr>
    </w:style>
    <w:style w:type="paragraph" w:styleId="ListParagraph">
        <w:name w:val="List Paragraph"/>
        <w:basedOn w:val="Normal"/>
        <w:qFormat/>
        <w:pPr><w:ind w:left="720"/><w:contextualSpacing/></w:pPr>
    </w:style>
    <w:style w:type="paragraph" w:styleId="Quote">
        <w:name w:val="Quote"/>
        <w:basedOn w:val="Normal"/>
        <w:next w:val="Normal"/>
        <w:qFormat/>
        <w:pPr>
            <w:pBdr><w:left w:val="single" w:sz="18" w:space="10" w:color="BFBFBF"/></w:pBdr>
            <w:spacing w:before="120" w:after="120"/>
            <w:ind w:left="360"/>
        </w:pPr>
        <w:rPr>
            <w:i/>
            <w:color w:val="595959"/>
        </w:rPr>
    </w:style>
    <w:style w:type="paragraph" w:styleId="Code">
        <w:name w:val="Code"/>
        <w:basedOn w:val="Normal"/>
        <w:next w:val="Normal"/>
        <w:qFormat/>
        <w:pPr>
            <w:shd w:val="clear" w:color="auto" w:fill="F2F2F2"/>
            <w:spacing w:before="0" w:after="0" w:line="240" w:lineRule="auto"/>
        </w:pPr>
        <w:rPr>
            <w:rFonts w:ascii="Consolas" w:hAnsi="Consolas" w:cs="Consolas"/>
            <w:sz w:val="20"/>
            <w:szCs w:val="20"/>
        </w:rPr>
    </w:style>
    <w:style w:type="paragraph" w:styleId="Caption">
        <w:name w:val="caption"/>
        <w:basedOn w:val="Normal"/>
        <w:next w:val="Normal"/>
        <w:uiPriority w:val="35"/>
        <w:qFormat/>
        <w:pPr><w:spacing w:after="200"/></w:pPr>
        <w:rPr>
            <w:i/>
            <w:color w:val="595959"/>
            <w:sz w:val="18"/>
            <w:szCs w:val="18"/>
        </w:rPr>
    </w:style>
    <w:style w:type="paragraph" w:styleId="TOCHeading">
        <w:name w:val="TOC Heading"/>
        <w:basedOn w:val="Heading1"/>
        <w:next w:val="Normal"/>
        <w:uiPriority w:val="39"/>
        <w:unhideWhenUsed/>
        <w:pPr><w:outlineLvl w:val="9"/></w:pPr>
    </w:style>
    <w:style w:type="character" w:styleId="Hyperlink">
        <w:name w:val="Hyperlink"/>
        <w:basedOn w:val="DefaultParagraphFont"/>
        <w:uiPriority w:val="99"/>
        <w:unhideWhenUsed/>
        <w:rPr>
            <w:color w:val="0563C1"/>
            <w:u w:val="single"/>
        </w:rPr>
    </w:style>
    <w:style w:type="character" w:styleId="CodeChar">
        <w:name w:val="Code Char"/>
        <w:basedOn w:val="DefaultParagraphFont"/>
        <w:uiPriority w:val="99"/>
        <w:unhideWhenUsed/>
        <w:rPr>
            <w:rFonts w:ascii="Consolas" w:hAnsi="Consolas" w:cs="Consolas"/>
            <w:shd w:val="clear" w:color="auto" w:fill="F2F2F2"/>
        </w:rPr>
    </w:style>
    <w:style w:type="table" w:styleId="TableGrid">
        <w:name w:val="Table Grid"/>
        <w:basedOn w:val="TableNormal"/>
        <w:uiPriority w:val="39"/>
        <w:pPr><w:spacing w:after="0" w:line="240" w:lineRule="auto"/></w:pPr>
        <w:tblPr>
            <w:tblBorders>
                <w:top w:val="single" w:sz="4" w:space="0" w:color="auto"/>
                <w:left w:val="single" w:sz="4" w:space="0" w:color="auto"/>
                <w:bottom w:val="single" w:sz="4" w:space="0" w:color="auto"/>
                <w:right w:val="single" w:sz="4" w:space="0" w:color="auto"/>
                <w:insideH w:val="single" w:sz="4" w:space="0" w:color="auto"/>
                <w:insideV w:val="single" w:sz="4" w:space="0" w:color="auto"/>
            </w:tblBorders>
        </w:tblPr>
    </w:style>
</w:styles>
"""
