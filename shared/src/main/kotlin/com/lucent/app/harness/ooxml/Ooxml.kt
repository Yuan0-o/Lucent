package com.lucent.app.harness.ooxml

import org.json.JSONObject
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.ByteArrayInputStream
import java.io.File
import java.io.StringReader
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory

const val CONTENT_TYPES_PART = "[Content_Types].xml"

const val NS_CONTENT_TYPES = "http://schemas.openxmlformats.org/package/2006/content-types"
const val NS_PACKAGE_RELATIONSHIPS = "http://schemas.openxmlformats.org/package/2006/relationships"
const val NS_OFFICE_RELATIONSHIPS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
const val NS_WORDPROCESSING = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
const val NS_WORD_DRAWING = "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
const val NS_DRAWING = "http://schemas.openxmlformats.org/drawingml/2006/main"
const val NS_PICTURE = "http://schemas.openxmlformats.org/drawingml/2006/picture"
const val NS_MARKUP_COMPATIBILITY = "http://schemas.openxmlformats.org/markup-compatibility/2006"
const val NS_SPREADSHEET = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
const val NS_SPREADSHEET_DRAWING = "http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing"
const val NS_CHART = "http://schemas.openxmlformats.org/drawingml/2006/chart"
const val NS_CORE_PROPERTIES = "http://schemas.openxmlformats.org/package/2006/metadata/core-properties"
const val NS_DUBLIN_CORE = "http://purl.org/dc/elements/1.1/"
const val NS_DCTERMS = "http://purl.org/dc/terms/"
const val NS_XSI = "http://www.w3.org/2001/XMLSchema-instance"
const val NS_EXTENDED_PROPERTIES = "http://schemas.openxmlformats.org/officeDocument/2006/extended-properties"

const val REL_OFFICE_DOCUMENT = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"
const val REL_CORE_PROPERTIES = "http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties"
const val REL_EXTENDED_PROPERTIES = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties"

const val XML_DECLARATION = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"""

private const val ZIP_TIME = 1704067200000L

fun escapeXml(value: String): String {
    val out = StringBuilder(value.length + 16)
    value.forEach { ch ->
        when (ch) {
            '&' -> out.append("&amp;")
            '<' -> out.append("&lt;")
            '>' -> out.append("&gt;")
            '"' -> out.append("&quot;")
            '\'' -> out.append("&apos;")
            else -> if (ch.code < 0x20 && ch != '\t' && ch != '\n' && ch != '\r') out.append(' ') else out.append(ch)
        }
    }
    return out.toString()
}

fun unescapeXml(value: String): String {
    if ('&' !in value) return value
    return value
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")
}

fun utcStamp(): String {
    val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
    format.timeZone = java.util.TimeZone.getTimeZone("UTC")
    return format.format(java.util.Date())
}

class XmlBuilder(val name: String) {

    private val attributes = LinkedHashMap<String, String>()
    private val children = mutableListOf<XmlBuilder>()
    private var value = ""

    fun attr(name: String, value: String): XmlBuilder {
        attributes[name] = value
        return this
    }

    fun attr(name: String, value: Int): XmlBuilder = attr(name, value.toString())

    fun attrIf(condition: Boolean, name: String, value: String): XmlBuilder =
        if (condition) attr(name, value) else this

    fun text(value: String): XmlBuilder {
        this.value = value
        return this
    }

    fun child(name: String): XmlBuilder {
        val created = XmlBuilder(name)
        children.add(created)
        return created
    }

    fun add(child: XmlBuilder): XmlBuilder {
        children.add(child)
        return this
    }

    fun addAll(nodes: List<XmlBuilder>): XmlBuilder {
        children.addAll(nodes)
        return this
    }

    fun render(): String {
        val out = StringBuilder()
        renderTo(out)
        return out.toString()
    }

    private fun renderTo(out: StringBuilder) {
        out.append('<').append(name)
        attributes.forEach { entry ->
            out.append(' ').append(entry.key).append("=\"").append(escapeXml(entry.value)).append('"')
        }
        if (children.isEmpty() && value.isEmpty()) {
            out.append("/>")
            return
        }
        out.append('>')
        if (value.isNotEmpty()) out.append(escapeXml(value))
        children.forEach { it.renderTo(out) }
        out.append("</").append(name).append('>')
    }
}

fun node(name: String): XmlBuilder = XmlBuilder(name)

fun xml(name: String, block: XmlBuilder.() -> Unit): XmlBuilder = XmlBuilder(name).apply(block)

fun documentText(root: XmlBuilder): String = XML_DECLARATION + "\n" + root.render() + "\n"

fun documentBytes(root: XmlBuilder): ByteArray = documentText(root).toByteArray(Charsets.UTF_8)

private fun storedEntry(name: String): Boolean {
    val lower = name.lowercase()
    return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
        lower.endsWith(".gif") || lower.endsWith(".bmp") || lower.endsWith(".webp")
}

fun writZip(out: File, entries: List<Pair<String, ByteArray>>) {
    out.parentFile?.mkdirs()
    out.writeBytes(buildZip(entries))
}

private fun buildZip(entries: List<Pair<String, ByteArray>>): ByteArray {
    val head = entries.firstOrNull { it.first == CONTENT_TYPES_PART }
    val ordered = if (head == null) entries else listOf(head) + entries.filter { it.first != CONTENT_TYPES_PART }
    val buffer = java.io.ByteArrayOutputStream()
    ZipOutputStream(buffer).use { zip ->
        ordered.forEach { entry ->
            val item = ZipEntry(entry.first)
            item.time = ZIP_TIME
            if (storedEntry(entry.first)) {
                val crc = CRC32()
                crc.update(entry.second)
                item.method = ZipEntry.STORED
                item.size = entry.second.size.toLong()
                item.compressedSize = entry.second.size.toLong()
                item.crc = crc.value
            } else {
                item.method = ZipEntry.DEFLATED
            }
            zip.putNextEntry(item)
            zip.write(entry.second)
            zip.closeEntry()
        }
    }
    return buffer.toByteArray()
}

fun readZip(file: File): Map<String, ByteArray> = openZip(file)

private fun openZip(file: File): Map<String, ByteArray> {
    if (!file.exists()) throw IllegalArgumentException("${file.name} does not exist")
    val out = LinkedHashMap<String, ByteArray>()
    try {
        ZipFile(file).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) continue
                val name = entry.name.replace('\\', '/')
                out[name] = zip.getInputStream(entry).use { it.readBytes() }
            }
        }
    } catch (e: Exception) {
        throw IllegalArgumentException("${file.name} is not a readable Office package: ${e.message ?: "zip error"}")
    }
    return out
}

fun readEntry(file: File, name: String): ByteArray? = openEntry(file, name)

private fun openEntry(file: File, name: String): ByteArray? {
    if (!file.exists()) return null
    return try {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry(name)
            if (entry == null) null else zip.getInputStream(entry).use { it.readBytes() }
        }
    } catch (e: Exception) {
        null
    }
}

fun parse(bytes: ByteArray): Document = parseXml(bytes)

private fun parseXml(bytes: ByteArray): Document {
    val factory = DocumentBuilderFactory.newInstance()
    factory.isNamespaceAware = true
    factory.isCoalescing = true
    factory.setExpandEntityReferences(false)
    try {
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    } catch (e: Exception) {
    }
    try {
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
    } catch (e: Exception) {
    }
    try {
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    } catch (e: Exception) {
    }
    try {
        factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "")
    } catch (e: Exception) {
    }
    try {
        factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
    } catch (e: Exception) {
    }
    val builder = factory.newDocumentBuilder()
    builder.setEntityResolver { _, _ -> InputSource(StringReader("")) }
    builder.setErrorHandler(null)
    return builder.parse(ByteArrayInputStream(bytes))
}

fun parse(text: String): Document = parseXml(text.toByteArray(Charsets.UTF_8))

fun localName(node: Node): String {
    val local = node.localName
    if (local != null && local.isNotEmpty()) return local.substringAfterLast(':')
    val name = node.nodeName ?: ""
    return name.substringAfterLast(':')
}

fun attr(node: Node?, name: String): String = attributeOf(node, name)

private fun attributeOf(node: Node?, name: String): String {
    val element = node as? Element ?: return ""
    val wanted = name.substringAfterLast(':')
    val attributes = element.attributes ?: return ""
    for (index in 0 until attributes.length) {
        val item = attributes.item(index) ?: continue
        val qualified = item.nodeName ?: ""
        if (qualified == name || qualified.substringAfterLast(':') == wanted) {
            return item.nodeValue ?: ""
        }
    }
    return ""
}

fun children(node: Node?, tag: String): List<Element> = elementChildren(node, tag)

private fun elementChildren(node: Node?, tag: String): List<Element> {
    if (node == null) return emptyList()
    val wanted = tag.substringAfterLast(':')
    val out = mutableListOf<Element>()
    var child = node.firstChild
    while (child != null) {
        if (child is Element && localName(child) == wanted) out.add(child)
        child = child.nextSibling
    }
    return out
}

fun directChildren(node: Node?): List<Element> {
    if (node == null) return emptyList()
    val out = mutableListOf<Element>()
    var child = node.firstChild
    while (child != null) {
        if (child is Element) out.add(child)
        child = child.nextSibling
    }
    return out
}

fun descendants(node: Node?, tag: String): List<Element> {
    if (node == null) return emptyList()
    val wanted = tag.substringAfterLast(':')
    val out = mutableListOf<Element>()
    collect(node, wanted, out)
    return out
}

private fun collect(node: Node, wanted: String, out: MutableList<Element>) {
    var child = node.firstChild
    while (child != null) {
        if (child is Element) {
            if (localName(child) == wanted) out.add(child)
            collect(child, wanted, out)
        }
        child = child.nextSibling
    }
}

fun textOf(node: Node?): String = xmlText(node)

private fun xmlText(node: Node?): String {
    if (node == null) return ""
    val out = StringBuilder()
    appendText(node, out)
    return out.toString()
}

private fun appendText(node: Node, out: StringBuilder) {
    var child = node.firstChild
    while (child != null) {
        when (child.nodeType) {
            Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> out.append(child.nodeValue ?: "")
            Node.ELEMENT_NODE -> when (localName(child)) {
                "tab" -> out.append('\t')
                "br", "cr" -> out.append('\n')
                "instrText" -> {
                }
                else -> appendText(child, out)
            }
            else -> {
            }
        }
        child = child.nextSibling
    }
}

fun serialize(document: Document): ByteArray {
    val out = StringBuilder()
    out.append(XML_DECLARATION).append('\n')
    var child = document.firstChild
    while (child != null) {
        render(child, out)
        child = child.nextSibling
    }
    return out.toString().toByteArray(Charsets.UTF_8)
}

fun renderNode(node: Node): String {
    val out = StringBuilder()
    render(node, out)
    return out.toString()
}

private fun render(node: Node, out: StringBuilder) {
    when (node.nodeType) {
        Node.DOCUMENT_NODE -> {
            var child = node.firstChild
            while (child != null) {
                render(child, out)
                child = child.nextSibling
            }
        }
        Node.ELEMENT_NODE -> {
            out.append('<').append(node.nodeName)
            val attributes = node.attributes
            if (attributes != null) {
                for (index in 0 until attributes.length) {
                    val item = attributes.item(index) ?: continue
                    out.append(' ').append(item.nodeName).append("=\"")
                    out.append(escapeXml(item.nodeValue ?: "")).append('"')
                }
            }
            if (!node.hasChildNodes()) {
                out.append("/>")
                return
            }
            out.append('>')
            var child = node.firstChild
            while (child != null) {
                render(child, out)
                child = child.nextSibling
            }
            out.append("</").append(node.nodeName).append('>')
        }
        Node.TEXT_NODE -> out.append(escapeXml(node.nodeValue ?: ""))
        Node.CDATA_SECTION_NODE -> out.append("<![CDATA[").append(node.nodeValue ?: "").append("]]>")
        Node.COMMENT_NODE -> out.append("<!--").append(node.nodeValue ?: "").append("-->")
        Node.PROCESSING_INSTRUCTION_NODE -> out.append("<?").append(node.nodeName).append(' ')
            .append(node.nodeValue ?: "").append("?>")
        else -> {
        }
    }
}

fun parseFragment(xml: String, namespaces: String = ""): List<Element> {
    val wrapped = if (namespaces.isEmpty()) "<frag>$xml</frag>" else "<frag $namespaces>$xml</frag>"
    val document = parse(wrapped)
    return directChildren(document.documentElement)
}

fun appendXml(document: Document, parent: Element, xml: String, namespaces: String = ""): Element? {
    val nodes = parseFragment(xml, namespaces)
    var last: Element? = null
    nodes.forEach { element ->
        val imported = document.importNode(element, true) as? Element
        if (imported != null) {
            parent.appendChild(imported)
            last = imported
        }
    }
    return last
}

fun orderedIndex(tag: String, order: List<String>): Int {
    val index = order.indexOf(tag.substringAfterLast(':'))
    return if (index < 0) order.size else index
}

fun insertOrdered(parent: Element, child: Element, tag: String, order: List<String>) {
    val index = orderedIndex(tag, order)
    var reference: Node? = null
    var node = parent.firstChild
    while (node != null) {
        if (node is Element && orderedIndex(localName(node), order) > index) {
            reference = node
            break
        }
        node = node.nextSibling
    }
    if (reference != null) parent.insertBefore(child, reference) else parent.appendChild(child)
}

fun ensureOrdered(document: Document, parent: Element, tag: String, order: List<String>): Element {
    val existing = children(parent, tag).firstOrNull()
    if (existing != null) return existing
    val created = document.createElement(tag)
    insertOrdered(parent, created, tag, order)
    return created
}

fun removeChildren(parent: Element, tag: String) {
    children(parent, tag).forEach { parent.removeChild(it) }
}

fun stringOf(json: JSONObject?, key: String, fallback: String = ""): String {
    if (json == null) return fallback
    val value = json.opt(key) ?: return fallback
    if (value == JSONObject.NULL) return fallback
    val text = value.toString()
    return if (text.isEmpty()) fallback else text
}

fun coreProperty(parts: Map<String, ByteArray>, tag: String): String {
    val bytes = parts["docProps/core.xml"] ?: return ""
    return try {
        val document = parse(bytes)
        textOf(children(document.documentElement, tag).firstOrNull()).trim()
    } catch (e: Exception) {
        ""
    }
}

object Ooxml {

    fun zipBytes(entries: List<Pair<String, ByteArray>>): ByteArray = buildZip(entries)

    fun readZip(file: File): Map<String, ByteArray> = openZip(file)

    fun readEntry(file: File, name: String): ByteArray? = openEntry(file, name)

    fun parse(bytes: ByteArray): Document = parseXml(bytes)

    fun parse(text: String): Document = parseXml(text.toByteArray(Charsets.UTF_8))

    fun escape(value: String): String = escapeXml(value)

    fun textOf(node: Node?): String = xmlText(node)

    fun children(node: Node?, tag: String): List<Element> = elementChildren(node, tag)

    fun attr(node: Node?, name: String): String = attributeOf(node, name)
}

data class MdSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val url: String = ""
)

data class MdBlock(
    val type: String,
    val text: String = "",
    val level: Int = 0,
    val items: List<String> = emptyList(),
    val rows: List<List<String>> = emptyList(),
    val url: String = "",
    val spans: List<MdSpan> = emptyList()
)

object SimpleMarkdown {

    private val HEADING = Regex("""^(#{1,6})\s+(.*)$""")
    private val RULE = Regex("""^(-{3,}|\*{3,}|_{3,})$""")
    private val BULLET = Regex("""^[-*+]\s+(.*)$""")
    private val NUMBER = Regex("""^\d{1,9}[.)]\s+(.*)$""")
    private val FENCE = Regex("""^```.*$""")

    fun blocks(text: String): List<MdBlock> {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n")
        val out = mutableListOf<MdBlock>()
        var index = 0
        while (index < lines.size) {
            val trimmed = lines[index].trim()
            if (trimmed.isEmpty()) {
                index++
                continue
            }
            if (FENCE.matches(trimmed)) {
                val body = mutableListOf<String>()
                index++
                while (index < lines.size && !FENCE.matches(lines[index].trim())) {
                    body.add(lines[index])
                    index++
                }
                if (index < lines.size) index++
                out.add(MdBlock("code", text = body.joinToString("\n")))
                continue
            }
            val heading = HEADING.matchEntire(trimmed)
            if (heading != null) {
                val body = heading.groupValues[2].trim()
                out.add(MdBlock("heading", text = body, level = heading.groupValues[1].length, spans = spans(body)))
                index++
                continue
            }
            if (RULE.matches(trimmed)) {
                out.add(MdBlock("rule"))
                index++
                continue
            }
            if (BULLET.containsMatchIn(trimmed)) {
                val items = mutableListOf<String>()
                while (index < lines.size) {
                    val item = BULLET.find(lines[index].trim()) ?: break
                    items.add(item.groupValues[1].trim())
                    index++
                }
                out.add(MdBlock("bullets", items = items))
                continue
            }
            if (NUMBER.containsMatchIn(trimmed)) {
                val items = mutableListOf<String>()
                while (index < lines.size) {
                    val item = NUMBER.find(lines[index].trim()) ?: break
                    items.add(item.groupValues[1].trim())
                    index++
                }
                out.add(MdBlock("numbers", items = items))
                continue
            }
            if (trimmed.startsWith(">")) {
                val body = mutableListOf<String>()
                while (index < lines.size && lines[index].trim().startsWith(">")) {
                    body.add(lines[index].trim().removePrefix(">").trim())
                    index++
                }
                val joined = body.joinToString("\n")
                out.add(MdBlock("quote", text = joined, spans = spans(joined)))
                continue
            }
            if (tableStart(lines, index)) {
                val rows = mutableListOf<List<String>>()
                rows.add(splitRow(lines[index]))
                index += 2
                while (index < lines.size && lines[index].contains('|') && lines[index].trim().isNotEmpty()) {
                    rows.add(splitRow(lines[index]))
                    index++
                }
                out.add(MdBlock("table", rows = rows))
                continue
            }
            val paragraph = mutableListOf<String>()
            while (index < lines.size) {
                val current = lines[index].trim()
                if (current.isEmpty()) break
                if (paragraph.isNotEmpty() && startsBlock(lines, index)) break
                paragraph.add(current)
                index++
            }
            val joined = paragraph.joinToString(" ")
            out.add(MdBlock("paragraph", text = joined, spans = spans(joined)))
        }
        return out
    }

    fun spans(text: String): List<MdSpan> {
        val out = mutableListOf<MdSpan>()
        val literal = StringBuilder()
        var index = 0
        while (index < text.length) {
            val ch = text[index]
            if (ch == '*' || ch == '_') {
                val doubled = index + 1 < text.length && text[index + 1] == ch
                val marker = if (doubled) "$ch$ch" else "$ch"
                val start = index + marker.length
                val end = text.indexOf(marker, start)
                if (end > start) {
                    if (literal.isNotEmpty()) {
                        out.add(MdSpan(literal.toString()))
                        literal.setLength(0)
                    }
                    out.add(MdSpan(text.substring(start, end), bold = doubled, italic = !doubled))
                    index = end + marker.length
                    continue
                }
            }
            if (ch == '`') {
                val end = text.indexOf('`', index + 1)
                if (end > index + 1) {
                    if (literal.isNotEmpty()) {
                        out.add(MdSpan(literal.toString()))
                        literal.setLength(0)
                    }
                    out.add(MdSpan(text.substring(index + 1, end), code = true))
                    index = end + 1
                    continue
                }
            }
            if (ch == '[') {
                val close = text.indexOf(']', index + 1)
                if (close > index && close + 1 < text.length && text[close + 1] == '(') {
                    val paren = text.indexOf(')', close + 2)
                    if (paren > close + 1) {
                        if (literal.isNotEmpty()) {
                            out.add(MdSpan(literal.toString()))
                            literal.setLength(0)
                        }
                        out.add(MdSpan(text.substring(index + 1, close), url = text.substring(close + 2, paren)))
                        index = paren + 1
                        continue
                    }
                }
            }
            literal.append(ch)
            index++
        }
        if (literal.isNotEmpty()) out.add(MdSpan(literal.toString()))
        return out
    }

    fun plain(spans: List<MdSpan>): String = spans.joinToString("") { it.text }

    private fun tableStart(lines: List<String>, index: Int): Boolean {
        if (index + 1 >= lines.size) return false
        if (!lines[index].contains('|')) return false
        return separator(lines[index + 1])
    }

    private fun separator(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return false
        if (!trimmed.contains('-')) return false
        return trimmed.all { it == '|' || it == '-' || it == ':' || it == ' ' }
    }

    private fun startsBlock(lines: List<String>, index: Int): Boolean {
        val trimmed = lines[index].trim()
        if (trimmed.isEmpty()) return true
        if (FENCE.matches(trimmed)) return true
        if (HEADING.matchEntire(trimmed) != null) return true
        if (RULE.matches(trimmed)) return true
        if (BULLET.containsMatchIn(trimmed)) return true
        if (NUMBER.containsMatchIn(trimmed)) return true
        if (trimmed.startsWith(">")) return true
        return tableStart(lines, index)
    }

    private fun splitRow(line: String): List<String> {
        var clean = line.trim()
        if (clean.startsWith("|")) clean = clean.removePrefix("|")
        if (clean.endsWith("|")) clean = clean.removeSuffix("|")
        return clean.split("|").map { it.trim() }
    }
}
