package com.lucent.app.harness

import com.lucent.app.network.ToolExecResult
import com.lucent.app.network.ToolImage
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.Inflater

private const val PDF_MAX_BYTES = 96L * 1024 * 1024
private const val PDF_MAX_PAGES = 5000
private const val PDF_MAX_DEPTH = 64
private const val PDF_MAX_STREAM = 64 * 1024 * 1024

private val PDF_WHITESPACE = charArrayOf(' ', '\t', '\r', '\n', '\u000C', '\u0000')

private sealed class PdfValue

private object PdfNull : PdfValue()

private class PdfBool(val value: Boolean) : PdfValue()

private class PdfNumber(val value: Double) : PdfValue() {
    fun asInt(fallback: Int): Int = if (value.isFinite()) value.toInt() else fallback
}

private class PdfString(val bytes: ByteArray) : PdfValue() {
    val size: Int get() = bytes.size

    fun codeAt(index: Int, length: Int): Int {
        var out = 0
        for (i in 0 until length) {
            val at = index + i
            val byte = if (at < bytes.size) bytes[at].toInt() and 0xFF else 0
            out = (out shl 8) or byte
        }
        return out
    }

    fun utf16(): String {
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16BE)
        }
        return String(bytes, StandardCharsets.ISO_8859_1)
    }
}

private class PdfName(val name: String) : PdfValue()

private class PdfArray(val items: MutableList<PdfValue> = mutableListOf()) : PdfValue()

private class PdfDict(val entries: LinkedHashMap<String, PdfValue> = LinkedHashMap()) : PdfValue() {
    fun get(key: String): PdfValue? = entries[key]
    fun name(key: String): String? = (entries[key] as? PdfName)?.name
    fun number(key: String): Double? = (entries[key] as? PdfNumber)?.value
    fun int(key: String, fallback: Int): Int = (entries[key] as? PdfNumber)?.asInt(fallback) ?: fallback
    fun dict(key: String): PdfDict? = entries[key] as? PdfDict
    fun array(key: String): PdfArray? = entries[key] as? PdfArray
}

private class PdfRef(val number: Int, val generation: Int) : PdfValue()

private class PdfOperator(val name: String) : PdfValue()

private class PdfStream(val dict: PdfDict, val raw: ByteArray) : PdfValue() {
    private var cached: ByteArray? = null

    fun decoded(): ByteArray {
        val ready = cached
        if (ready != null) return ready
        val out = pdfDecodeStream(dict, raw)
        cached = out
        return out
    }
}

private class PdfParser(private val text: String, var pos: Int) {

    fun eof(): Boolean = pos >= text.length

    fun skip() {
        while (pos < text.length) {
            val ch = text[pos]
            if (ch == '%') {
                while (pos < text.length && text[pos] != '\n' && text[pos] != '\r') pos++
            } else if (ch == ' ' || ch == '\t' || ch == '\r' || ch == '\n' || ch == '\u000C' || ch == '\u0000') {
                pos++
            } else {
                return
            }
        }
    }

    fun peekChar(): Char = if (pos < text.length) text[pos] else '\u0000'

    fun parse(): PdfValue {
        skip()
        if (pos >= text.length) return PdfNull
        val ch = text[pos]
        return when {
            ch == '<' && pos + 1 < text.length && text[pos + 1] == '<' -> parseDict()
            ch == '<' -> parseHexString()
            ch == '[' -> parseArray()
            ch == '(' -> parseLiteralString()
            ch == '/' -> parseName()
            ch == ']' || ch == '>' || ch == ')' || ch == '}' || ch == '{' -> {
                pos++
                PdfNull
            }
            else -> parseToken()
        }
    }

    private fun parseDict(): PdfValue {
        val dict = PdfDict()
        pos += 2
        while (true) {
            skip()
            if (pos >= text.length) break
            if (text[pos] == '>' && pos + 1 < text.length && text[pos + 1] == '>') {
                pos += 2
                break
            }
            if (text[pos] != '/') {
                val probe = parse()
                if (probe is PdfNull || probe is PdfOperator) continue
                continue
            }
            val key = parseName()
            val keyName = (key as? PdfName)?.name ?: continue
            val value = parse()
            dict.entries[keyName] = value
        }
        return dict
    }

    private fun parseArray(): PdfValue {
        val array = PdfArray()
        pos++
        while (true) {
            skip()
            if (pos >= text.length) break
            if (text[pos] == ']') {
                pos++
                break
            }
            array.items.add(parse())
        }
        return array
    }

    private fun parseName(): PdfValue {
        pos++
        val sb = StringBuilder()
        while (pos < text.length) {
            val ch = text[pos]
            if (ch == ' ' || ch == '\t' || ch == '\r' || ch == '\n' || ch == '\u000C' || ch == '\u0000') break
            if (ch == '/' || ch == '[' || ch == ']' || ch == '<' || ch == '>' || ch == '(' || ch == ')' || ch == '{' || ch == '}' || ch == '%') break
            if (ch == '#') {
                if (pos + 2 < text.length) {
                    val code = text.substring(pos + 1, pos + 3).toIntOrNull(16)
                    if (code != null) {
                        sb.append(code.toChar())
                        pos += 3
                        continue
                    }
                }
                pos++
                continue
            }
            sb.append(ch)
            pos++
        }
        return PdfName(sb.toString())
    }

    private fun parseHexString(): PdfValue {
        pos++
        val sb = StringBuilder()
        while (pos < text.length && text[pos] != '>') {
            val ch = text[pos]
            if (ch.isDigit() || (ch in 'a'..'f') || (ch in 'A'..'F')) sb.append(ch)
            pos++
        }
        if (pos < text.length) pos++
        if (sb.length % 2 == 1) sb.append('0')
        val out = ByteArray(sb.length / 2)
        var i = 0
        while (i < out.size) {
            val value = sb.substring(i * 2, i * 2 + 2).toIntOrNull(16) ?: 0
            out[i] = value.toByte()
            i++
        }
        return PdfString(out)
    }

    private fun parseLiteralString(): PdfValue {
        pos++
        val out = ByteArrayOutputStream()
        var depth = 1
        while (pos < text.length) {
            val ch = text[pos]
            pos++
            when {
                ch == '\\' -> {
                    if (pos >= text.length) break
                    val escaped = text[pos]
                    pos++
                    when (escaped) {
                        'n' -> out.write('\n'.code)
                        'r' -> out.write('\r'.code)
                        't' -> out.write('\t'.code)
                        'b' -> out.write('\b'.code)
                        'f' -> out.write('\u000C'.code)
                        '(', ')', '\\' -> out.write(escaped.code)
                        '\r' -> if (pos < text.length && text[pos] == '\n') pos++
                        '\n' -> {
                        }
                        in '0'..'7' -> {
                            var value = escaped - '0'
                            var count = 1
                            while (count < 3 && pos < text.length && text[pos] in '0'..'7') {
                                value = value * 8 + (text[pos] - '0')
                                pos++
                                count++
                            }
                            out.write(value and 0xFF)
                        }
                        else -> out.write(escaped.code)
                    }
                }
                ch == '(' -> {
                    depth++
                    out.write(ch.code)
                }
                ch == ')' -> {
                    depth--
                    if (depth == 0) break
                    out.write(ch.code)
                }
                else -> out.write(ch.code)
            }
        }
        return PdfString(out.toByteArray())
    }

    private fun parseToken(): PdfValue {
        val start = pos
        while (pos < text.length) {
            val ch = text[pos]
            if (ch == ' ' || ch == '\t' || ch == '\r' || ch == '\n' || ch == '\u000C' || ch == '\u0000') break
            if (ch == '/' || ch == '[' || ch == ']' || ch == '<' || ch == '>' || ch == '(' || ch == ')' || ch == '{' || ch == '}' || ch == '%') break
            pos++
        }
        if (pos == start) {
            pos++
            return PdfNull
        }
        val token = text.substring(start, pos)
        when (token) {
            "true" -> return PdfBool(true)
            "false" -> return PdfBool(false)
            "null" -> return PdfNull
        }
        val number = token.toDoubleOrNull()
        if (number == null) return PdfOperator(token)
        if (token.indexOf('.') < 0 && token.indexOf('e') < 0 && token.indexOf('E') < 0) {
            val save = pos
            skip()
            val nextStart = pos
            while (pos < text.length && text[pos].isDigit()) pos++
            val next = text.substring(nextStart, pos)
            if (next.isNotEmpty()) {
                skip()
                if (pos < text.length && text[pos] == 'R' && (pos + 1 >= text.length || pdfIsDelimiter(text[pos + 1]))) {
                    pos++
                    return PdfRef(number.toInt(), next.toInt())
                }
            }
            pos = save
        }
        return PdfNumber(number)
    }

    fun skipInlineImage() {
        val idAt = text.indexOf("ID", pos)
        if (idAt < 0) {
            pos = text.length
            return
        }
        pos = idAt + 2
        if (pos < text.length && text[pos] == ' ') pos++
        if (pos < text.length && text[pos] == '\r') pos++
        if (pos < text.length && text[pos] == '\n') pos++
        var search = pos
        while (true) {
            val at = text.indexOf("EI", search)
            if (at < 0) {
                pos = text.length
                return
            }
            val before = at == 0 || pdfIsDelimiter(text[at - 1])
            val afterAt = at + 2
            val after = afterAt >= text.length || pdfIsDelimiter(text[afterAt])
            if (before && after) {
                pos = afterAt
                return
            }
            search = at + 2
        }
    }
}

private class PdfPendingStream(val number: Int, val dict: PdfDict, val start: Int)

private class PdfParsed(
    val objects: MutableMap<Int, PdfValue>,
    val streams: MutableList<PdfPendingStream>,
    val trailer: PdfDict?
)

private fun pdfIsDelimiter(ch: Char): Boolean =
    PDF_WHITESPACE.contains(ch) || ch == '/' || ch == '[' || ch == ']' || ch == '<' || ch == '>' ||
        ch == '(' || ch == ')' || ch == '{' || ch == '}' || ch == '%'

private fun pdfScan(text: String): PdfParsed {
    val objects = HashMap<Int, PdfValue>()
    val streams = mutableListOf<PdfPendingStream>()
    var index = 0
    while (true) {
        val at = text.indexOf("obj", index)
        if (at < 0) break
        index = at + 3
        if (at + 3 < text.length && !pdfIsDelimiter(text[at + 3])) continue
        var cursor = at - 1
        while (cursor >= 0 && PDF_WHITESPACE.contains(text[cursor])) cursor--
        val generationEnd = cursor + 1
        while (cursor >= 0 && text[cursor].isDigit()) cursor--
        val generationStart = cursor + 1
        if (generationStart >= generationEnd) continue
        while (cursor >= 0 && PDF_WHITESPACE.contains(text[cursor])) cursor--
        val numberEnd = cursor + 1
        while (cursor >= 0 && text[cursor].isDigit()) cursor--
        val numberStart = cursor + 1
        if (numberStart >= numberEnd) continue
        val objectNumber = text.substring(numberStart, numberEnd).toIntOrNull() ?: continue
        val parser = PdfParser(text, index)
        parser.skip()
        if (text.startsWith("endobj", parser.pos)) continue
        val value = parser.parse()
        if (value is PdfOperator || value is PdfNull) continue
        parser.skip()
        if (value is PdfDict && text.startsWith("stream", parser.pos)) {
            var dataStart = parser.pos + 6
            if (dataStart < text.length && text[dataStart] == '\r') dataStart++
            if (dataStart < text.length && text[dataStart] == '\n') dataStart++
            streams.add(PdfPendingStream(objectNumber, value, dataStart))
        } else {
            objects[objectNumber] = value
        }
    }
    for (pending in streams) {
        val end = pdfStreamEnd(text, pending, objects)
        val raw = text.substring(pending.start, end).toByteArray(StandardCharsets.ISO_8859_1)
        objects[pending.number] = PdfStream(pending.dict, raw)
    }
    val trailer = pdfFindTrailer(text, objects)
    return PdfParsed(objects, streams, trailer)
}

private fun pdfStreamEnd(text: String, pending: PdfPendingStream, objects: Map<Int, PdfValue>): Int {
    val resolved = pdfResolve(objects, pending.dict.get("Length"))
    val length = (resolved as? PdfNumber)?.asInt(-1) ?: -1
    if (length >= 0 && pending.start + length <= text.length && length <= PDF_MAX_STREAM) {
        var probe = pending.start + length
        while (probe < text.length && PDF_WHITESPACE.contains(text[probe])) probe++
        if (text.startsWith("endstream", probe)) return pending.start + length
    }
    val at = text.indexOf("endstream", pending.start)
    var end = if (at < 0) text.length else at
    while (end > pending.start && (text[end - 1] == '\n' || text[end - 1] == '\r')) end--
    return end
}

private fun pdfFindTrailer(text: String, objects: Map<Int, PdfValue>): PdfDict? {
    var index = text.length
    var found: PdfDict? = null
    var guard = 0
    while (guard < 64) {
        guard++
        val at = text.lastIndexOf("trailer", index - 1)
        if (at < 0) break
        val parser = PdfParser(text, at + 7)
        val value = parser.parse()
        if (value is PdfDict) {
            found = value
            break
        }
        index = at
    }
    if (found != null) return found
    objects.values.forEach { value ->
        val dict = pdfAsDict(value) ?: return@forEach
        if (dict.name("Type") == "XRef" && dict.get("Root") != null && found == null) found = dict
    }
    return found
}

private class PdfPageNode(val dict: PdfDict, val resources: PdfDict?, val mediaBox: DoubleArray, val rotate: Int)

private class PdfFont(
    val type0: Boolean,
    val codes: Map<Int, String>,
    val differences: Map<Int, String>,
    val widths: Map<Int, Double>,
    val defaultWidth: Double,
    val spaceLengths: List<IntArray>
) {
    fun codeLength(bytes: ByteArray, index: Int): Int {
        if (!type0) return 1
        for (length in 1..4) {
            if (index + length > bytes.size) break
            var value = 0
            var i = 0
            while (i < length) {
                value = (value shl 8) or (bytes[index + i].toInt() and 0xFF)
                i++
            }
            for (range in spaceLengths) {
                if (range[0] == length && value >= range[1] && value <= range[2]) return length
            }
        }
        return 2
    }

    fun width(code: Int): Double {
        val direct = widths[code]
        if (direct != null) return direct
        return defaultWidth
    }
}

private class PdfDocument(
    val file: File,
    val objects: Map<Int, PdfValue>,
    val trailer: PdfDict?,
    val pages: List<PdfPageNode>,
    val encrypted: Boolean,
    val info: PdfDict?
) {
    private val fontCache = HashMap<Int, PdfFont>()

    fun font(dict: PdfDict?, key: Int): PdfFont? {
        if (dict == null) return null
        val cached = fontCache[key]
        if (cached != null) return cached
        val font = pdfBuildFont(objects, dict)
        fontCache[key] = font
        return font
    }
}

private fun pdfAsDict(value: PdfValue?): PdfDict? = when (value) {
    is PdfDict -> value
    is PdfStream -> value.dict
    else -> null
}

private fun pdfAsArray(value: PdfValue?): PdfArray? = value as? PdfArray

private fun pdfAsStream(value: PdfValue?): PdfStream? = value as? PdfStream

private fun pdfResolve(objects: Map<Int, PdfValue>, value: PdfValue?, depth: Int = 0): PdfValue? {
    if (value is PdfRef && depth < 32) return pdfResolve(objects, objects[value.number], depth + 1)
    return value
}

private fun pdfResolveDict(objects: Map<Int, PdfValue>, value: PdfValue?): PdfDict? =
    pdfAsDict(pdfResolve(objects, value))

private fun pdfLoad(file: File): PdfDocument {
    if (!file.exists()) throw IllegalArgumentException("${file.name} does not exist")
    if (file.isDirectory) throw IllegalArgumentException("${file.name} is a directory, not a PDF")
    val size = file.length()
    if (size > PDF_MAX_BYTES) {
        throw IllegalArgumentException("${file.name} is ${size / 1048576} MiB; PDFs larger than ${PDF_MAX_BYTES / 1048576} MiB are not read here")
    }
    val bytes = file.readBytes()
    val head = String(bytes, 0, minOf(bytes.size, 1024), StandardCharsets.ISO_8859_1)
    if (!head.contains("%PDF-")) throw IllegalArgumentException("${file.name} does not look like a PDF file")
    val text = String(bytes, StandardCharsets.ISO_8859_1)
    val parsed = pdfScan(text)
    val objects = parsed.objects
    var encrypted = false
    objects.values.forEach { value ->
        val dict = pdfAsDict(value) ?: return@forEach
        if (dict.name("Type") == "Encrypt") encrypted = true
    }
    if (parsed.trailer?.get("Encrypt") != null) encrypted = true
    val info = pdfResolveDict(objects, parsed.trailer?.get("Info"))
    val root = pdfResolveDict(objects, parsed.trailer?.get("Root")) ?: pdfFindCatalog(objects)
    val pages = if (root == null) emptyList() else pdfCollectPages(objects, root)
    return PdfDocument(file, objects, parsed.trailer, pages, encrypted, info)
}

private fun pdfFindCatalog(objects: Map<Int, PdfValue>): PdfDict? {
    objects.values.forEach { value ->
        val dict = pdfAsDict(value) ?: return@forEach
        if (dict.name("Type") == "Catalog") return dict
    }
    return null
}

private class PdfInherit(val resources: PdfDict?, val mediaBox: DoubleArray?, val rotate: Int)

private fun pdfCollectPages(objects: Map<Int, PdfValue>, root: PdfDict): List<PdfPageNode> {
    val out = mutableListOf<PdfPageNode>()
    val seen = HashSet<Int>()
    val pagesRef = root.get("Pages")
    val pagesDict = pdfResolveDict(objects, pagesRef)
    if (pagesDict != null) {
        val key = (pagesRef as? PdfRef)?.number ?: -1
        pdfWalkPages(objects, pagesDict, PdfInherit(null, null, 0), out, seen, key, 0)
    }
    return out
}

private fun pdfWalkPages(
    objects: Map<Int, PdfValue>,
    node: PdfDict,
    inherited: PdfInherit,
    out: MutableList<PdfPageNode>,
    seen: MutableSet<Int>,
    key: Int,
    depth: Int
) {
    if (depth > PDF_MAX_DEPTH || out.size >= PDF_MAX_PAGES) return
    if (key >= 0 && !seen.add(key)) return
    val resources = pdfResolveDict(objects, node.get("Resources")) ?: inherited.resources
    val media = pdfResolve(objects, node.get("MediaBox"))
    val mediaBox = pdfNumbers(media) ?: inherited.mediaBox
    val rotate = (node.get("Rotate") as? PdfNumber)?.asInt(inherited.rotate) ?: inherited.rotate
    val kids = pdfAsArray(pdfResolve(objects, node.get("Kids")))
    if (kids == null) {
        out.add(PdfPageNode(node, resources, mediaBox ?: doubleArrayOf(0.0, 0.0, 612.0, 792.0), rotate))
        return
    }
    val next = PdfInherit(resources, mediaBox, rotate)
    kids.items.forEach { kid ->
        val childKey = (kid as? PdfRef)?.number ?: -1
        val child = pdfResolveDict(objects, kid) ?: return@forEach
        pdfWalkPages(objects, child, next, out, seen, childKey, depth + 1)
    }
}

private fun pdfNumbers(value: PdfValue?): DoubleArray? {
    val array = value as? PdfArray ?: return null
    if (array.items.size < 4) return null
    val out = DoubleArray(4)
    for (i in 0 until 4) {
        val number = array.items[i] as? PdfNumber ?: return null
        out[i] = number.value
    }
    return out
}

private fun pdfDecodeStream(dict: PdfDict, raw: ByteArray): ByteArray {
    var data = raw
    val parsms = dict.get("DecodeParms") ?: dict.get("DP")
    val filters = dict.get("Filter") ?: dict.get("F")
    val names = mutableListOf<String>()
    when (filters) {
        is PdfName -> names.add(filters.name)
        is PdfArray -> filters.items.forEach { item -> (item as? PdfName)?.let { names.add(it.name) } }
        else -> {
        }
    }
    val parmList = mutableListOf<PdfDict?>()
    when (parsms) {
        is PdfDict -> parmList.add(parsms)
        is PdfArray -> parsms.items.forEach { item -> parmList.add(pdfAsDict(item)) }
        else -> {
        }
    }
    names.forEachIndexed { index, name ->
        val parm = parmList.getOrNull(index)
        data = when (name) {
            "FlateDecode", "Fl" -> {
                val inflated = pdfInflate(data)
                if (inflated == null) data else pdfApplyPredictor(inflated, parm)
            }
            "LZWDecode", "LZW" -> pdfApplyPredictor(data, parm)
            else -> data
        }
    }
    if (names.isEmpty()) {
        val inflated = pdfInflate(data)
        if (inflated != null) return inflated
    }
    return data
}

private fun pdfInflate(data: ByteArray): ByteArray? {
    if (data.isEmpty()) return null
    val zlibWrapped = data.size > 2 && (data[0].toInt() and 0x0F) == 8
    if (zlibWrapped) {
        val wrapped = pdfInflateAt(data, 2)
        if (wrapped != null && wrapped.isNotEmpty()) return wrapped
    }
    val direct = pdfInflateAt(data, 0)
    if (direct != null && direct.isNotEmpty()) return direct
    if (data.size > 2) return pdfInflateAt(data, 2)
    return null
}

private fun pdfInflateAt(data: ByteArray, offset: Int): ByteArray? {
    val inflater = Inflater(true)
    return try {
        inflater.setInput(data, offset, data.size - offset)
        val out = ByteArrayOutputStream(minOf(1 shl 20, maxOf(256, data.size * 3)))
        val buffer = ByteArray(32768)
        while (!inflater.finished()) {
            val produced = inflater.inflate(buffer)
            if (produced == 0) {
                if (inflater.needsInput() || inflater.needsDictionary()) break
            }
            out.write(buffer, 0, produced)
            if (out.size() > PDF_MAX_STREAM) break
        }
        val result = out.toByteArray()
        if (result.isEmpty() && data.size > 2) null else result
    } catch (e: Exception) {
        null
    } finally {
        inflater.end()
    }
}

private fun pdfApplyPredictor(data: ByteArray, parms: PdfDict?): ByteArray {
    if (parms == null) return data
    val predictor = (parms.get("Predictor") as? PdfNumber)?.asInt(1) ?: 1
    if (predictor <= 1) return data
    val colors = (parms.get("Colors") as? PdfNumber)?.asInt(1) ?: 1
    val bits = (parms.get("BitsPerComponent") as? PdfNumber)?.asInt(8) ?: 8
    val columns = (parms.get("Columns") as? PdfNumber)?.asInt(1) ?: 1
    if (bits != 8) return data
    val rowLength = colors * columns
    if (rowLength <= 0) return data
    if (predictor == 2) {
        val rows = data.size / rowLength
        var r = 1
        while (r < rows) {
            var i = 0
            while (i < rowLength) {
                val at = r * rowLength + i
                data[at] = (data[at].toInt() + data[at - rowLength].toInt()).toByte()
                i++
            }
            r++
        }
        return data
    }
    val stride = rowLength + 1
    if (stride <= 1) return data
    val rows = data.size / stride
    val out = ByteArray(rows * rowLength)
    var previous = ByteArray(rowLength)
    var r = 0
    while (r < rows) {
        val filter = data[r * stride].toInt() and 0xFF
        val row = data.copyOfRange(r * stride + 1, r * stride + 1 + rowLength)
        var i = 0
        while (i < rowLength) {
            val left = if (i >= colors) row[i - colors].toInt() and 0xFF else 0
            val up = previous[i].toInt() and 0xFF
            val upLeft = if (i >= colors) previous[i - colors].toInt() and 0xFF else 0
            val value = row[i].toInt() and 0xFF
            val decoded = when (filter) {
                0 -> value
                1 -> value + left
                2 -> value + up
                3 -> value + (left + up) / 2
                4 -> value + pdfPaeth(left, up, upLeft)
                else -> value
            }
            row[i] = decoded.toByte()
            i++
        }
        System.arraycopy(row, 0, out, r * rowLength, rowLength)
        previous = row
        r++
    }
    return out
}

private fun pdfPaeth(a: Int, b: Int, c: Int): Int {
    val p = a + b - c
    val pa = Math.abs(p - a)
    val pb = Math.abs(p - b)
    val pc = Math.abs(p - c)
    return when {
        pa <= pb && pa <= pc -> a
        pb <= pc -> b
        else -> c
    }
}

private class PdfCMap(val codes: HashMap<Int, String>, val spaces: MutableList<IntArray>)

private fun pdfParseCMap(bytes: ByteArray): PdfCMap {
    val map = PdfCMap(HashMap(), mutableListOf())
    val text = String(bytes, StandardCharsets.ISO_8859_1)
    val parser = PdfParser(text, 0)
    var guard = 0
    while (guard < 2000000) {
        guard++
        parser.skip()
        if (parser.eof()) break
        val token = parser.parse()
        if (token !is PdfName) continue
        when (token.name) {
            "beginbfchar" -> {
                while (true) {
                    val source = parser.parse() as? PdfString ?: break
                    val target = parser.parse() as? PdfString ?: break
                    map.codes[source.codeAt(0, source.size)] = target.utf16()
                }
            }
            "beginbfrange" -> {
                while (true) {
                    val low = parser.parse() as? PdfString ?: break
                    val high = parser.parse() as? PdfString ?: break
                    val lowCode = low.codeAt(0, low.size)
                    val highCode = high.codeAt(0, high.size)
                    val destination = parser.parse()
                    if (destination is PdfString) {
                        val base = destination.codeAt(0, destination.size)
                        val baseText = destination.utf16()
                        if (highCode >= lowCode && highCode - lowCode <= 65535) {
                            var code = lowCode
                            while (code <= highCode) {
                                map.codes[code] = pdfShiftText(baseText, code - base)
                                code++
                            }
                        }
                    } else if (destination is PdfArray) {
                        destination.items.forEachIndexed { index, item ->
                            val entry = item as? PdfString ?: return@forEachIndexed
                            map.codes[lowCode + index] = entry.utf16()
                        }
                    }
                }
            }
            "begincodespacerange" -> {
                while (true) {
                    val low = parser.parse() as? PdfString ?: break
                    val high = parser.parse() as? PdfString ?: break
                    val length = maxOf(low.size, high.size)
                    map.spaces.add(intArrayOf(length, low.codeAt(0, low.size), high.codeAt(0, high.size)))
                }
            }
            else -> {
            }
        }
    }
    return map
}

private fun pdfShiftText(text: String, delta: Int): String {
    if (delta == 0 || text.isEmpty()) return text
    val last = text[text.length - 1]
    return text.substring(0, text.length - 1) + (last + delta)
}

private fun pdfBuildFont(objects: Map<Int, PdfValue>, dict: PdfDict): PdfFont {
    val subtype = dict.name("Subtype") ?: ""
    val type0 = subtype == "Type0"
    val codes = HashMap<Int, String>()
    var spaces = mutableListOf<IntArray>()
    val toUnicode = pdfAsStream(pdfResolve(objects, dict.get("ToUnicode")))
    if (toUnicode != null) {
        val cmap = pdfParseCMap(toUnicode.decoded())
        codes.putAll(cmap.codes)
        spaces = cmap.spaces
    }
    val differences = HashMap<Int, String>()
    if (!type0) {
        val encoding = pdfResolve(objects, dict.get("Encoding"))
        val encodingDict = pdfAsDict(encoding)
        val differencesArray = pdfAsArray(pdfResolve(objects, encodingDict?.get("Differences")))
        if (differencesArray != null) {
            var current = 0
            differencesArray.items.forEach { item ->
                when (item) {
                    is PdfNumber -> current = item.asInt(current)
                    is PdfName -> {
                        differences[current] = item.name
                        current++
                    }
                    else -> {
                    }
                }
            }
        }
    }
    val widths = HashMap<Int, Double>()
    var defaultWidth = if (type0) 1000.0 else 500.0
    val descriptor = pdfResolveDict(objects, dict.get("FontDescriptor"))
    val missing = (pdfResolve(objects, descriptor?.get("MissingWidth")) as? PdfNumber)?.value
    if (missing != null && missing > 0) defaultWidth = missing
    val dw = (pdfResolve(objects, dict.get("DW")) as? PdfNumber)?.value
    if (dw != null && dw > 0) defaultWidth = dw
    val firstChar = (pdfResolve(objects, dict.get("FirstChar")) as? PdfNumber)?.asInt(0) ?: 0
    val widthArray = pdfAsArray(pdfResolve(objects, dict.get("Widths")))
    if (widthArray != null) {
        widthArray.items.forEachIndexed { index, item ->
            val number = item as? PdfNumber ?: return@forEachIndexed
            widths[firstChar + index] = number.value
        }
    }
    val wArray = pdfAsArray(pdfResolve(objects, dict.get("W")))
    if (wArray != null) {
        var index = 0
        while (index < wArray.items.size) {
            val start = (wArray.items[index] as? PdfNumber)?.asInt(-1) ?: -1
            if (start < 0) break
            val next = wArray.items.getOrNull(index + 1)
            if (next is PdfArray) {
                next.items.forEachIndexed { offset, item ->
                    val number = item as? PdfNumber ?: return@forEachIndexed
                    widths[start + offset] = number.value
                }
                index += 2
            } else if (next is PdfNumber) {
                val end = next.asInt(start)
                val width = (wArray.items.getOrNull(index + 2) as? PdfNumber)?.value ?: defaultWidth
                var code = start
                while (code <= end && code - start < 65536) {
                    widths[code] = width
                    code++
                }
                index += 3
            } else {
                break
            }
        }
    }
    return PdfFont(type0, codes, differences, widths, defaultWidth, spaces)
}

private fun pdfPageContent(objects: Map<Int, PdfValue>, page: PdfPageNode): String {
    val contents = pdfResolve(objects, page.dict.get("Contents"))
    val chunks = mutableListOf<String>()
    when (contents) {
        is PdfArray -> contents.items.forEach { item ->
            val stream = pdfAsStream(pdfResolve(objects, item)) ?: return@forEach
            chunks.add(String(stream.decoded(), StandardCharsets.ISO_8859_1))
        }
        else -> {
            val stream = pdfAsStream(contents)
            if (stream != null) chunks.add(String(stream.decoded(), StandardCharsets.ISO_8859_1))
        }
    }
    return chunks.joinToString("\n")
}

private class PdfTextState {
    var font: PdfFont? = null
    var size: Double = 12.0
    var charSpacing: Double = 0.0
    var wordSpacing: Double = 0.0
    var scale: Double = 1.0
    var leading: Double = 0.0
    var x: Double = 0.0
    var y: Double = 0.0
    var previousX: Double = Double.NaN
    var previousY: Double = Double.NaN
}

private class PdfLine(var y: Double, var text: String, var endX: Double)

private fun pdfExtractText(document: PdfDocument, page: PdfPageNode): String {
    val content = pdfPageContent(document.objects, page)
    if (content.isEmpty()) return ""
    val fonts = pdfPageFonts(document, page)
    val lines = mutableListOf<PdfLine>()
    val state = PdfTextState()
    val parser = PdfParser(content, 0)
    val operands = mutableListOf<PdfValue>()
    var pending = StringBuilder()
    var guard = 0
    while (guard < 4000000) {
        guard++
        parser.skip()
        if (parser.eof()) break
        val value = parser.parse()
        if (value is PdfOperator) {
            if (value.name == "BI") {
                parser.skipInlineImage()
                operands.clear()
                continue
            }
            try {
                pdfHandleOperator(value.name, operands, state, fonts, lines, pending)
            } catch (e: Exception) {
                pending = StringBuilder()
            }
            operands.clear()
        } else {
            operands.add(value)
        }
    }
    pdfFlushLine(lines, pending, state)
    return lines.joinToString("\n") { it.text }
}

private fun pdfPageFonts(document: PdfDocument, page: PdfPageNode): Map<String, PdfFont> {
    val out = HashMap<String, PdfFont>()
    val fontDict = pdfResolveDict(document.objects, page.resources?.get("Font")) ?: return out
    fontDict.entries.forEach { (key, value) ->
        val dict = pdfResolveDict(document.objects, value) ?: return@forEach
        val font = document.font(dict, System.identityHashCode(dict))
        if (font != null) out[key] = font
    }
    return out
}

private fun pdfHandleOperator(
    name: String,
    operands: List<PdfValue>,
    state: PdfTextState,
    fonts: Map<String, PdfFont>,
    lines: MutableList<PdfLine>,
    pending: StringBuilder
) {
    when (name) {
        "BT" -> {
            pdfFlushLine(lines, pending, state)
            state.x = 0.0
            state.y = 0.0
            state.previousX = Double.NaN
            state.previousY = Double.NaN
        }
        "ET" -> pdfFlushLine(lines, pending, state)
        "Tf" -> {
            val fontName = (operands.getOrNull(0) as? PdfName)?.name
            if (fontName != null) state.font = fonts[fontName]
            val size = (operands.getOrNull(1) as? PdfNumber)?.value
            if (size != null && size > 0) state.size = size
        }
        "Td" -> pdfMove(state, pending, lines, pdfOperand(operands, 0), pdfOperand(operands, 1))
        "TD" -> {
            val ty = pdfOperand(operands, 1)
            state.leading = -ty
            pdfMove(state, pending, lines, pdfOperand(operands, 0), ty)
        }
        "Tm" -> {
            pdfFlushLine(lines, pending, state)
            state.x = pdfOperand(operands, 4)
            state.y = pdfOperand(operands, 5)
            state.previousX = Double.NaN
            state.previousY = Double.NaN
        }
        "T*" -> pdfMove(state, pending, lines, 0.0, -state.leading)
        "TL" -> state.leading = pdfOperand(operands, 0)
        "Tc" -> state.charSpacing = pdfOperand(operands, 0)
        "Tw" -> state.wordSpacing = pdfOperand(operands, 0)
        "Tz" -> {
            val scale = pdfOperand(operands, 0)
            state.scale = if (scale > 0) scale / 100.0 else 1.0
        }
        "Tj" -> pdfShow(operands.getOrNull(0), state, lines, pending)
        "'" -> {
            pdfMove(state, pending, lines, 0.0, -state.leading)
            pdfShow(operands.getOrNull(0), state, lines, pending)
        }
        "\"" -> {
            state.wordSpacing = pdfOperand(operands, 0)
            state.charSpacing = pdfOperand(operands, 1)
            pdfMove(state, pending, lines, 0.0, -state.leading)
            pdfShow(operands.getOrNull(2), state, lines, pending)
        }
        "TJ" -> {
            val array = operands.getOrNull(0) as? PdfArray ?: return
            array.items.forEach { item ->
                when (item) {
                    is PdfString -> pdfShow(item, state, lines, pending)
                    is PdfNumber -> state.x -= item.value / 1000.0 * state.size * state.scale
                    else -> {
                    }
                }
            }
        }
        else -> {
        }
    }
}

private fun pdfOperand(operands: List<PdfValue>, index: Int): Double =
    (operands.getOrNull(index) as? PdfNumber)?.value ?: 0.0

private fun pdfMove(state: PdfTextState, pending: StringBuilder, lines: MutableList<PdfLine>, dx: Double, dy: Double) {
    if (dx != 0.0 || dy != 0.0) pdfFlushLine(lines, pending, state)
    state.x += dx
    state.y += dy
}

private fun pdfFlushLine(lines: MutableList<PdfLine>, pending: StringBuilder, state: PdfTextState) {
    val text = pending.toString().trim()
    pending.setLength(0)
    if (text.isEmpty()) return
    lines.add(PdfLine(state.y, text, state.previousX))
}

private fun pdfShow(value: PdfValue?, state: PdfTextState, lines: MutableList<PdfLine>, pending: StringBuilder) {
    val string = value as? PdfString ?: return
    val font = state.font
    val text = StringBuilder()
    var index = 0
    var advance = 0.0
    while (index < string.size) {
        val length = if (font == null) 1 else font.codeLength(string.bytes, index)
        val code = string.codeAt(index, length)
        text.append(pdfChar(font, code))
        val width = font?.width(code) ?: 500.0
        advance += (width / 1000.0 * state.size + state.charSpacing + if (code == 32) state.wordSpacing else 0.0) * state.scale
        index += length
    }
    val fragment = text.toString()
    if (fragment.isEmpty()) {
        state.x += advance
        return
    }
    if (!state.previousX.isNaN()) {
        val sameLine = Math.abs(state.y - state.previousY) <= maxOf(1.5, state.size * 0.35)
        val gap = state.x - state.previousX
        if (!sameLine || gap < -state.size * 0.5 || (gap > state.size * 1.6 && pending.isNotEmpty())) {
            pdfFlushLine(lines, pending, state)
        } else if (gap > state.size * 0.22 && pending.isNotEmpty() && pending[pending.length - 1] != ' ') {
            pending.append(' ')
        }
    }
    pending.append(fragment)
    state.previousX = state.x + advance
    state.previousY = state.y
    state.x += advance
}

private fun pdfChar(font: PdfFont?, code: Int): String {
    if (font == null) return pdfWinAnsi(code)
    if (font.type0) {
        val mapped = font.codes[code]
        if (mapped != null) return mapped
        return if (code in 0x20..0x7E) code.toChar().toString() else ""
    }
    val mapped = font.codes[code]
    if (mapped != null) return mapped
    val glyph = font.differences[code]
    if (glyph != null) {
        val unicode = pdfGlyphUnicode(glyph)
        if (unicode != null) return unicode
    }
    return pdfWinAnsi(code)
}

private const val PDF_WIN_ANSI_HIGH = "\u20AC\uFFFD\u201A\u0192\u201E\u2026\u2020\u2021\u02C6\u2030\u0160\u2039\u0152\uFFFD\u017D\uFFFD\uFFFD\u2018\u2019\u201C\u201D\u2022\u2013\u2014\u02DC\u2122\u0161\u203A\u0153\uFFFD\u017E\u0178"

private fun pdfWinAnsi(code: Int): String {
    if (code < 0) return ""
    when (code) {
        9, 10, 13 -> return " "
        0xA0 -> return " "
    }
    if (code < 0x20) return ""
    if (code < 0x7F) return code.toChar().toString()
    if (code in 0x80..0x9F) {
        val index = code - 0x80
        val ch = PDF_WIN_ANSI_HIGH[index]
        return if (ch == '\uFFFD') "" else ch.toString()
    }
    return code.toChar().toString()
}

private fun pdfGlyphUnicode(name: String): String? {
    if (name.isEmpty()) return null
    if (name.length == 1) return name
    if (name.startsWith("uni") && name.length >= 7) {
        val value = name.substring(3, 7).toIntOrNull(16) ?: return null
        return value.toChar().toString()
    }
    if (name.startsWith("u") && name.length in 5..7) {
        val value = name.substring(1).toIntOrNull(16) ?: return null
        if (value in 0x20..0xFFFF) return value.toChar().toString()
        return null
    }
    val ascii = pdfGlyphTableLookup(name)
    if (ascii != null) return ascii
    return null
}

private const val PDF_GLYPH_NAMES: String =
    "space=32,exclam=33,quotedbl=34,numbersign=35,dollar=36,percent=37,ampersand=38,quotesingle=39," +
        "parenleft=40,parenright=41,asterisk=42,plus=43,comma=44, hyphen=45,period=46,slash=47," +
        "zero=48,one=49,two=50,three=51,four=52,five=53,six=54,seven=55,eight=56,nine=57," +
        "colon=58,semicolon=59,less=60,equal=61,greater=62,question=63,at=64," +
        "bracketleft=91,backslash=92,bracketright=93,asciicircum=94,underscore=95,grave=96," +
        "braceleft=123,bar=124,braceright=125,asciitilde=126," +
        "quoteright=8217,quoteleft=8216,quotedblleft=8220,quotedblright=8221,quotesinglbase=8218," +
        "endash=8211,emdash=8212,bullet=8226,ellipsis=8230,dagger=8224,daggerdbl=8225,perthousand=8240," +
        "guilsinglleft=8249,guilsinglright=8250,quotedblbase=8222,fi=64257,fl=64258,fraction=8260," +
        "Euro=8364,sterling=163,yen=165,cent=162,copyright=169,registered=174,trademark=8482," +
        "degree=176,plusminus=177,onehalf=189,onequarter=188,threequarters=190,multiply=215,divide=247," +
        "agrave=224,aacute=225,acircumflex=226,atilde=227,adieresis=228,aring=229,ae=230,ccedilla=231," +
        "egrave=232,eacute=233,ecircumflex=234,edieresis=235,igrave=236,iacute=237,icircumflex=238," +
        "idieresis=239,ntilde=241,ograve=242,oacute=243,ocircumflex=244,otilde=245,odieresis=246," +
        "oslash=248,ugrave=249,uacute=250,ucircumflex=251,udieresis=252,yacute=253,thorn=254,ydieresis=255," +
        "Agrave=192,Aacute=193,Acircumflex=194,Atilde=195,Adieresis=196,Aring=197,AE=198,Ccedilla=199," +
        "Egrave=200,Eacute=201,Ecircumflex=202,Edieresis=203,Igrave=204,Iacute=205,Icircumflex=206," +
        "Idieresis=207,Ntilde=209,Ograve=210,Oacute=211,Ocircumflex=212,Otilde=213,Odieresis=214," +
        "Oslash=216,Ugrave=217,Uacute=218,Ucircumflex=219,Udieresis=220,Yacute=221,Thorn=222, germandbls=223"

private val PDF_GLYPH_TABLE: Map<String, String> by lazy {
    val out = HashMap<String, String>()
    PDF_GLYPH_NAMES.split(',').forEach { entry ->
        val at = entry.indexOf('=')
        if (at <= 0) return@forEach
        val key = entry.substring(0, at).trim()
        val value = entry.substring(at + 1).trim().toIntOrNull() ?: return@forEach
        if (key.isNotEmpty()) out[key] = value.toChar().toString()
    }
    out
}

private fun pdfGlyphTableLookup(name: String): String? = PDF_GLYPH_TABLE[name]

private fun pdfInfoValue(objects: Map<Int, PdfValue>, info: PdfDict?, key: String): String {
    val value = pdfResolve(objects, info?.get(key)) ?: return ""
    return when (value) {
        is PdfString -> value.utf16().trim()
        is PdfName -> value.name
        else -> ""
    }
}

private fun pdfPagesLabel(total: Int): String = if (total == 1) "1 page" else "$total pages"

private fun pdfParsePages(spec: String, count: Int): List<Int> {
    val clean = spec.trim()
    if (clean.isEmpty() || clean == "*" || clean.equals("all", ignoreCase = true)) return (1..count).toList()
    val out = LinkedHashSet<Int>()
    clean.split(',', ';').forEach { raw ->
        val part = raw.trim()
        if (part.isEmpty()) return@forEach
        val dash = part.indexOf('-')
        if (dash < 0) {
            val single = part.toIntOrNull() ?: return@forEach
            if (single in 1..count) out.add(single)
        } else {
            val from = part.substring(0, dash).trim().toIntOrNull() ?: 1
            val tail = part.substring(dash + 1).trim()
            val to = if (tail.isEmpty()) count else (tail.toIntOrNull() ?: count)
            var page = maxOf(1, from)
            while (page <= to) {
                if (page in 1..count) out.add(page)
                page++
            }
        }
    }
    return out.sorted()
}

private fun pdfSafeText(document: PdfDocument, page: PdfPageNode): String = try {
    pdfExtractText(document, page)
} catch (e: Exception) {
    ""
}

private const val PDF_PROBLEM = "Cannot read this PDF: "

private fun pdfProblem(file: File, error: Exception): String =
    PDF_PROBLEM + (error.message ?: "${file.name} is damaged")

object PdfBook {

    fun info(file: File): String = try {
        pdfInfo(file)
    } catch (e: Exception) {
        pdfProblem(file, e)
    }

    fun text(file: File, pages: String = "", maxChars: Int = 40000): String = try {
        pdfText(file, pages, maxChars)
    } catch (e: Exception) {
        pdfProblem(file, e)
    }

    fun search(file: File, query: String, maxHits: Int = 40): String = try {
        pdfSearch(file, query, maxHits)
    } catch (e: Exception) {
        pdfProblem(file, e)
    }

    fun pageText(file: File, page: Int): String = try {
        pdfPage(file, page)
    } catch (e: Exception) {
        pdfProblem(file, e)
    }
}

private fun pdfInfo(file: File): String {
    val document = pdfLoad(file)
    val sb = StringBuilder()
    sb.append("File: ").append(file.name).append('\n')
    sb.append("Pages: ").append(document.pages.size).append('\n')
    val title = pdfInfoValue(document.objects, document.info, "Title")
    val author = pdfInfoValue(document.objects, document.info, "Author")
    val producer = pdfInfoValue(document.objects, document.info, "Producer")
    val creator = pdfInfoValue(document.objects, document.info, "Creator")
    val created = pdfInfoValue(document.objects, document.info, "CreationDate")
    if (title.isNotEmpty()) sb.append("Title: ").append(title).append('\n')
    if (author.isNotEmpty()) sb.append("Author: ").append(author).append('\n')
    if (creator.isNotEmpty()) sb.append("Creator: ").append(creator).append('\n')
    if (producer.isNotEmpty()) sb.append("Producer: ").append(producer).append('\n')
    if (created.isNotEmpty()) sb.append("Created: ").append(pdfDate(created)).append('\n')
    sb.append("Encrypted: ").append(if (document.encrypted) "yes (text cannot be extracted)" else "no").append('\n')
    if (document.pages.isNotEmpty()) {
        val box = document.pages[0].mediaBox
        sb.append("Page size: ").append(pdfNumber(box[2] - box[0])).append(" x ")
            .append(pdfNumber(box[3] - box[1])).append(" pt\n")
    }
    return sb.toString().trimEnd()
}

private fun pdfText(file: File, pages: String, maxChars: Int): String {
    val document = pdfLoad(file)
    if (document.encrypted) {
        return "${file.name} is encrypted, so its text cannot be extracted without the password."
    }
    val total = document.pages.size
    if (total == 0) return "${file.name} has no readable pages."
    val selected = pdfParsePages(pages, total)
    if (selected.isEmpty()) return "No pages of ${file.name} matched \"$pages\"."
    val limit = maxChars.coerceIn(400, 400000)
    val sb = StringBuilder()
    sb.append(file.name).append(" (").append(pdfPagesLabel(total)).append(")\n")
    var chars = sb.length
    var truncated = false
    selected.forEach { number ->
        if (truncated) return@forEach
        val pageText = pdfSafeText(document, document.pages[number - 1])
        val block = StringBuilder()
        block.append("\n--- Page ").append(number).append(" ---\n")
        if (pageText.isBlank()) block.append("[no extractable text]\n") else block.append(pageText.trim()).append('\n')
        if (chars + block.length > limit) {
            val room = (limit - chars).coerceAtLeast(0)
            sb.append(block.substring(0, minOf(room, block.length)))
            truncated = true
        } else {
            sb.append(block)
            chars += block.length
        }
    }
    if (truncated) sb.append("\n... truncated at ").append(limit).append(" characters")
    return sb.toString().trimEnd()
}

private fun pdfSearch(file: File, query: String, maxHits: Int): String {
    val document = pdfLoad(file)
    if (document.encrypted) {
        return "${file.name} is encrypted, so it cannot be searched without the password."
    }
    val needle = query.trim()
    if (needle.isEmpty()) return "Give a word or phrase to look for."
    val limit = maxHits.coerceIn(1, 400)
    val sb = StringBuilder()
    sb.append("Search \"").append(needle).append("\" in ").append(file.name)
        .append(" (").append(pdfPagesLabel(document.pages.size)).append(")\n")
    var hits = 0
    var pagesHit = 0
    document.pages.forEachIndexed { index, page ->
        if (hits >= limit) return@forEachIndexed
        val pageText = pdfSafeText(document, page).replace(Regex("\\s+"), " ")
        if (pageText.isEmpty()) return@forEachIndexed
        val lower = pageText.lowercase(Locale.US)
        val target = needle.lowercase(Locale.US)
        var from = lower.indexOf(target)
        if (from < 0) return@forEachIndexed
        pagesHit++
        while (from >= 0 && hits < limit) {
            hits++
            sb.append("p.").append(index + 1).append(": ").append(pdfSnippet(pageText, from, target.length)).append('\n')
            from = lower.indexOf(target, from + target.length)
        }
    }
    if (hits == 0) {
        sb.append("No matches.")
    } else {
        sb.append(hits).append(" hit(s) on ").append(pagesHit).append(" page(s).")
    }
    return sb.toString()
}

private fun pdfPage(file: File, page: Int): String {
    val document = pdfLoad(file)
    if (document.encrypted) return "${file.name} is encrypted, so its text cannot be extracted."
    val total = document.pages.size
    if (total == 0) return "${file.name} has no readable pages."
    if (page < 1 || page > total) return "Page $page is out of range; ${file.name} has ${pdfPagesLabel(total)}."
    val text = pdfSafeText(document, document.pages[page - 1])
    return if (text.isBlank()) "Page $page of ${file.name} has no extractable text." else text.trim()
}

private fun pdfSnippet(text: String, at: Int, length: Int): String {
    val start = maxOf(0, at - 70)
    val end = minOf(text.length, at + length + 70)
    val prefix = if (start > 0) "..." else ""
    val suffix = if (end < text.length) "..." else ""
    return prefix + text.substring(start, end).trim() + suffix
}

private fun pdfNumber(value: Double): String {
    val rounded = Math.round(value * 100.0) / 100.0
    if (rounded == Math.floor(rounded)) return rounded.toLong().toString()
    return String.format(Locale.US, "%.2f", rounded)
}

private fun pdfDate(value: String): String {
    val clean = value.trim().removePrefix("D:")
    if (clean.length < 8) return value.trim()
    val year = clean.substring(0, 4)
    val month = clean.substring(4, 6)
    val day = clean.substring(6, 8)
    val hour = if (clean.length >= 10) clean.substring(8, 10) else "00"
    val minute = if (clean.length >= 12) clean.substring(10, 12) else "00"
    return "$year-$month-$day $hour:$minute"
}

private fun pdfPageCount(file: File): Int = pdfLoad(file).pages.size

private fun pdfPathList(args: JSONObject, key: String): List<String> {
    val value = args.opt(key)
    val raw = mutableListOf<String>()
    when (value) {
        is JSONArray -> for (i in 0 until value.length()) {
            val item = value.optString(i, "").trim()
            if (item.isNotEmpty()) raw.add(item)
        }
        is String -> value.split(',', '\n', ';').forEach { item ->
            val clean = item.trim()
            if (clean.isNotEmpty()) raw.add(clean)
        }
        else -> {
        }
    }
    return raw
}

private fun pdfImageName(source: File, page: Int): String =
    source.nameWithoutExtension + "-p" + page + ".png"

private fun pdfBase64(bytes: ByteArray): String =
    android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

object PdfTools : HarnessGroupTools {

    override val group = HarnessGroup.PDF

    override val tools: List<HarnessTool> = listOf(
        HarnessTool(
            name = "read_pdf",
            group = group,
            permission = HarnessPermission.READ,
            description = "Extract the text of a PDF, page by page, with a hand-written parser that understands " +
                "Flate-compressed content streams and ToUnicode CMaps, so Chinese, Japanese and Korean documents come " +
                "out correctly. Arguments: path, pages (for example \"1-5,9\"), max_chars. Use pdf_info first when you " +
                "only need the metadata or the page count.",
            params = listOf(
                HarnessSchema.text("path", "PDF file to read"),
                HarnessSchema.text("pages", "Pages to read, for example 1-3,7 (default all)", false),
                HarnessSchema.number("max_chars", "Stop after roughly this many characters (default 40000)", false)
            )
        ),
        HarnessTool(
            name = "pdf_info",
            group = group,
            permission = HarnessPermission.READ,
            description = "Report a PDF's page count, page size and document metadata (title, author, creator, " +
                "producer, creation date) and whether the file is encrypted. Cheap; call it before reading a long " +
                "document. Arguments: path.",
            params = listOf(HarnessSchema.text("path", "PDF file to inspect"))
        ),
        HarnessTool(
            name = "pdf_search",
            group = group,
            permission = HarnessPermission.READ,
            description = "Search a PDF for a word or phrase and return the page numbers with a short snippet around " +
                "each hit. Case-insensitive, and it survives line breaks inside the extracted text. Arguments: path, " +
                "query, max_hits.",
            params = listOf(
                HarnessSchema.text("path", "PDF file to search"),
                HarnessSchema.text("query", "Word or phrase to look for"),
                HarnessSchema.number("max_hits", "Stop after this many hits (default 40)", false)
            )
        ),
        HarnessTool(
            name = "render_pdf_page",
            group = group,
            permission = HarnessPermission.READ,
            description = "Draw one PDF page as a PNG so you can look at the layout, and return the image to you. " +
                "Arguments: path, page (1-based), width (pixels, default 1400), out (optional output path; by default " +
                "the PNG is written next to the PDF).",
            params = listOf(
                HarnessSchema.text("path", "PDF file to draw"),
                HarnessSchema.number("page", "Page number, 1-based"),
                HarnessSchema.number("width", "Pixel width of the image", false),
                HarnessSchema.text("out", "Where to write the PNG", false)
            )
        ),
        HarnessTool(
            name = "pdf_to_images",
            group = group,
            permission = HarnessPermission.READ,
            description = "Draw up to 20 pages of a PDF to PNG files next to the document and list the paths. Use it " +
                "to skim a scanned report. Arguments: path, pages, width.",
            params = listOf(
                HarnessSchema.text("path", "PDF file to draw"),
                HarnessSchema.text("pages", "Pages to draw, for example 1-6 (default the first 20)", false),
                HarnessSchema.number("width", "Pixel width of each image", false)
            )
        ),
        HarnessTool(
            name = "merge_pdfs",
            group = group,
            permission = HarnessPermission.WRITE,
            description = "Join several PDFs into one file, in the order given. Arguments: paths (a list or a " +
                "comma-separated string), out. When the platform cannot merge PDFs it says so and you should install " +
                "the python-office plugin and use run_command instead.",
            params = listOf(
                HarnessSchema.list("paths", "PDF files to join, in order"),
                HarnessSchema.text("out", "Output PDF path")
            )
        ),
        HarnessTool(
            name = "split_pdf",
            group = group,
            permission = HarnessPermission.WRITE,
            description = "Write the selected pages of a PDF into a new file. Arguments: path, pages (for example " +
                "\"2-4,9\"), out (optional; by default a new file next to the source). When the platform cannot split " +
                "PDFs it says so and you should install the python-office plugin and use run_command instead.",
            params = listOf(
                HarnessSchema.text("path", "PDF file to split"),
                HarnessSchema.text("pages", "Pages to keep, for example 2-4,9"),
                HarnessSchema.text("out", "Output PDF path", false)
            )
        )
    )

    override suspend fun execute(ctx: HarnessCtx, name: String, args: JSONObject): ToolExecResult? = try {
        when (name) {
            "read_pdf" -> readPdf(ctx, args)
            "pdf_info" -> info(ctx, args)
            "pdf_search" -> search(ctx, args)
            "render_pdf_page" -> renderPage(ctx, args)
            "pdf_to_images" -> toImages(ctx, args)
            "merge_pdfs" -> merge(ctx, args)
            "split_pdf" -> split(ctx, args)
            else -> null
        }
    } catch (e: HarnessError) {
        ToolExecResult(e.message ?: "That path cannot be used", success = false)
    } catch (e: Exception) {
        ToolExecResult("$name failed: ${e.message ?: e::class.simpleName}", success = false)
    }

    private fun readPdf(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val file = Workspace.forRead(ctx, args.optString("path", ""))
        val pages = args.optString("pages", "")
        val maxChars = args.optInt("max_chars", 40000)
        return pdfResult(PdfBook.text(file, pages, maxChars))
    }

    private fun info(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val file = Workspace.forRead(ctx, args.optString("path", ""))
        return pdfResult(PdfBook.info(file))
    }

    private fun search(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val file = Workspace.forRead(ctx, args.optString("path", ""))
        val query = args.optString("query", "")
        val maxHits = args.optInt("max_hits", 40)
        return pdfResult(PdfBook.search(file, query, maxHits))
    }

    private fun pdfResult(text: String): ToolExecResult =
        if (text.startsWith(PDF_PROBLEM)) ToolExecResult(text, success = false) else ToolExecResult(text)

    private suspend fun renderPage(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val file = Workspace.forRead(ctx, args.optString("path", ""))
        val page = args.optInt("page", 1).coerceAtLeast(1)
        val width = args.optInt("width", 1400).coerceIn(120, 4000)
        val host = HarnessRuntime.host
            ?: return ToolExecResult("No platform renderer is available, so the page cannot be drawn.", success = false)
        val bytes = host.renderPdfPage(file.path, page, width)
            ?: return ToolExecResult(
                "This platform cannot draw PDF pages. Install the python-office plugin and use run_command with " +
                    "pdftoppm instead.",
                success = false
            )
        if (bytes.isEmpty()) {
            return ToolExecResult("Page $page of ${file.name} came back empty; it may not exist.", success = false)
        }
        val target = pdfOutputTarget(ctx, args.optString("out", ""), pdfSibling(ctx, file, pdfImageName(file, page)))
        target.parentFile?.mkdirs()
        target.writeBytes(bytes)
        val shown = Workspace.display(ctx, target)
        val image = ToolImage(mime = "image/png", data = pdfBase64(bytes), name = target.name)
        return ToolExecResult(
            "Rendered page $page of ${file.name} to $shown (${Workspace.humanSize(bytes.size.toLong())}, " +
                "${width}px wide). The image is shown to you below.",
            images = listOf(image)
        )
    }

    private suspend fun toImages(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val file = Workspace.forRead(ctx, args.optString("path", ""))
        val width = args.optInt("width", 1200).coerceIn(120, 4000)
        val host = HarnessRuntime.host
            ?: return ToolExecResult("No platform renderer is available, so pages cannot be drawn.", success = false)
        val total = try {
            pdfPageCount(file)
        } catch (e: Exception) {
            0
        }
        val requested = pdfParsePages(args.optString("pages", ""), if (total > 0) total else 20)
        val selected = requested.take(20)
        if (selected.isEmpty()) return ToolExecResult("No pages were selected to draw.", success = false)
        val sb = StringBuilder()
        var drawn = 0
        var failed = 0
        selected.forEach { page ->
            val bytes = host.renderPdfPage(file.path, page, width)
            if (bytes == null || bytes.isEmpty()) {
                failed++
                return@forEach
            }
            val target = pdfSibling(ctx, file, pdfImageName(file, page))
            val written = pdfWritableTarget(ctx, target)
            written.parentFile?.mkdirs()
            written.writeBytes(bytes)
            drawn++
            sb.append(Workspace.display(ctx, written)).append(" (").append(Workspace.humanSize(bytes.size.toLong()))
                .append(")\n")
        }
        if (drawn == 0) {
            return ToolExecResult(
                "This platform could not draw any of the selected pages. Install the python-office plugin and use " +
                    "run_command with pdftoppm instead.",
                success = false
            )
        }
        val note = if (failed > 0) "\n$failed page(s) could not be drawn." else ""
        return ToolExecResult("Drew $drawn page(s) of ${file.name} at ${width}px:\n" + sb.toString().trimEnd() + note)
    }

    private suspend fun merge(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val paths = pdfPathList(args, "paths")
        if (paths.size < 2) return ToolExecResult("Give at least two PDFs to merge.", success = false)
        val inputs = paths.map { Workspace.forRead(ctx, it) }
        val out = Workspace.forWrite(ctx, args.optString("out", ""))
        out.parentFile?.mkdirs()
        if (ctx.config.snapshots && out.exists()) Snapshots.capture(ctx, out)
        val host = HarnessRuntime.host
            ?: return ToolExecResult(pdfPluginHint("merge"), success = false)
        val ok = host.pdfMerge(inputs.map { it.path }, out.path)
        if (!ok) return ToolExecResult(pdfPluginHint("merge"), success = false)
        if (!out.exists()) return ToolExecResult("The PDF merge produced no file at ${out.path}.", success = false)
        return ToolExecResult(
            "Merged ${inputs.size} PDFs into ${Workspace.display(ctx, out)} (${Workspace.humanSize(out.length())}, " +
                "${inputs.joinToString(", ") { it.name }} in that order)."
        )
    }

    private suspend fun split(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val file = Workspace.forRead(ctx, args.optString("path", ""))
        val pages = args.optString("pages", "").trim()
        if (pages.isEmpty()) return ToolExecResult("Give the pages to keep, for example \"2-4,9\".", success = false)
        val raw = args.optString("out", "")
        val out = if (raw.isBlank()) {
            pdfWritableTarget(ctx, pdfSibling(ctx, file, file.nameWithoutExtension + "-pages.pdf"))
        } else {
            Workspace.forWrite(ctx, raw)
        }
        out.parentFile?.mkdirs()
        if (ctx.config.snapshots && out.exists()) Snapshots.capture(ctx, out)
        val host = HarnessRuntime.host
            ?: return ToolExecResult(pdfPluginHint("split"), success = false)
        val ok = host.pdfSplit(file.path, out.path, pages)
        if (!ok) return ToolExecResult(pdfPluginHint("split"), success = false)
        if (!out.exists()) return ToolExecResult("The PDF split produced no file at ${out.path}.", success = false)
        return ToolExecResult(
            "Wrote pages $pages of ${file.name} to ${Workspace.display(ctx, out)} " +
                "(${Workspace.humanSize(out.length())})."
        )
    }

    private fun pdfPluginHint(action: String): String =
        "This platform cannot $action PDFs by itself. Install the python-office plugin (install_plugin) and use " +
            "run_command instead, for example: python3 -c \"import pypdf; ...\"."

    private fun pdfOutputTarget(ctx: HarnessCtx, raw: String, fallback: File): File {
        if (raw.isNotBlank()) return Workspace.forWrite(ctx, raw)
        return pdfWritableTarget(ctx, fallback)
    }

    private fun pdfSibling(ctx: HarnessCtx, file: File, name: String): File =
        File(file.parentFile ?: ctx.workspace, name)

private fun pdfWritableTarget(ctx: HarnessCtx, target: File): File {
        return if (Workspace.writable(ctx, target)) target else File(ctx.workspace, target.name)
    }
}
