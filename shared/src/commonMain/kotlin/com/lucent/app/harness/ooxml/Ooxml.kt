package com.lucent.app.harness.ooxml

import okio.Path.Companion.toPath

import kotlinx.serialization.json.*
import com.lucent.app.harness.ZipWriter
import okio.FileSystem
import okio.Path

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

fun writZip(out: Path, entries: List<Pair<String, ByteArray>>) {
    val parent = out.parent
    if (parent != null) FileSystem.SYSTEM.createDirectories(parent)
    FileSystem.SYSTEM.write(out) { write(buildZip(entries)) }
}

private fun buildZip(entries: List<Pair<String, ByteArray>>): ByteArray {
    val head = entries.firstOrNull { it.first == CONTENT_TYPES_PART }
    val ordered = if (head == null) entries else listOf(head) + entries.filter { it.first != CONTENT_TYPES_PART }
    val writer = ZipWriter()
    ordered.forEach { entry ->
        if (storedEntry(entry.first)) writer.addStoredEntry(entry.first, entry.second)
        else writer.addEntry(entry.first, entry.second)
    }
    return writer.close()
}

fun readZip(path: Path): Map<String, ByteArray> {
    if (!FileSystem.SYSTEM.exists(path)) throw IllegalArgumentException("${path.name} does not exist")
    return try {
        val bytes = FileSystem.SYSTEM.read(path) { readByteArray() }
        com.lucent.app.harness.ZipReader.readEntries(bytes) { true }
    } catch (e: Exception) {
        throw IllegalArgumentException("${path.name} is not a readable Office package: ${e.message ?: "zip error"}")
    }
}

fun readEntry(path: Path, name: String): ByteArray? {
    if (!FileSystem.SYSTEM.exists(path)) return null
    return try {
        val bytes = FileSystem.SYSTEM.read(path) { readByteArray() }
        val map = com.lucent.app.harness.ZipReader.readEntries(bytes) { it == name }
        map[name]
    } catch (e: Exception) {
        null
    }
}

fun parse(bytes: ByteArray): XmlNode = parseXmlDocument(bytes)


fun parse(text: String): XmlNode = parseXmlDocument(text.encodeToByteArray())

fun localName(node: XmlNode): String = node.localName

fun attr(node: XmlNode?, name: String): String {
    if (node == null) return ""
    val wanted = name.substringAfterLast(':')
    return node.attributes[name] ?: node.attributes.entries.firstOrNull { it.key.substringAfterLast(':') == wanted }?.value ?: ""
}

fun children(node: XmlNode?, tag: String): List<XmlNode> {
    if (node == null) return emptyList()
    val wanted = tag.substringAfterLast(':')
    return node.children.filter { it.localName == wanted }
}

fun directChildren(node: XmlNode?): List<XmlNode> {
    return node?.children ?: emptyList()
}

fun descendants(node: XmlNode?, tag: String): List<XmlNode> {
    if (node == null) return emptyList()
    val wanted = tag.substringAfterLast(':')
    val out = mutableListOf<XmlNode>()
    collect(node, wanted, out)
    return out
}

private fun collect(node: XmlNode, wanted: String, out: MutableList<XmlNode>) {
    for (child in node.children) {
        if (child.localName == wanted) out.add(child)
        collect(child, wanted, out)
    }
}

fun textOf(node: XmlNode?): String {
    if (node == null) return ""
    val out = StringBuilder()
    appendText(node, out)
    return out.toString()
}

private fun appendText(node: XmlNode, out: StringBuilder) {
    if (node.text.isNotEmpty()) out.append(node.text)
    for (child in node.children) {
        when (child.localName) {
            "tab" -> out.append('\t')
            "br", "cr" -> out.append('\n')
            "instrText" -> {}
            else -> appendText(child, out)
        }
    }
}

fun serialize(document: XmlNode): ByteArray {
    val out = StringBuilder()
    out.append(XML_DECLARATION).append('\n')
    render(document, out)
    return out.toString().encodeToByteArray()
}

fun renderNode(node: XmlNode): String {
    val out = StringBuilder()
    render(node, out)
    return out.toString()
}

private fun render(node: XmlNode, out: StringBuilder) {
    if (node.name.isEmpty()) {
        out.append(escapeXml(node.text))
        return
    }
    out.append('<').append(node.name)
    for ((k, v) in node.attributes) {
        out.append(' ').append(k).append("=\"").append(escapeXml(v)).append('\"')
    }
    if (node.children.isEmpty() && node.text.isEmpty()) {
        out.append("/>")
        return
    }
    out.append('>')
    if (node.text.isNotEmpty()) out.append(escapeXml(node.text))
    for (child in node.children) render(child, out)
    out.append("</").append(node.name).append('>')
}

fun parseFragment(xml: String, namespaces: String = ""): List<XmlNode> {
    val wrapped = if (namespaces.isEmpty()) "<frag>$xml</frag>" else "<frag $namespaces>$xml</frag>"
    val document = parse(wrapped)
    return document.children
}

fun appendXml(document: XmlNode, parent: XmlNode, xml: String, namespaces: String = ""): XmlNode? {
    val nodes = parseFragment(xml, namespaces)
    var last: XmlNode? = null
    for (element in nodes) {
        parent.children.add(element)
        last = element
    }
    return last
}

fun orderedIndex(tag: String, order: List<String>): Int {
    val index = order.indexOf(tag.substringAfterLast(':'))
    return if (index < 0) order.size else index
}

fun insertOrdered(parent: XmlNode, child: XmlNode, tag: String, order: List<String>) {
    val index = orderedIndex(tag, order)
    var reference: XmlNode? = null
    for (node in parent.children) {
        if (orderedIndex(localName(node), order) > index) {
            reference = node
            break
        }
    }
    if (reference != null) parent.insertBefore(child, reference) else parent.appendChild(child)
}

fun ensureOrdered(document: XmlNode, parent: XmlNode, tag: String, order: List<String>): XmlNode {
    val existing = children(parent, tag).firstOrNull()
    if (existing != null) return existing
    val created = document.createXmlNode(tag)
    insertOrdered(parent, created, tag, order)
    return created
}

fun removeChildren(parent: XmlNode, tag: String) {
    children(parent, tag).forEach { parent.children.remove(it) }
}

fun stringOf(json: JsonObject?, key: String, fallback: String = ""): String {
    if (json == null) return fallback
    val value = json[key]?.jsonPrimitive?.content ?: return fallback
    return if (value.isEmpty()) fallback else value
}

fun coreProperty(parts: Map<String, ByteArray>, tag: String): String {
    val bytes = parts["docProps/core.xml"] ?: return ""
    return try {
        val document = parse(bytes)
        textOf(children(document, tag).firstOrNull()).trim()
    } catch (e: Exception) {
        ""
    }
}

object Ooxml {

    fun zipBytes(entries: List<Pair<String, ByteArray>>): ByteArray = buildZip(entries)

    fun readZip(path: Path): Map<String, ByteArray> = com.lucent.app.harness.ooxml.readZip(path)

    fun readEntry(path: Path, name: String): ByteArray? = com.lucent.app.harness.ooxml.readEntry(path, name)

    fun parse(bytes: ByteArray): XmlNode = parseXmlDocument(bytes)

    fun parse(text: String): XmlNode = parseXmlDocument(text.encodeToByteArray())

    fun escape(value: String): String = escapeXml(value)

    fun textOf(node: XmlNode?): String = textOf(node)

    fun children(node: XmlNode?, tag: String): List<XmlNode> = children(node, tag)

    fun attr(node: XmlNode?, name: String): String = attr(node, name)
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


fun XmlNode.createXmlNode(name: String): XmlNode {
    return XmlNode(name, name.substringAfterLast(':'), mutableMapOf(), mutableListOf(), "")
}

fun XmlNode.createTextXmlNode(text: String): XmlNode {
    return XmlNode("", "", mutableMapOf(), mutableListOf(), text)
}

fun XmlNode.importXmlNode(node: XmlNode, deep: Boolean): XmlNode {
    return XmlNode(
        node.name,
        node.localName,
        node.attributes.toMutableMap(),
        if (deep) node.children.map { this.importXmlNode(it, true) }.toMutableList() else mutableListOf(),
        node.text
    )
}

fun XmlNode.appendChild(child: XmlNode) {
    this.children.add(child)
}

fun XmlNode.insertBefore(child: XmlNode, ref: XmlNode?) {
    if (ref == null) {
        this.children.add(child)
    } else {
        val idx = this.children.indexOf(ref)
        if (idx >= 0) this.children.add(idx, child)
        else this.children.add(child)
    }
}

fun XmlNode.removeChild(child: XmlNode) {
    this.children.remove(child)
}

fun parentOf(root: XmlNode, target: XmlNode): XmlNode? {
    if (target in root.children) return root
    for (c in root.children) {
        val p = parentOf(c, target)
        if (p != null) return p
    }
    return null
}

fun parentOf(root: XmlNode?, target: XmlNode): XmlNode? {
    if (root == null) return null
    if (root.children.contains(target)) return root
    for (child in root.children) {
        val found = parentOf(child, target)
        if (found != null) return found
    }
    return null
}
