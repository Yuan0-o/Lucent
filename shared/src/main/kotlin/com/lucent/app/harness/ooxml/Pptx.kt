package com.lucent.app.harness.ooxml

import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val PPTX_EMU_PER_INCH = 914400.0
private const val PPTX_WIDE_CX = 12192000L
private const val PPTX_WIDE_CY = 6858000L
private const val PPTX_STD_CX = 9144000L
private const val PPTX_STD_CY = 6858000L

private const val PPTX_NS_A = "http://schemas.openxmlformats.org/drawingml/2006/main"
private const val PPTX_NS_P = "http://schemas.openxmlformats.org/presentationml/2006/main"
private const val PPTX_NS_R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
private const val PPTX_NS_C = "http://schemas.openxmlformats.org/drawingml/2006/chart"
private const val PPTX_NS_REL = "http://schemas.openxmlformats.org/package/2006/relationships"
private const val PPTX_NS_CT = "http://schemas.openxmlformats.org/package/2006/content-types"
private const val PPTX_NS_SS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"

private const val PPTX_REL_OD = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

private const val PPTX_CT_PRESENTATION = "application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"
private const val PPTX_CT_SLIDE = "application/vnd.openxmlformats-officedocument.presentationml.slide+xml"
private const val PPTX_CT_LAYOUT = "application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml"
private const val PPTX_CT_MASTER = "application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml"
private const val PPTX_CT_NOTES_MASTER = "application/vnd.openxmlformats-officedocument.presentationml.notesMaster+xml"
private const val PPTX_CT_NOTES_SLIDE = "application/vnd.openxmlformats-officedocument.presentationml.notesSlide+xml"
private const val PPTX_CT_THEME = "application/vnd.openxmlformats-officedocument.theme+xml"
private const val PPTX_CT_PRES_PROPS = "application/vnd.openxmlformats-officedocument.presentationml.presProps+xml"
private const val PPTX_CT_VIEW_PROPS = "application/vnd.openxmlformats-officedocument.presentationml.viewProps+xml"
private const val PPTX_CT_TABLE_STYLES = "application/vnd.openxmlformats-officedocument.presentationml.tableStyles+xml"
private const val PPTX_CT_CHART = "application/vnd.openxmlformats-officedocument.drawingml.chart+xml"
private const val PPTX_CT_CORE = "application/vnd.openxmlformats-package.core-properties+xml"
private const val PPTX_CT_APP = "application/vnd.openxmlformats-officedocument.extended-properties+xml"
private const val PPTX_CT_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
private const val PPTX_CT_RELS = "application/vnd.openxmlformats-package.relationships+xml"

private const val PPTX_TABLE_STYLE_ID = "{5C22544A-7EE6-4342-B048-85BDC9FD1C3A}"

private val PPTX_XML_HEAD = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"

private class PptxTheme(
    var accent1: String = "4472C4",
    var accent2: String = "ED7D31",
    var background: String = "FFFFFF",
    var titleColour: String = "1F3864",
    var bodyColour: String = "404040",
    var font: String = "Calibri"
)

private class PptxSeries(var name: String = "", var values: MutableList<Double> = mutableListOf())

private class PptxImage(
    var path: String = "",
    var x: Double = 0.6,
    var y: Double = 1.6,
    var w: Double = 8.0,
    var h: Double = 4.5,
    var caption: String = "",
    var media: String = ""
)

private class PptxTable(
    var header: MutableList<String> = mutableListOf(),
    var rows: MutableList<MutableList<String>> = mutableListOf(),
    var x: Double = 0.6,
    var y: Double = 1.6,
    var w: Double = 9.0
)

private class PptxChart(
    var type: String = "bar",
    var title: String = "",
    var categories: MutableList<String> = mutableListOf(),
    var series: MutableList<PptxSeries> = mutableListOf(),
    var x: Double = 0.6,
    var y: Double = 1.6,
    var w: Double = 9.0,
    var h: Double = 4.5
)

private class PptxSlide(
    var layout: String = "bullets",
    var title: String = "",
    var subtitle: String = "",
    var bullets: MutableList<String> = mutableListOf(),
    var bulletsRight: MutableList<String> = mutableListOf(),
    var notes: String = "",
    var image: PptxImage? = null,
    var table: PptxTable? = null,
    var chart: PptxChart? = null
)

private class PptxDeck(
    var title: String = "",
    var subtitle: String = "",
    var author: String = "",
    var cx: Long = PPTX_WIDE_CX,
    var cy: Long = PPTX_WIDE_CY,
    var theme: PptxTheme = PptxTheme(),
    val slides: MutableList<PptxSlide> = mutableListOf()
)

private class PptxMetrics(val cx: Long, val cy: Long) {
    val wide: Boolean = cx >= 11000000L
    val margin: Long = if (wide) 548640L else 457200L
    val titleX: Long = margin
    val titleY: Long = if (wide) 320040L else 274320L
    val titleW: Long = cx - margin * 2
    val titleH: Long = if (wide) 914400L else 822960L
    val bodyX: Long = margin
    val bodyY: Long = if (wide) 1371600L else 1188720L
    val bodyW: Long = cx - margin * 2
    val bodyH: Long = cy - bodyY - (if (wide) 548640L else 457200L)
    val halfW: Long = (bodyW - 274320L) / 2
    val gap: Long = 274320L
    val titleSize: Int = if (wide) 3200 else 3000
    val bodySize: Int = if (wide) 1800 else 1600
}

private class PptxRelBuilder {
    private val rows = StringBuilder()
    private var seq = 1

    fun add(type: String, target: String): String {
        val id = "rId${seq}"
        seq++
        rows.append("<Relationship Id=\"").append(id).append("\" Type=\"").append(type)
            .append("\" Target=\"").append(pptxAtt(target)).append("\"/>")
        return id
    }

    fun document(): String = "<Relationships xmlns=\"" + PPTX_NS_REL + "\">" + rows + "</Relationships>"
}

private class PptxBuilder(val deck: PptxDeck, val existingMedia: Map<String, ByteArray>) {

    val entries = LinkedHashMap<String, ByteArray>()
    private val overrides = LinkedHashMap<String, String>()
    private val defaults = LinkedHashMap<String, String>()
    private val presentationRels = PptxRelBuilder()
    private var chartSeq = 0
    private var imageSeq = 0
    private val metrics = PptxMetrics(deck.cx, deck.cy)

    fun build(): ByteArray {
        defaults["rels"] = PPTX_CT_RELS
        defaults["xml"] = "application/xml"
        defaults["png"] = "image/png"
        defaults["jpeg"] = "image/jpeg"
        defaults["jpg"] = "image/jpeg"
        defaults["gif"] = "image/gif"
        defaults["xlsx"] = PPTX_CT_XLSX

        presentationRels.add(PPTX_REL_OD + "/slideMaster", "slideMasters/slideMaster1.xml")
        presentationRels.add(PPTX_REL_OD + "/notesMaster", "notesMasters/notesMaster1.xml")

        val slideIds = StringBuilder()
        var notesCount = 0
        deck.slides.forEachIndexed { index, slide ->
            val number = index + 1
            val rels = PptxRelBuilder()
            rels.add(PPTX_REL_OD + "/slideLayout", "../slideLayouts/slideLayout" + pptxLayoutIndex(slide.layout) + ".xml")
            val shapes = pptxShapeTree(this, slide, rels)
            val notesTarget = "../notesSlides/notesSlide$number.xml"
            val hasNotes = slide.notes.isNotBlank()
            if (hasNotes) rels.add(PPTX_REL_OD + "/notesSlide", notesTarget)
            val slidePart = "ppt/slides/slide$number.xml"
            entries[slidePart] = pptxXml(pptxSlideXml(shapes))
            overrides["/ppt/slides/slide$number.xml"] = PPTX_CT_SLIDE
            entries["ppt/slides/_rels/slide$number.xml.rels"] = pptxXml(rels.document())
            if (hasNotes) {
                notesCount++
                val notesRels = PptxRelBuilder()
                notesRels.add(PPTX_REL_OD + "/slide", "../slides/slide$number.xml")
                notesRels.add(PPTX_REL_OD + "/notesMaster", "../notesMasters/notesMaster1.xml")
                entries["ppt/notesSlides/notesSlide$number.xml"] = pptxXml(pptxNotesSlide(slide, deck.theme.font))
                overrides["/ppt/notesSlides/notesSlide$number.xml"] = PPTX_CT_NOTES_SLIDE
                entries["ppt/notesSlides/_rels/notesSlide$number.xml.rels"] = pptxXml(notesRels.document())
            }
            val id = presentationRels.add(PPTX_REL_OD + "/slide", "slides/slide$number.xml")
            slideIds.append("<p:sldId id=\"").append(255 + number).append("\" r:id=\"").append(id).append("\"/>")
        }

        entries["ppt/presentation.xml"] = pptxXml(pptxPresentationXml(slideIds.toString(), deck))
        overrides["/ppt/presentation.xml"] = PPTX_CT_PRESENTATION

        entries["ppt/presProps.xml"] = pptxXml(pptxPresProps())
        overrides["/ppt/presProps.xml"] = PPTX_CT_PRES_PROPS
        entries["ppt/viewProps.xml"] = pptxXml(pptxViewProps())
        overrides["/ppt/viewProps.xml"] = PPTX_CT_VIEW_PROPS
        entries["ppt/tableStyles.xml"] = pptxXml(pptxTableStyles())
        overrides["/ppt/tableStyles.xml"] = PPTX_CT_TABLE_STYLES

        entries["ppt/theme/theme1.xml"] = pptxXml(pptxThemeXml(deck.theme))
        overrides["/ppt/theme/theme1.xml"] = PPTX_CT_THEME

        val masterRels = PptxRelBuilder()
        for (i in 1..5) masterRels.add(PPTX_REL_OD + "/slideLayout", "../slideLayouts/slideLayout$i.xml")
        masterRels.add(PPTX_REL_OD + "/theme", "../theme/theme1.xml")
        entries["ppt/slideMasters/slideMaster1.xml"] = pptxXml(pptxMasterXml(deck, metrics))
        overrides["/ppt/slideMasters/slideMaster1.xml"] = PPTX_CT_MASTER
        entries["ppt/slideMasters/_rels/slideMaster1.xml.rels"] = pptxXml(masterRels.document())

        for (i in 1..5) {
            val layoutRels = PptxRelBuilder()
            layoutRels.add(PPTX_REL_OD + "/slideMaster", "../slideMasters/slideMaster1.xml")
            entries["ppt/slideLayouts/slideLayout$i.xml"] = pptxXml(pptxLayoutXml(i, deck, metrics))
            overrides["/ppt/slideLayouts/slideLayout$i.xml"] = PPTX_CT_LAYOUT
            entries["ppt/slideLayouts/_rels/slideLayout$i.xml.rels"] = pptxXml(layoutRels.document())
        }

        val notesMasterRels = PptxRelBuilder()
        notesMasterRels.add(PPTX_REL_OD + "/theme", "../theme/theme1.xml")
        notesMasterRels.add(PPTX_REL_OD + "/slideMaster", "../slideMasters/slideMaster1.xml")
        entries["ppt/notesMasters/notesMaster1.xml"] = pptxXml(pptxNotesMasterXml(deck, metrics))
        overrides["/ppt/notesMasters/notesMaster1.xml"] = PPTX_CT_NOTES_MASTER
        entries["ppt/notesMasters/_rels/notesMaster1.xml.rels"] = pptxXml(notesMasterRels.document())

        presentationRels.add(PPTX_REL_OD + "/presProps", "presProps.xml")
        presentationRels.add(PPTX_REL_OD + "/viewProps", "viewProps.xml")
        presentationRels.add(PPTX_REL_OD + "/theme", "theme/theme1.xml")
        presentationRels.add(PPTX_REL_OD + "/tableStyles", "tableStyles.xml")
        entries["ppt/_rels/presentation.xml.rels"] = pptxXml(presentationRels.document())

        entries["docProps/core.xml"] = pptxXml(pptxCoreXml(deck))
        overrides["/docProps/core.xml"] = PPTX_CT_CORE
        entries["docProps/app.xml"] = pptxXml(pptxAppXml(deck, notesCount))
        overrides["/docProps/app.xml"] = PPTX_CT_APP

        val rootRels = PptxRelBuilder()
        rootRels.add(PPTX_REL_OD + "/officeDocument", "ppt/presentation.xml")
        rootRels.add("http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties", "docProps/core.xml")
        rootRels.add(PPTX_REL_OD + "/extended-properties", "docProps/app.xml")
        entries["_rels/.rels"] = pptxXml(rootRels.document())

        entries["[Content_Types].xml"] = pptxXml(pptxContentTypes())
        return Ooxml.zipBytes(entries.map { it.key to it.value })
    }

    fun nextChart(): Int {
        chartSeq++
        return chartSeq
    }

    fun nextImage(): Int {
        imageSeq++
        return imageSeq
    }

    fun override(part: String, type: String) {
        overrides["/$part"] = type
    }

    fun put(part: String, body: String) {
        entries[part] = pptxXml(body)
    }

    fun putBytes(part: String, bytes: ByteArray) {
        entries[part] = bytes
    }

    fun media(name: String): ByteArray? = existingMedia[name]

    private fun pptxContentTypes(): String {
        val sb = StringBuilder()
        sb.append("<Types xmlns=\"").append(PPTX_NS_CT).append("\">")
        defaults.forEach { (ext, type) ->
            sb.append("<Default Extension=\"").append(ext).append("\" ContentType=\"").append(type).append("\"/>")
        }
        overrides.forEach { (part, type) ->
            sb.append("<Override PartName=\"").append(part).append("\" ContentType=\"").append(type).append("\"/>")
        }
        sb.append("</Types>")
        return sb.toString()
    }
}

object Pptx {

    fun create(specJson: String, out: File): String {
        val spec = try {
            JSONObject(specJson)
        } catch (e: Exception) {
            throw IllegalArgumentException("The deck specification is not valid JSON: ${e.message}")
        }
        val deck = pptxDeckFromSpec(spec)
        if (deck.slides.isEmpty()) throw IllegalArgumentException("The deck needs at least one slide")
        val bytes = PptxBuilder(deck, emptyMap()).build()
        out.parentFile?.mkdirs()
        out.writeBytes(bytes)
        return "${out.name}: ${deck.slides.size} slide(s), ${pptxSizeLabel(deck.cx, deck.cy)}, ${pptxHuman(bytes.size.toLong())}"
    }

    fun read(file: File, maxChars: Int = 20000): String {
        val entries = pptxEntries(file)
        val deck = pptxDeckFromEntries(entries, file)
        val limit = maxChars.coerceIn(500, 400000)
        val sb = StringBuilder()
        sb.append("Presentation: ").append(deck.title.ifBlank { file.name }).append('\n')
        sb.append("Slides: ").append(deck.slides.size).append('\n')
        sb.append("Size: ").append(pptxSizeLabel(deck.cx, deck.cy))
            .append(" (").append(deck.cx).append(" x ").append(deck.cy).append(" EMU)\n")
        deck.slides.forEachIndexed { index, slide ->
            sb.append("\n# Slide ").append(index + 1).append('\n')
            pptxDescribeSlide(sb, slide)
        }
        val text = sb.toString()
        return if (text.length <= limit) text else text.take(limit) + "\n... truncated at $limit characters"
    }

    fun edit(file: File, opsJson: String): String {
        val entries = pptxEntries(file)
        val deck = pptxDeckFromEntries(entries, file)
        val media = LinkedHashMap<String, ByteArray>()
        entries.forEach { (name, bytes) ->
            if (name.startsWith("ppt/media/")) media[name.removePrefix("ppt/media/")] = bytes
        }
        val ops = pptxOps(opsJson)
        if (ops.isEmpty()) throw IllegalArgumentException("No operations were given")
        val log = mutableListOf<String>()
        ops.forEachIndexed { index, op ->
            val note = pptxApplyOp(deck, op)
            log.add("${index + 1}. $note")
        }
        if (deck.slides.isEmpty()) throw IllegalArgumentException("The deck would end up with no slides")
        val bytes = PptxBuilder(deck, media).build()
        file.writeBytes(bytes)
        return "${file.name}: ${deck.slides.size} slide(s), ${pptxHuman(bytes.size.toLong())}\n" + log.joinToString("\n")
    }
}

private fun pptxEntries(file: File): Map<String, ByteArray> {
    if (!file.exists()) throw IllegalArgumentException("${file.name} does not exist")
    if (file.isDirectory) throw IllegalArgumentException("${file.name} is a directory, not a presentation")
    if (file.length() > 256L * 1024 * 1024) throw IllegalArgumentException("${file.name} is too large to read")
    val entries = try {
        Ooxml.readZip(file)
    } catch (e: Exception) {
        throw IllegalArgumentException("${file.name} is not a readable .pptx package: ${e.message}")
    }
    if (!entries.containsKey("ppt/presentation.xml")) {
        throw IllegalArgumentException("${file.name} has no ppt/presentation.xml, so it is not a PowerPoint presentation")
    }
    return entries
}

private fun pptxOps(opsJson: String): List<JSONObject> {
    val trimmed = opsJson.trim()
    if (trimmed.isEmpty()) return emptyList()
    val parsed = try {
        if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed)
    } catch (e: Exception) {
        throw IllegalArgumentException("The operations are not valid JSON: ${e.message}")
    }
    val array = when (parsed) {
        is JSONArray -> parsed
        is JSONObject -> parsed.optJSONArray("ops") ?: JSONArray().put(parsed)
        else -> JSONArray()
    }
    val out = mutableListOf<JSONObject>()
    for (i in 0 until array.length()) {
        val item = array.optJSONObject(i)
        if (item != null) out.add(item)
    }
    return out
}

private fun pptxDeckFromSpec(spec: JSONObject): PptxDeck {
    val deck = PptxDeck()
    deck.title = spec.optString("title", "").trim()
    deck.subtitle = spec.optString("subtitle", "").trim()
    deck.author = spec.optString("author", "").trim()
    val size = pptxSizeKey(spec.optString("size", "16:9"))
    if (size == "4:3") {
        deck.cx = PPTX_STD_CX
        deck.cy = PPTX_STD_CY
    }
    val theme = spec.optJSONObject("theme")
    if (theme != null) {
        deck.theme = PptxTheme(
            accent1 = pptxColour(theme.optString("accent1", ""), deck.theme.accent1),
            accent2 = pptxColour(theme.optString("accent2", ""), deck.theme.accent2),
            background = pptxColour(theme.optString("background", ""), deck.theme.background),
            titleColour = pptxColour(theme.optString("title_colour", ""), deck.theme.titleColour),
            bodyColour = pptxColour(theme.optString("body_colour", ""), deck.theme.bodyColour),
            font = theme.optString("font", deck.theme.font).trim().ifEmpty { deck.theme.font }
        )
    }
    val slides = spec.optJSONArray("slides")
    if (slides != null && slides.length() > 0) {
        for (i in 0 until slides.length()) {
            val item = slides.optJSONObject(i) ?: continue
            deck.slides.add(pptxSlideFromJson(item, "bullets"))
        }
    }
    val markdown = spec.optString("content", "").ifBlank { spec.optString("markdown", "") }
    if (markdown.isNotBlank()) pptxDeckFromMarkdown(deck, markdown)
    return deck
}

private fun pptxSlideFromJson(json: JSONObject, fallbackLayout: String): PptxSlide {
    val slide = PptxSlide()
    slide.layout = pptxLayoutKey(json.optString("layout", fallbackLayout))
    slide.title = json.optString("title", "").trim()
    slide.subtitle = json.optString("subtitle", "").trim()
    slide.notes = json.optString("notes", "").trim()
    slide.bullets = pptxStrings(json.opt("bullets"))
    slide.bulletsRight = pptxStrings(json.opt("bullets_right"))
    val image = json.optJSONObject("image")
    if (image != null) slide.image = pptxImageFromJson(image)
    val table = json.optJSONObject("table")
    if (table != null) slide.table = pptxTableFromJson(table)
    val chart = json.optJSONObject("chart")
    if (chart != null) slide.chart = pptxChartFromJson(chart)
    return slide
}

private fun pptxImageFromJson(json: JSONObject): PptxImage = PptxImage(
    path = json.optString("path", "").trim(),
    x = json.optDouble("x", 0.6),
    y = json.optDouble("y", 1.6),
    w = json.optDouble("w", 8.0),
    h = json.optDouble("h", 4.5),
    caption = json.optString("caption", "").trim()
)

private fun pptxTableFromJson(json: JSONObject): PptxTable {
    val table = PptxTable(
        x = json.optDouble("x", 0.6),
        y = json.optDouble("y", 1.6),
        w = json.optDouble("w", 9.0)
    )
    table.header = pptxStrings(json.opt("header"))
    val rows = json.optJSONArray("rows")
    if (rows != null) {
        for (i in 0 until rows.length()) {
            val row = rows.optJSONArray(i) ?: continue
            val cells = mutableListOf<String>()
            for (c in 0 until row.length()) cells.add(row.optString(c, ""))
            table.rows.add(cells)
        }
    }
    return table
}

private fun pptxChartFromJson(json: JSONObject): PptxChart {
    val chart = PptxChart(
        type = pptxChartType(json.optString("type", "bar")),
        title = json.optString("title", "").trim(),
        categories = pptxStrings(json.opt("categories")),
        x = json.optDouble("x", 0.6),
        y = json.optDouble("y", 1.6),
        w = json.optDouble("w", 9.0),
        h = json.optDouble("h", 4.5)
    )
    val series = json.optJSONArray("series")
    if (series != null) {
        for (i in 0 until series.length()) {
            val item = series.optJSONObject(i) ?: continue
            val name = item.optString("name", "").trim()
            chart.series.add(
                PptxSeries(
                    name.ifEmpty { "Series ${i + 1}" },
                    pptxNumbers(item.optJSONArray("values"))
                )
            )
        }
    }
    return chart
}

private fun pptxDeckFromMarkdown(deck: PptxDeck, markdown: String) {
    val blocks = try {
        SimpleMarkdown.blocks(markdown)
    } catch (e: Exception) {
        emptyList()
    }
    if (blocks.isEmpty()) return
    val titleSlide = PptxSlide(layout = "title", title = deck.title, subtitle = deck.subtitle)
    var usedTitle = false
    var current: PptxSlide? = null
    blocks.forEach { block ->
        val text = block.text.trim()
        if (block.level > 0 && text.isNotEmpty()) {
            if (!usedTitle && block.level == 1) {
                usedTitle = true
                if (deck.title.isBlank()) deck.title = text
                titleSlide.title = deck.title
                deck.slides.add(titleSlide)
                current = titleSlide
            } else {
                val slide = PptxSlide(layout = if (block.level >= 2) "section" else "bullets", title = text)
                deck.slides.add(slide)
                current = slide
            }
            return@forEach
        }
        if (block.rows.isNotEmpty()) {
            val table = PptxTable()
            table.header = block.rows.first().map { it.trim() }.toMutableList()
            block.rows.drop(1).forEach { row -> table.rows.add(row.map { it.trim() }.toMutableList()) }
            val slide = PptxSlide(layout = "table", title = current?.title ?: "")
            slide.table = table
            deck.slides.add(slide)
            return@forEach
        }
        val items = if (block.items.isNotEmpty()) {
            block.items.map { it.trim() }.filter { it.isNotEmpty() }
        } else if (text.isNotEmpty()) {
            listOf(text)
        } else {
            emptyList()
        }
        if (items.isEmpty()) return@forEach
        val target = current ?: PptxSlide(layout = "bullets").also {
            deck.slides.add(it)
            current = it
        }
        target.bullets.addAll(items)
    }
    if (deck.slides.isEmpty()) {
        deck.slides.add(titleSlide)
    } else if (deck.slides.first() !== titleSlide && (titleSlide.title.isNotBlank() || titleSlide.subtitle.isNotBlank())) {
        deck.slides.add(0, titleSlide)
    }
}

private fun pptxStrings(value: Any?): MutableList<String> {
    val out = mutableListOf<String>()
    when (value) {
        is JSONArray -> {
            for (i in 0 until value.length()) {
                val text = value.optString(i, "").trim()
                if (text.isNotEmpty()) out.add(text)
            }
        }
        is String -> value.split("\n").forEach { line ->
            val text = line.trim()
            if (text.isNotEmpty()) out.add(text)
        }
        else -> {
        }
    }
    return out
}

private fun pptxNumbers(array: JSONArray?): MutableList<Double> {
    val out = mutableListOf<Double>()
    if (array == null) return out
    for (i in 0 until array.length()) out.add(array.optDouble(i, 0.0))
    return out
}

private fun pptxShapeTree(builder: PptxBuilder, slide: PptxSlide, rels: PptxRelBuilder): String {
    val deck = builder.deck
    val metrics = PptxMetrics(deck.cx, deck.cy)
    val theme = deck.theme
    val sb = StringBuilder()
    var id = 2
    val titleSlide = slide.layout == "title"

    if (titleSlide) {
        val titleWidth = metrics.cx - metrics.margin * 2
        sb.append(
            pptxPlaceholder(
                id, "Title 1", "ctrTitle", 0,
                metrics.margin, pptxEmu(2.0), titleWidth, pptxEmu(1.6),
                if (slide.title.isBlank()) emptyList() else listOf(slide.title),
                4400, theme.titleColour, theme.font, "ctr", "ctr", false, true
            )
        )
        id++
        val subtitle = when {
            slide.subtitle.isNotBlank() -> listOf(slide.subtitle)
            else -> emptyList()
        }
        sb.append(
            pptxPlaceholder(
                id, "Subtitle 2", "subTitle", 1,
                metrics.margin, pptxEmu(3.7), titleWidth, pptxEmu(1.2),
                subtitle, 2000, theme.bodyColour, theme.font, "ctr", "t", false, false
            )
        )
        id++
        if (slide.bullets.isNotEmpty()) {
            sb.append(
                pptxTextBox(
                    id, "Title Slide Bullets", metrics.margin, pptxEmu(4.4), titleWidth, pptxEmu(1.6),
                    slide.bullets, 1600, theme.bodyColour, theme.font, "ctr", true
                )
            )
            id++
        }
    } else {
        val titleY = if (slide.layout == "section") pptxEmu(2.2) else metrics.titleY
        val titleH = if (slide.layout == "section") pptxEmu(1.2) else metrics.titleH
        val titleSize = if (slide.layout == "section") 4000 else metrics.titleSize
        if (slide.title.isNotBlank() || slide.layout != "blank") {
            sb.append(
                pptxPlaceholder(
                    id, "Title 1", "title", 0,
                    metrics.titleX, titleY, metrics.titleW, titleH,
                    if (slide.title.isBlank()) emptyList() else listOf(slide.title),
                    titleSize, theme.titleColour, theme.font, "l", "b", false, true
                )
            )
            id++
        }

        val bodyY = if (slide.layout == "section") pptxEmu(3.6) else metrics.bodyY
        val bodyH = if (slide.layout == "section") pptxEmu(2.0) else metrics.bodyH
        if (slide.layout == "two_content") {
            sb.append(
                pptxPlaceholder(
                    id, "Content Placeholder 2", "body", 1,
                    metrics.bodyX, metrics.bodyY, metrics.halfW, metrics.bodyH,
                    slide.bullets, metrics.bodySize, theme.bodyColour, theme.font, "l", "t", true, false
                )
            )
            id++
            sb.append(
                pptxPlaceholder(
                    id, "Content Placeholder 3", "body", 2,
                    metrics.bodyX + metrics.halfW + metrics.gap, metrics.bodyY, metrics.halfW, metrics.bodyH,
                    slide.bulletsRight, metrics.bodySize, theme.bodyColour, theme.font, "l", "t", true, false
                )
            )
            id++
        } else if (slide.bullets.isNotEmpty()) {
            sb.append(
                pptxPlaceholder(
                    id, "Content Placeholder 2", "body", 1,
                    metrics.bodyX, bodyY, metrics.bodyW, bodyH,
                    slide.bullets, metrics.bodySize, theme.bodyColour, theme.font, "l", "t", true, false
                )
            )
            id++
        } else if (slide.layout == "bullets" && slide.image == null && slide.table == null && slide.chart == null) {
            sb.append(
                pptxPlaceholder(
                    id, "Content Placeholder 2", "body", 1,
                    metrics.bodyX, bodyY, metrics.bodyW, bodyH,
                    emptyList(), metrics.bodySize, theme.bodyColour, theme.font, "l", "t", true, false
                )
            )
            id++
        }
    }

    val image = slide.image
    if (image != null) {
        val partName = pptxImagePart(builder, image)
        val rid = rels.add(PPTX_REL_OD + "/image", "../media/" + partName)
        sb.append(
            pptxPicture(
                id, "Picture $id", image, rid,
                pptxEmu(image.x), pptxEmu(image.y), pptxEmu(image.w), pptxEmu(image.h)
            )
        )
        id++
        if (image.caption.isNotBlank()) {
            sb.append(
                pptxTextBox(
                    id, "Caption", pptxEmu(image.x), pptxEmu(image.y + image.h + 0.06),
                    pptxEmu(image.w), pptxEmu(0.34), listOf(image.caption),
                    1200, theme.bodyColour, theme.font, "ctr", false
                )
            )
            id++
        }
    }

    val table = slide.table
    if (table != null && (table.header.isNotEmpty() || table.rows.isNotEmpty())) {
        sb.append(pptxTableFrame(id, "Table $id", table, theme))
        id++
    }

    val chart = slide.chart
    if (chart != null) {
        val number = builder.nextChart()
        val chartPart = "ppt/charts/chart$number.xml"
        builder.put(chartPart, pptxChartXml(chart, theme))
        builder.override(chartPart, PPTX_CT_CHART)
        val chartRels = PptxRelBuilder()
        chartRels.add(PPTX_REL_OD + "/package", "../embeddings/Microsoft_Excel_Worksheet$number.xlsx")
        builder.put("ppt/charts/_rels/chart$number.xml.rels", chartRels.document())
        builder.putBytes("ppt/embeddings/Microsoft_Excel_Worksheet$number.xlsx", pptxChartWorkbook(chart))
        val rid = rels.add(PPTX_REL_OD + "/chart", "../charts/chart$number.xml")
        sb.append(
            pptxChartFrame(
                id, "Chart $id", rid,
                pptxEmu(chart.x), pptxEmu(chart.y), pptxEmu(chart.w), pptxEmu(chart.h)
            )
        )
        id++
    }
    return sb.toString()
}

private fun pptxImagePart(builder: PptxBuilder, image: PptxImage): String {
    val bytes = if (image.media.isNotEmpty()) {
        builder.media(image.media) ?: throw IllegalArgumentException("The image part ${image.media} is missing from the presentation")
    } else {
        val file = File(image.path)
        if (!file.exists() || file.isDirectory) throw IllegalArgumentException("Image not found: ${image.path}")
        if (file.length() > 64L * 1024 * 1024) throw IllegalArgumentException("${file.name} is too large to embed")
        file.readBytes()
    }
    val extension = pptxImageExtension(bytes, image.path)
    val number = builder.nextImage()
    val name = "image$number.$extension"
    builder.putBytes("ppt/media/$name", bytes)
    return name
}

private fun pptxSlideXml(shapes: String): String {
    val sb = StringBuilder()
    sb.append("<p:sld xmlns:a=\"").append(PPTX_NS_A).append("\" xmlns:r=\"").append(PPTX_NS_R)
        .append("\" xmlns:p=\"").append(PPTX_NS_P).append("\">")
    sb.append("<p:cSld>")
    sb.append("<p:spTree>").append(pptxGroupShape()).append(shapes).append("</p:spTree>")
    sb.append("</p:cSld>")
    sb.append("<p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr>")
    sb.append("</p:sld>")
    return sb.toString()
}

private fun pptxNotesSlide(slide: PptxSlide, font: String): String {
    val paragraphs = pptxStrings(slide.notes)
    val sb = StringBuilder()
    sb.append("<p:notes xmlns:a=\"").append(PPTX_NS_A).append("\" xmlns:r=\"").append(PPTX_NS_R)
        .append("\" xmlns:p=\"").append(PPTX_NS_P).append("\">")
    sb.append("<p:cSld><p:spTree>").append(pptxGroupShape())
    sb.append("<p:sp><p:nvSpPr><p:cNvPr id=\"2\" name=\"Notes Placeholder 1\"/>")
    sb.append("<p:cNvSpPr><a:spLocks noGrp=\"1\"/></p:cNvSpPr>")
    sb.append("<p:nvPr><p:ph type=\"body\" idx=\"1\"/></p:nvPr></p:nvSpPr>")
    sb.append("<p:spPr/>")
    sb.append("<p:txBody><a:bodyPr/><a:lstStyle/>")
    if (paragraphs.isEmpty()) {
        sb.append("<a:p/>")
    } else {
        paragraphs.forEach { line -> sb.append(pptxPara(listOf(line), 0, 1200, "000000", font, false, "l")) }
    }
    sb.append("</p:txBody></p:sp>")
    sb.append("</p:spTree></p:cSld>")
    sb.append("<p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr>")
    sb.append("</p:notes>")
    return sb.toString()
}

private fun pptxGroupShape(): String =
    "<p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>" +
        "<p:grpSpPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/>" +
        "<a:chOff x=\"0\" y=\"0\"/><a:chExt cx=\"0\" cy=\"0\"/></a:xfrm></p:grpSpPr>"

private fun pptxPlaceholder(
    id: Int,
    name: String,
    phType: String,
    phIdx: Int,
    x: Long,
    y: Long,
    cx: Long,
    cy: Long,
    paragraphs: List<String>,
    size: Int,
    colour: String,
    font: String,
    align: String,
    anchor: String,
    bullet: Boolean,
    bold: Boolean
): String {
    val ph = "<p:ph type=\"" + phType + "\"" + (if (phIdx > 0) " idx=\"$phIdx\"" else "") + "/>"
    return pptxShapeXml(id, name, ph, x, y, cx, cy, paragraphs, size, colour, font, align, anchor, bullet, bold)
}

private fun pptxTextBox(
    id: Int,
    name: String,
    x: Long,
    y: Long,
    cx: Long,
    cy: Long,
    paragraphs: List<String>,
    size: Int,
    colour: String,
    font: String,
    align: String,
    bullet: Boolean
): String {
    return pptxShapeXml(id, name, null, x, y, cx, cy, paragraphs, size, colour, font, align, "t", bullet, false)
}

private fun pptxShapeXml(
    id: Int,
    name: String,
    ph: String?,
    x: Long,
    y: Long,
    cx: Long,
    cy: Long,
    paragraphs: List<String>,
    size: Int,
    colour: String,
    font: String,
    align: String,
    anchor: String,
    bullet: Boolean,
    bold: Boolean
): String {
    val sb = StringBuilder()
    sb.append("<p:sp><p:nvSpPr><p:cNvPr id=\"").append(id).append("\" name=\"").append(pptxAtt(name)).append("\"/>")
    if (ph == null) {
        sb.append("<p:cNvSpPr txBox=\"1\"/>")
    } else {
        sb.append("<p:cNvSpPr><a:spLocks noGrp=\"1\"/></p:cNvSpPr>")
    }
    sb.append("<p:nvPr>").append(ph ?: "").append("</p:nvPr></p:nvSpPr>")
    sb.append("<p:spPr><a:xfrm><a:off x=\"").append(x).append("\" y=\"").append(y)
        .append("\"/><a:ext cx=\"").append(cx).append("\" cy=\"").append(cy).append("\"/></a:xfrm>")
    sb.append("<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom>")
    if (ph == null) sb.append("<a:noFill/>")
    sb.append("</p:spPr>")
    sb.append("<p:txBody><a:bodyPr wrap=\"square\" anchor=\"").append(anchor).append("\"><a:normAutofit/></a:bodyPr><a:lstStyle/>")
    if (paragraphs.isEmpty()) {
        sb.append(pptxPara(emptyList(), 0, size, colour, font, bullet, align))
    } else {
        paragraphs.forEach { text ->
            sb.append(pptxPara(listOf(text), 0, size, colour, font, bullet, align, bold))
        }
    }
    sb.append("</p:txBody></p:sp>")
    return sb.toString()
}

private fun pptxPara(
    lines: List<String>,
    level: Int,
    size: Int,
    colour: String,
    font: String,
    bullet: Boolean,
    align: String,
    bold: Boolean = false
): String {
    val sb = StringBuilder("<a:p>")
    if (bullet) {
        val indent = 342900L * (level + 1)
        sb.append("<a:pPr lvl=\"").append(level).append("\" marL=\"").append(indent)
            .append("\" indent=\"").append(-342900L)
            .append("\" algn=\"").append(align).append("\">")
        sb.append("<a:buFont typeface=\"Arial\"/>")
        sb.append(if (level == 0) "<a:buChar char=\"&#8226;\"/>" else "<a:buChar char=\"&#8211;\"/>")
        sb.append("</a:pPr>")
    } else if (align != "l" || level > 0) {
        sb.append("<a:pPr lvl=\"").append(level).append("\" algn=\"").append(align).append("\"/>")
    }
    if (lines.isEmpty()) {
        sb.append("</a:p>")
        return sb.toString()
    }
    lines.forEach { text ->
        val parts = text.split("\n")
        parts.forEachIndexed { index, part ->
            if (index > 0) sb.append("<a:br/>")
            if (part.isEmpty()) return@forEachIndexed
            sb.append("<a:r><a:rPr lang=\"en-US\" sz=\"").append(size)
                .append("\" b=\"").append(if (bold) "1" else "0").append("\" dirty=\"0\">")
            sb.append("<a:solidFill><a:srgbClr val=\"").append(pptxColour(colour, "000000")).append("\"/></a:solidFill>")
            sb.append("<a:latin typeface=\"").append(pptxAtt(font)).append("\"/>")
            sb.append("<a:cs typeface=\"").append(pptxAtt(font)).append("\"/></a:rPr>")
            sb.append("<a:t").append(if (part != part.trim()) " xml:space=\"preserve\"" else "").append(">")
            sb.append(pptxEsc(part)).append("</a:t></a:r>")
        }
    }
    sb.append("</a:p>")
    return sb.toString()
}

private fun pptxPicture(id: Int, name: String, image: PptxImage, rid: String, x: Long, y: Long, cx: Long, cy: Long): String {
    val sb = StringBuilder()
    sb.append("<p:pic><p:nvPicPr><p:cNvPr id=\"").append(id).append("\" name=\"").append(pptxAtt(name)).append("\"")
    if (image.caption.isNotBlank()) sb.append(" descr=\"").append(pptxAtt(image.caption)).append("\"")
    sb.append("/><p:cNvPicPr><a:picLocks noChangeAspect=\"1\"/></p:cNvPicPr><p:nvPr/></p:nvPicPr>")
    sb.append("<p:blipFill><a:blip r:embed=\"").append(rid).append("\"/><a:stretch><a:fillRect/></a:stretch></p:blipFill>")
    sb.append("<p:spPr><a:xfrm><a:off x=\"").append(x).append("\" y=\"").append(y)
        .append("\"/><a:ext cx=\"").append(cx).append("\" cy=\"").append(cy).append("\"/></a:xfrm>")
    sb.append("<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></p:spPr></p:pic>")
    return sb.toString()
}

private fun pptxTableFrame(id: Int, name: String, table: PptxTable, theme: PptxTheme): String {
    val columns = maxOf(
        table.header.size,
        table.rows.maxOfOrNull { it.size } ?: 0,
        1
    )
    val totalCx = pptxEmu(table.w)
    val columnCx = totalCx / columns
    val headerCx = totalCx - columnCx * (columns - 1)
    val rowCy = pptxEmu(0.42)
    val rows = table.rows.size + if (table.header.isEmpty()) 0 else 1
    val totalCy = rowCy * rows
    val sb = StringBuilder()
    sb.append("<p:graphicFrame><p:nvGraphicFramePr><p:cNvPr id=\"").append(id).append("\" name=\"")
        .append(pptxAtt(name)).append("\"/>")
    sb.append("<p:cNvGraphicFramePr><a:graphicFrameLocks noGrp=\"1\"/></p:cNvGraphicFramePr><p:nvPr/></p:nvGraphicFramePr>")
    sb.append("<p:xfrm><a:off x=\"").append(pptxEmu(table.x)).append("\" y=\"").append(pptxEmu(table.y))
        .append("\"/><a:ext cx=\"").append(totalCx).append("\" cy=\"").append(totalCy).append("\"/></p:xfrm>")
    sb.append("<a:graphic><a:graphicData uri=\"").append(PPTX_NS_A).append("/table\">")
    sb.append("<a:tbl><a:tblPr firstRow=\"1\" bandRow=\"1\"><a:tableStyleId>")
        .append(PPTX_TABLE_STYLE_ID).append("</a:tableStyleId></a:tblPr>")
    sb.append("<a:tblGrid>")
    for (c in 0 until columns) {
        sb.append("<a:gridCol w=\"").append(if (c == 0) headerCx else columnCx).append("\"/>")
    }
    sb.append("</a:tblGrid>")
    if (table.header.isNotEmpty()) {
        sb.append("<a:tr h=\"").append(rowCy).append("\">")
        for (c in 0 until columns) {
            sb.append(pptxTableCell(table.header.getOrElse(c) { "" }, true, theme))
        }
        sb.append("</a:tr>")
    }
    table.rows.forEachIndexed { index, row ->
        sb.append("<a:tr h=\"").append(rowCy).append("\">")
        for (c in 0 until columns) {
            sb.append(pptxTableCell(row.getOrElse(c) { "" }, false, theme, index))
        }
        sb.append("</a:tr>")
    }
    sb.append("</a:tbl></a:graphicData></a:graphic></p:graphicFrame>")
    return sb.toString()
}

private fun pptxTableCell(
    text: String,
    header: Boolean,
    theme: PptxTheme,
    rowIndex: Int = 0
): String {
    val fill = if (header) {
        theme.accent1
    } else if (rowIndex % 2 == 0) {
        "FFFFFF"
    } else {
        "F2F2F2"
    }
    val colour = if (header) "FFFFFF" else theme.bodyColour
    val size = if (header) 1400 else 1300
    val sb = StringBuilder("<a:tc><a:txBody><a:bodyPr/><a:lstStyle/>")
    sb.append(pptxPara(listOf(text), 0, size, colour, theme.font, false, "l", header))
    sb.append("</a:txBody><a:tcPr marL=\"91440\" marR=\"91440\" marT=\"45720\" marB=\"45720\" anchor=\"ctr\">")
    sb.append(pptxCellBorder("lnL")).append(pptxCellBorder("lnR"))
        .append(pptxCellBorder("lnT")).append(pptxCellBorder("lnB"))
    sb.append("<a:solidFill><a:srgbClr val=\"").append(fill).append("\"/></a:solidFill>")
    sb.append("</a:tcPr></a:tc>")
    return sb.toString()
}

private fun pptxCellBorder(tag: String): String =
    "<a:$tag w=\"12700\" cap=\"flat\" cmpd=\"sng\" algn=\"ctr\"><a:solidFill><a:srgbClr val=\"BFBFBF\"/>" +
        "</a:solidFill><a:prstDash val=\"solid\"/></a:$tag>"

private fun pptxChartFrame(id: Int, name: String, rid: String, x: Long, y: Long, cx: Long, cy: Long): String {
    val sb = StringBuilder()
    sb.append("<p:graphicFrame><p:nvGraphicFramePr><p:cNvPr id=\"").append(id).append("\" name=\"")
        .append(pptxAtt(name)).append("\"/>")
    sb.append("<p:cNvGraphicFramePr><a:graphicFrameLocks noGrp=\"1\"/></p:cNvGraphicFramePr><p:nvPr/></p:nvGraphicFramePr>")
    sb.append("<p:xfrm><a:off x=\"").append(x).append("\" y=\"").append(y)
        .append("\"/><a:ext cx=\"").append(cx).append("\" cy=\"").append(cy).append("\"/></p:xfrm>")
    sb.append("<a:graphic><a:graphicData uri=\"").append(PPTX_NS_C).append("\">")
    sb.append("<c:chart xmlns:c=\"").append(PPTX_NS_C).append("\" xmlns:r=\"").append(PPTX_NS_R)
        .append("\" r:id=\"").append(rid).append("\"/></a:graphicData></a:graphic></p:graphicFrame>")
    return sb.toString()
}

private fun pptxChartXml(chart: PptxChart, theme: PptxTheme): String {
    val sb = StringBuilder()
    sb.append("<c:chartSpace xmlns:c=\"").append(PPTX_NS_C).append("\" xmlns:a=\"").append(PPTX_NS_A)
        .append("\" xmlns:r=\"").append(PPTX_NS_R).append("\">")
    sb.append("<c:date1904 val=\"0\"/><c:lang val=\"en-US\"/><c:roundedCorners val=\"0\"/>")
    sb.append("<c:chart>")
    if (chart.title.isBlank()) {
        sb.append("<c:autoTitleDeleted val=\"1\"/>")
    } else {
        sb.append("<c:title><c:tx><c:rich><a:bodyPr/><a:lstStyle/><a:p>")
        sb.append("<a:pPr><a:defRPr sz=\"1400\" b=\"1\"/></a:pPr>")
        sb.append("<a:r><a:rPr lang=\"en-US\"/><a:t>").append(pptxEsc(chart.title)).append("</a:t></a:r>")
        sb.append("</a:p></c:rich></c:tx><c:overlay val=\"0\"/></c:title>")
        sb.append("<c:autoTitleDeleted val=\"0\"/>")
    }
    sb.append("<c:plotArea><c:layout/>")
    val accents = pptxAccents(theme)
    val categories = pptxCategories(chart)
    when (chart.type) {
        "pie" -> sb.append(pptxPieChart(chart, accents, categories, false))
        "doughnut" -> sb.append(pptxPieChart(chart, accents, categories, true))
        "line" -> sb.append(pptxLineChart(chart, accents, categories))
        "area" -> sb.append(pptxAreaChart(chart, accents, categories))
        else -> sb.append(pptxBarChart(chart, accents, categories))
    }
    sb.append("<c:spPr><a:noFill/><a:ln><a:noFill/></a:ln></c:spPr>")
    sb.append("<c:plotVisOnly val=\"1\"/><c:dispBlanksAs val=\"gap\"/></c:plotArea>")
    sb.append("<c:legend><c:legendPos val=\"b\"/><c:overlay val=\"0\"/></c:legend>")
    sb.append("<c:plotVisOnly val=\"1\"/><c:dispBlanksAs val=\"gap\"/></c:chart>")
    sb.append("<c:externalData r:id=\"rId1\"><c:autoUpdate val=\"0\"/></c:externalData>")
    sb.append("</c:chartSpace>")
    return sb.toString()
}

private fun pptxBarChart(chart: PptxChart, accents: List<String>, categories: List<String>): String {
    val sb = StringBuilder("<c:barChart><c:barDir val=\"col\"/><c:grouping val=\"clustered\"/><c:varyColors val=\"0\"/>")
    chart.series.forEachIndexed { index, series ->
        sb.append(pptxSeries(index, series, accents[index % accents.size], categories, 2 + index, false))
    }
    sb.append("<c:gapWidth val=\"150\"/><c:overlap val=\"-27\"/>")
    sb.append("<c:axId val=\"111111111\"/><c:axId val=\"222222222\"/></c:barChart>")
    sb.append(pptxCategoryAxis())
    sb.append(pptxValueAxis())
    return sb.toString()
}

private fun pptxLineChart(chart: PptxChart, accents: List<String>, categories: List<String>): String {
    val sb = StringBuilder("<c:lineChart><c:grouping val=\"standard\"/><c:varyColors val=\"0\"/>")
    chart.series.forEachIndexed { index, series ->
        sb.append(pptxSeries(index, series, accents[index % accents.size], categories, 2 + index, true))
    }
    sb.append("<c:marker val=\"1\"/>")
    sb.append("<c:axId val=\"111111111\"/><c:axId val=\"222222222\"/></c:lineChart>")
    sb.append(pptxCategoryAxis())
    sb.append(pptxValueAxis())
    return sb.toString()
}

private fun pptxAreaChart(chart: PptxChart, accents: List<String>, categories: List<String>): String {
    val sb = StringBuilder("<c:areaChart><c:grouping val=\"standard\"/><c:varyColors val=\"0\"/>")
    chart.series.forEachIndexed { index, series ->
        sb.append(pptxSeries(index, series, accents[index % accents.size], categories, 2 + index, false))
    }
    sb.append("<c:axId val=\"111111111\"/><c:axId val=\"222222222\"/></c:areaChart>")
    sb.append(pptxCategoryAxis())
    sb.append(pptxValueAxis())
    return sb.toString()
}

private fun pptxPieChart(chart: PptxChart, accents: List<String>, categories: List<String>, doughnut: Boolean): String {
    val tag = if (doughnut) "doughnutChart" else "pieChart"
    val sb = StringBuilder("<c:").append(tag).append("><c:varyColors val=\"1\"/>")
    chart.series.forEachIndexed { index, series ->
        sb.append("<c:ser><c:idx val=\"").append(index).append("\"/><c:order val=\"").append(index).append("\"/>")
        sb.append("<c:tx><c:strRef><c:f>Sheet1!\$").append(pptxColumn(2 + index)).append("\$1</c:f>")
        sb.append("<c:strCache><c:ptCount val=\"1\"/><c:pt idx=\"0\"><c:v>").append(pptxEsc(series.name))
            .append("</c:v></c:pt></c:strCache></c:strRef></c:tx>")
        sb.append("<c:spPr><a:solidFill><a:srgbClr val=\"").append(accents[index % accents.size])
            .append("\"/></a:solidFill></c:spPr>")
        series.values.forEachIndexed { point, _ ->
            sb.append("<c:dPt><c:idx val=\"").append(point).append("\"/><c:bubble3D val=\"0\"/>")
            sb.append("<c:spPr><a:solidFill><a:srgbClr val=\"").append(accents[point % accents.size])
                .append("\"/></a:solidFill></c:spPr></c:dPt>")
        }
        sb.append(pptxCategoriesXml(categories))
        sb.append(pptxValuesXml(series, 2 + index))
        sb.append("</c:ser>")
    }
    if (doughnut) {
        sb.append("<c:firstSliceAng val=\"0\"/><c:holeSize val=\"50\"/>")
    } else {
        sb.append("<c:firstSliceAng val=\"0\"/>")
    }
    sb.append("</c:").append(tag).append(">")
    return sb.toString()
}

private fun pptxSeries(
    index: Int,
    series: PptxSeries,
    colour: String,
    categories: List<String>,
    column: Int,
    line: Boolean
): String {
    val sb = StringBuilder("<c:ser>")
    sb.append("<c:idx val=\"").append(index).append("\"/><c:order val=\"").append(index).append("\"/>")
    sb.append("<c:tx><c:strRef><c:f>Sheet1!\$").append(pptxColumn(column)).append("\$1</c:f>")
    sb.append("<c:strCache><c:ptCount val=\"1\"/><c:pt idx=\"0\"><c:v>").append(pptxEsc(series.name))
        .append("</c:v></c:pt></c:strCache></c:strRef></c:tx>")
    if (line) {
        sb.append("<c:spPr><a:ln w=\"28575\" cap=\"rnd\"><a:solidFill><a:srgbClr val=\"").append(colour)
            .append("\"/></a:solidFill><a:round/></a:ln><a:effectLst/></c:spPr>")
        sb.append("<c:marker><c:symbol val=\"circle\"/><c:size val=\"5\"/></c:marker>")
    } else {
        sb.append("<c:spPr><a:solidFill><a:srgbClr val=\"").append(colour)
            .append("\"/></a:solidFill><a:ln><a:noFill/></a:ln><a:effectLst/></c:spPr>")
    }
    sb.append(pptxCategoriesXml(categories))
    sb.append(pptxValuesXml(series, column))
    if (line) sb.append("<c:smooth val=\"0\"/>")
    sb.append("</c:ser>")
    return sb.toString()
}

private fun pptxCategoriesXml(categories: List<String>): String {
    if (categories.isEmpty()) return ""
    val sb = StringBuilder("<c:cat><c:strRef><c:f>Sheet1!\$A\$2:\$A\$")
        .append(categories.size + 1).append("</c:f><c:strCache><c:ptCount val=\"")
        .append(categories.size).append("\"/>")
    categories.forEachIndexed { index, value ->
        sb.append("<c:pt idx=\"").append(index).append("\"><c:v>").append(pptxEsc(value)).append("</c:v></c:pt>")
    }
    sb.append("</c:strCache></c:strRef></c:cat>")
    return sb.toString()
}

private fun pptxValuesXml(series: PptxSeries, column: Int): String {
    val values = series.values
    val sb = StringBuilder("<c:val><c:numRef><c:f>Sheet1!\$").append(pptxColumn(column)).append("\$2:\$")
        .append(pptxColumn(column)).append("\$").append(values.size + 1).append("</c:f><c:numCache>")
    sb.append("<c:formatCode>General</c:formatCode><c:ptCount val=\"").append(values.size).append("\"/>")
    values.forEachIndexed { index, value ->
        sb.append("<c:pt idx=\"").append(index).append("\"><c:v>").append(pptxNum(value)).append("</c:v></c:pt>")
    }
    sb.append("</c:numCache></c:numRef></c:val>")
    return sb.toString()
}

private fun pptxCategoryAxis(): String =
    "<c:catAx><c:axId val=\"111111111\"/><c:scaling><c:orientation val=\"minMax\"/></c:scaling>" +
        "<c:delete val=\"0\"/><c:axPos val=\"b\"/><c:tickLblPos val=\"nextTo\"/>" +
        "<c:crossAx val=\"222222222\"/><c:crosses val=\"autoZero\"/><c:auto val=\"1\"/>" +
        "<c:lblAlgn val=\"ctr\"/><c:lblOffset val=\"100\"/><c:noMultiLvlLbl val=\"0\"/></c:catAx>"

private fun pptxValueAxis(): String =
    "<c:valAx><c:axId val=\"222222222\"/><c:scaling><c:orientation val=\"minMax\"/></c:scaling>" +
        "<c:delete val=\"0\"/><c:axPos val=\"l\"/><c:majorGridlines/>" +
        "<c:numFmt formatCode=\"General\" sourceLinked=\"1\"/><c:tickLblPos val=\"nextTo\"/>" +
        "<c:crossAx val=\"111111111\"/><c:crosses val=\"autoZero\"/><c:crossBetween val=\"between\"/></c:valAx>"

private fun pptxAccents(theme: PptxTheme): List<String> =
    listOf(theme.accent1, theme.accent2, "A5A5A5", "FFC000", "5B9BD5", "70AD47")

private fun pptxCategories(chart: PptxChart): List<String> {
    if (chart.categories.isNotEmpty()) return chart.categories
    val count = chart.series.maxOfOrNull { it.values.size } ?: 0
    return (1..count).map { "Item $it" }
}

private fun pptxChartWorkbook(chart: PptxChart): ByteArray {
    val categories = pptxCategories(chart)
    val rows = mutableListOf<List<String>>()
    val header = mutableListOf("")
    chart.series.forEach { header.add(it.name) }
    rows.add(header)
    val count = maxOf(categories.size, chart.series.maxOfOrNull { it.values.size } ?: 0)
    for (i in 0 until count) {
        val row = mutableListOf(categories.getOrElse(i) { "Item ${i + 1}" })
        chart.series.forEach { series -> row.add(pptxNum(series.values.getOrElse(i) { 0.0 })) }
        rows.add(row)
    }
    val sheet = StringBuilder()
    sheet.append("<worksheet xmlns=\"").append(PPTX_NS_SS).append("\">")
    val lastColumn = pptxColumn(maxOf(chart.series.size, 1))
    sheet.append("<dimension ref=\"A1:").append(lastColumn).append(rows.size).append("\"/>")
    sheet.append("<sheetData>")
    rows.forEachIndexed { index, row ->
        val rowNumber = index + 1
        sheet.append("<row r=\"").append(rowNumber).append("\">")
        row.forEachIndexed { column, value ->
            val ref = pptxColumn(column + 1) + rowNumber
            if (index == 0 || column == 0) {
                sheet.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t>")
                    .append(pptxEsc(value)).append("</t></is></c>")
            } else {
                sheet.append("<c r=\"").append(ref).append("\"><v>").append(pptxNumText(value))
                    .append("</v></c>")
            }
        }
        sheet.append("</row>")
    }
    sheet.append("</sheetData></worksheet>")

    val contentTypes = "<Types xmlns=\"" + PPTX_NS_CT + "\">" +
        "<Default Extension=\"rels\" ContentType=\"" + PPTX_CT_RELS + "\"/>" +
        "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
        "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
        "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
        "</Types>"
    val rootRels = "<Relationships xmlns=\"" + PPTX_NS_REL + "\">" +
        "<Relationship Id=\"rId1\" Type=\"" + PPTX_REL_OD + "/officeDocument\" Target=\"xl/workbook.xml\"/>" +
        "</Relationships>"
    val workbook = "<workbook xmlns=\"" + PPTX_NS_SS + "\" xmlns:r=\"" + PPTX_NS_R + "\">" +
        "<sheets><sheet name=\"Sheet1\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>"
    val workbookRels = "<Relationships xmlns=\"" + PPTX_NS_REL + "\">" +
        "<Relationship Id=\"rId1\" Type=\"" + PPTX_REL_OD + "/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
        "</Relationships>"
    val parts = listOf(
        "[Content_Types].xml" to pptxXml(contentTypes),
        "_rels/.rels" to pptxXml(rootRels),
        "xl/workbook.xml" to pptxXml(workbook),
        "xl/_rels/workbook.xml.rels" to pptxXml(workbookRels),
        "xl/worksheets/sheet1.xml" to pptxXml(sheet.toString())
    )
    return Ooxml.zipBytes(parts)
}

private fun pptxPresentationXml(slideIds: String, deck: PptxDeck): String {
    val sb = StringBuilder()
    sb.append("<p:presentation xmlns:a=\"").append(PPTX_NS_A).append("\" xmlns:r=\"").append(PPTX_NS_R)
        .append("\" xmlns:p=\"").append(PPTX_NS_P).append("\" saveSubsetFonts=\"1\">")
    sb.append("<p:sldMasterIdLst><p:sldMasterId id=\"2147483648\" r:id=\"rId1\"/></p:sldMasterIdLst>")
    sb.append("<p:notesMasterIdLst><p:notesMasterId r:id=\"rId2\"/></p:notesMasterIdLst>")
    sb.append("<p:sldIdLst>").append(slideIds).append("</p:sldIdLst>")
    sb.append("<p:sldSz cx=\"").append(deck.cx).append("\" cy=\"").append(deck.cy).append("\"/>")
    sb.append("<p:notesSz cx=\"").append(PPTX_STD_CY).append("\" cy=\"").append(PPTX_STD_CX).append("\"/>")
    sb.append("<p:defaultTextStyle>")
    for (level in 1..9) {
        sb.append("<a:lvl").append(level).append("pPr marL=\"").append(342900L * level)
            .append("\" indent=\"-342900\" algn=\"l\">")
        sb.append("<a:defRPr sz=\"").append(if (level == 1) 1800 else 1600).append("\" b=\"0\"/>")
        sb.append("</a:lvl").append(level).append("pPr>")
    }
    sb.append("</p:defaultTextStyle></p:presentation>")
    return sb.toString()
}

private fun pptxPresProps(): String =
    "<p:presentationPr xmlns:a=\"" + PPTX_NS_A + "\" xmlns:r=\"" + PPTX_NS_R + "\" xmlns:p=\"" + PPTX_NS_P + "\"/>"

private fun pptxViewProps(): String =
    "<p:viewPr xmlns:a=\"" + PPTX_NS_A + "\" xmlns:r=\"" + PPTX_NS_R + "\" xmlns:p=\"" + PPTX_NS_P +
        "\" lastView=\"sldView\"><p:normalViewPr><p:restoredLeft sz=\"15620\"/><p:restoredTop sz=\"94660\"/>" +
        "</p:normalViewPr><p:slideViewPr><p:cSldViewPr><p:cViewPr varScale=\"1\"><p:scale>" +
        "<a:sx n=\"100\" d=\"100\"/><a:sy n=\"100\" d=\"100\"/></p:scale><p:origin x=\"0\" y=\"0\"/>" +
        "</p:cViewPr><p:guideLst/></p:cSldViewPr></p:slideViewPr><p:notesTextViewPr><p:cViewPr>" +
        "<p:scale><a:sx n=\"100\" d=\"100\"/><a:sy n=\"100\" d=\"100\"/></p:scale><p:origin x=\"0\" y=\"0\"/>" +
        "</p:cViewPr></p:notesTextViewPr><p:gridSpacing cx=\"76200\" cy=\"76200\"/></p:viewPr>"

private fun pptxTableStyles(): String =
    "<a:tblStyleLst xmlns:a=\"" + PPTX_NS_A + "\" def=\"" + PPTX_TABLE_STYLE_ID + "\"/>"

private fun pptxThemeXml(theme: PptxTheme): String {
    val major = if (theme.font.equals("Calibri", ignoreCase = true)) "Calibri Light" else theme.font
    val sb = StringBuilder()
    sb.append("<a:theme xmlns:a=\"").append(PPTX_NS_A).append("\" name=\"Lucent\"><a:themeElements>")
    sb.append("<a:clrScheme name=\"Lucent\">")
    sb.append("<a:dk1><a:sysClr val=\"windowText\" lastClr=\"000000\"/></a:dk1>")
    sb.append("<a:lt1><a:srgbClr val=\"").append(theme.background).append("\"/></a:lt1>")
    sb.append("<a:dk2><a:srgbClr val=\"").append(theme.titleColour).append("\"/></a:dk2>")
    sb.append("<a:lt2><a:srgbClr val=\"EEECE1\"/></a:lt2>")
    sb.append("<a:accent1><a:srgbClr val=\"").append(theme.accent1).append("\"/></a:accent1>")
    sb.append("<a:accent2><a:srgbClr val=\"").append(theme.accent2).append("\"/></a:accent2>")
    sb.append("<a:accent3><a:srgbClr val=\"A5A5A5\"/></a:accent3>")
    sb.append("<a:accent4><a:srgbClr val=\"FFC000\"/></a:accent4>")
    sb.append("<a:accent5><a:srgbClr val=\"5B9BD5\"/></a:accent5>")
    sb.append("<a:accent6><a:srgbClr val=\"70AD47\"/></a:accent6>")
    sb.append("<a:hlink><a:srgbClr val=\"0563C1\"/></a:hlink>")
    sb.append("<a:folHlink><a:srgbClr val=\"954F72\"/></a:folHlink>")
    sb.append("</a:clrScheme>")
    sb.append("<a:fontScheme name=\"Lucent\">")
    sb.append("<a:majorFont><a:latin typeface=\"").append(pptxAtt(major))
        .append("\"/><a:ea typeface=\"\"/><a:cs typeface=\"\"/></a:majorFont>")
    sb.append("<a:minorFont><a:latin typeface=\"").append(pptxAtt(theme.font))
        .append("\"/><a:ea typeface=\"\"/><a:cs typeface=\"\"/></a:minorFont>")
    sb.append("</a:fontScheme>")
    sb.append("<a:fmtScheme name=\"Lucent\">")
    sb.append("<a:fillStyleLst>")
    sb.append("<a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill>")
    sb.append("<a:gradFill rotWithShape=\"1\"><a:gsLst>")
    sb.append("<a:gs pos=\"0\"><a:schemeClr val=\"phClr\"><a:lumMod val=\"110000\"/><a:satMod val=\"105000\"/><a:tint val=\"67000\"/></a:schemeClr></a:gs>")
    sb.append("<a:gs pos=\"50000\"><a:schemeClr val=\"phClr\"><a:lumMod val=\"105000\"/><a:satMod val=\"103000\"/><a:tint val=\"73000\"/></a:schemeClr></a:gs>")
    sb.append("<a:gs pos=\"100000\"><a:schemeClr val=\"phClr\"><a:lumMod val=\"105000\"/><a:satMod val=\"109000\"/><a:tint val=\"81000\"/></a:schemeClr></a:gs>")
    sb.append("</a:gsLst><a:lin ang=\"5400000\" scaled=\"0\"/></a:gradFill>")
    sb.append("<a:gradFill rotWithShape=\"1\"><a:gsLst>")
    sb.append("<a:gs pos=\"0\"><a:schemeClr val=\"phClr\"><a:satMod val=\"103000\"/><a:lumMod val=\"102000\"/><a:tint val=\"94000\"/></a:schemeClr></a:gs>")
    sb.append("<a:gs pos=\"50000\"><a:schemeClr val=\"phClr\"><a:satMod val=\"110000\"/><a:lumMod val=\"100000\"/><a:shade val=\"100000\"/></a:schemeClr></a:gs>")
    sb.append("<a:gs pos=\"100000\"><a:schemeClr val=\"phClr\"><a:lumMod val=\"99000\"/><a:satMod val=\"120000\"/><a:shade val=\"78000\"/></a:schemeClr></a:gs>")
    sb.append("</a:gsLst><a:lin ang=\"5400000\" scaled=\"0\"/></a:gradFill>")
    sb.append("</a:fillStyleLst>")
    sb.append("<a:lnStyleLst>")
    sb.append("<a:ln w=\"6350\" cap=\"flat\" cmpd=\"sng\" algn=\"ctr\"><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill><a:prstDash val=\"solid\"/><a:miter lim=\"800000\"/></a:ln>")
    sb.append("<a:ln w=\"12700\" cap=\"flat\" cmpd=\"sng\" algn=\"ctr\"><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill><a:prstDash val=\"solid\"/><a:miter lim=\"800000\"/></a:ln>")
    sb.append("<a:ln w=\"19050\" cap=\"flat\" cmpd=\"sng\" algn=\"ctr\"><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill><a:prstDash val=\"solid\"/><a:miter lim=\"800000\"/></a:ln>")
    sb.append("</a:lnStyleLst>")
    sb.append("<a:effectStyleLst>")
    sb.append("<a:effectStyle><a:effectLst/></a:effectStyle>")
    sb.append("<a:effectStyle><a:effectLst/></a:effectStyle>")
    sb.append("<a:effectStyle><a:effectLst><a:outerShdw blurRad=\"57150\" dist=\"19050\" dir=\"5400000\" algn=\"ctr\" rotWithShape=\"0\">")
    sb.append("<a:srgbClr val=\"000000\"><a:alpha val=\"63000\"/></a:srgbClr></a:outerShdw></a:effectLst></a:effectStyle>")
    sb.append("</a:effectStyleLst>")
    sb.append("<a:bgFillStyleLst>")
    sb.append("<a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill>")
    sb.append("<a:solidFill><a:schemeClr val=\"phClr\"><a:tint val=\"95000\"/><a:satMod val=\"170000\"/></a:schemeClr></a:solidFill>")
    sb.append("<a:gradFill rotWithShape=\"1\"><a:gsLst>")
    sb.append("<a:gs pos=\"0\"><a:schemeClr val=\"phClr\"><a:tint val=\"93000\"/><a:satMod val=\"150000\"/><a:lumMod val=\"98000\"/></a:schemeClr></a:gs>")
    sb.append("<a:gs pos=\"50000\"><a:schemeClr val=\"phClr\"><a:tint val=\"98000\"/><a:satMod val=\"130000\"/><a:lumMod val=\"90000\"/></a:schemeClr></a:gs>")
    sb.append("<a:gs pos=\"100000\"><a:schemeClr val=\"phClr\"><a:shade val=\"63000\"/><a:satMod val=\"120000\"/><a:lumMod val=\"80000\"/></a:schemeClr></a:gs>")
    sb.append("</a:gsLst><a:path path=\"circle\"><a:fillToRect l=\"50000\" t=\"-80000\" r=\"50000\" b=\"180000\"/></a:path></a:gradFill>")
    sb.append("</a:bgFillStyleLst>")
    sb.append("</a:fmtScheme></a:themeElements><a:objectDefaults/><a:extraClrSchemeLst/></a:theme>")
    return sb.toString()
}

private fun pptxMasterXml(deck: PptxDeck, metrics: PptxMetrics): String {
    val theme = deck.theme
    val sb = StringBuilder()
    sb.append("<p:sldMaster xmlns:a=\"").append(PPTX_NS_A).append("\" xmlns:r=\"").append(PPTX_NS_R)
        .append("\" xmlns:p=\"").append(PPTX_NS_P).append("\">")
    sb.append("<p:cSld><p:bg><p:bgPr><a:solidFill><a:srgbClr val=\"").append(theme.background)
        .append("\"/></a:solidFill><a:effectLst/></p:bgPr></p:bg>")
    sb.append("<p:spTree>").append(pptxGroupShape())
    sb.append(
        pptxPlaceholder(
            2, "Title Placeholder 1", "title", 0,
            metrics.titleX, metrics.titleY, metrics.titleW, metrics.titleH,
            emptyList(), metrics.titleSize, theme.titleColour, theme.font, "l", "b", false, false
        )
    )
    sb.append(
        pptxPlaceholder(
            3, "Text Placeholder 2", "body", 1,
            metrics.bodyX, metrics.bodyY, metrics.bodyW, metrics.bodyH,
            emptyList(), metrics.bodySize, theme.bodyColour, theme.font, "l", "t", true, false
        )
    )
    sb.append("</p:spTree></p:cSld>")
    sb.append("<p:clrMap bg1=\"lt1\" tx1=\"dk1\" bg2=\"lt2\" tx2=\"dk2\" accent1=\"accent1\" accent2=\"accent2\" ")
    sb.append("accent3=\"accent3\" accent4=\"accent4\" accent5=\"accent5\" accent6=\"accent6\" hlink=\"hlink\" folHlink=\"folHlink\"/>")
    sb.append("<p:sldLayoutIdLst>")
    for (i in 1..5) sb.append("<p:sldLayoutId id=\"").append(2147483648L + i).append("\" r:id=\"rId").append(i).append("\"/>")
    sb.append("</p:sldLayoutIdLst>")
    sb.append("<p:txStyles>")
    sb.append("<p:titleStyle><a:lvl1pPr algn=\"l\" rtl=\"0\"><a:defRPr sz=\"").append(metrics.titleSize)
        .append("\" b=\"0\"><a:solidFill><a:srgbClr val=\"").append(theme.titleColour)
        .append("\"/></a:solidFill><a:latin typeface=\"").append(pptxAtt(theme.font))
        .append("\"/></a:defRPr></a:lvl1pPr></p:titleStyle>")
    sb.append("<p:bodyStyle>")
    for (level in 1..5) {
        sb.append("<a:lvl").append(level).append("pPr marL=\"").append(342900L * level)
            .append("\" indent=\"-342900\"><a:buFont typeface=\"Arial\"/>")
        sb.append(if (level == 1) "<a:buChar char=\"&#8226;\"/>" else "<a:buChar char=\"&#8211;\"/>")
        sb.append("<a:defRPr sz=\"").append(if (level == 1) metrics.bodySize else metrics.bodySize - 200)
            .append("\"><a:solidFill><a:srgbClr val=\"").append(theme.bodyColour)
            .append("\"/></a:solidFill><a:latin typeface=\"").append(pptxAtt(theme.font))
            .append("\"/></a:defRPr></a:lvl").append(level).append("pPr>")
    }
    sb.append("</p:bodyStyle>")
    sb.append("<p:otherStyle><a:defPPr><a:defRPr lang=\"en-US\"/></a:defPPr>")
    sb.append("<a:lvl1pPr marL=\"0\" algn=\"l\"><a:defRPr sz=\"1800\"/></a:lvl1pPr></p:otherStyle>")
    sb.append("</p:txStyles></p:sldMaster>")
    return sb.toString()
}

private fun pptxLayoutXml(index: Int, deck: PptxDeck, metrics: PptxMetrics): String {
    val theme = deck.theme
    val type = when (index) {
        1 -> "title"
        3 -> "secHead"
        4 -> "blank"
        5 -> "twoObj"
        else -> "obj"
    }
    val name = when (index) {
        1 -> "Title Slide"
        3 -> "Section Header"
        4 -> "Blank"
        5 -> "Two Content"
        else -> "Title and Content"
    }
    val sb = StringBuilder()
    sb.append("<p:sldLayout xmlns:a=\"").append(PPTX_NS_A).append("\" xmlns:r=\"").append(PPTX_NS_R)
        .append("\" xmlns:p=\"").append(PPTX_NS_P).append("\" type=\"").append(type).append("\" preserve=\"1\">")
    sb.append("<p:cSld name=\"").append(pptxAtt(name)).append("\">")
    sb.append("<p:bg><p:bgPr><a:solidFill><a:srgbClr val=\"").append(theme.background)
        .append("\"/></a:solidFill><a:effectLst/></p:bgPr></p:bg>")
    sb.append("<p:spTree>").append(pptxGroupShape())
    when (index) {
        1 -> {
            val width = metrics.cx - metrics.margin * 2
            sb.append(
                pptxPlaceholder(
                    2, "Title 1", "ctrTitle", 0,
                    metrics.margin, pptxEmu(2.0), width, pptxEmu(1.6),
                    emptyList(), 4400, theme.titleColour, theme.font, "ctr", "ctr", false, false
                )
            )
            sb.append(
                pptxPlaceholder(
                    3, "Subtitle 2", "subTitle", 1,
                    metrics.margin, pptxEmu(3.7), width, pptxEmu(1.2),
                    emptyList(), 2000, theme.bodyColour, theme.font, "ctr", "t", false, false
                )
            )
        }
        3 -> {
            sb.append(
                pptxPlaceholder(
                    2, "Title 1", "title", 0,
                    metrics.titleX, pptxEmu(2.2), metrics.titleW, pptxEmu(1.2),
                    emptyList(), 4000, theme.titleColour, theme.font, "l", "b", false, false
                )
            )
            sb.append(
                pptxPlaceholder(
                    3, "Text Placeholder 2", "body", 1,
                    metrics.bodyX, pptxEmu(3.6), metrics.bodyW, pptxEmu(2.0),
                    emptyList(), metrics.bodySize, theme.bodyColour, theme.font, "l", "t", true, false
                )
            )
        }
        4 -> {
        }
        5 -> {
            sb.append(
                pptxPlaceholder(
                    2, "Title 1", "title", 0,
                    metrics.titleX, metrics.titleY, metrics.titleW, metrics.titleH,
                    emptyList(), metrics.titleSize, theme.titleColour, theme.font, "l", "b", false, false
                )
            )
            sb.append(
                pptxPlaceholder(
                    3, "Content Placeholder 2", "body", 1,
                    metrics.bodyX, metrics.bodyY, metrics.halfW, metrics.bodyH,
                    emptyList(), metrics.bodySize, theme.bodyColour, theme.font, "l", "t", true, false
                )
            )
            sb.append(
                pptxPlaceholder(
                    4, "Content Placeholder 3", "body", 2,
                    metrics.bodyX + metrics.halfW + metrics.gap, metrics.bodyY, metrics.halfW, metrics.bodyH,
                    emptyList(), metrics.bodySize, theme.bodyColour, theme.font, "l", "t", true, false
                )
            )
        }
        else -> {
            sb.append(
                pptxPlaceholder(
                    2, "Title 1", "title", 0,
                    metrics.titleX, metrics.titleY, metrics.titleW, metrics.titleH,
                    emptyList(), metrics.titleSize, theme.titleColour, theme.font, "l", "b", false, false
                )
            )
            sb.append(
                pptxPlaceholder(
                    3, "Content Placeholder 2", "body", 1,
                    metrics.bodyX, metrics.bodyY, metrics.bodyW, metrics.bodyH,
                    emptyList(), metrics.bodySize, theme.bodyColour, theme.font, "l", "t", true, false
                )
            )
        }
    }
    sb.append("</p:spTree></p:cSld>")
    sb.append("<p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr>")
    sb.append("</p:sldLayout>")
    return sb.toString()
}

private fun pptxNotesMasterXml(deck: PptxDeck, metrics: PptxMetrics): String {
    val theme = deck.theme
    val sb = StringBuilder()
    sb.append("<p:notesMaster xmlns:a=\"").append(PPTX_NS_A).append("\" xmlns:r=\"").append(PPTX_NS_R)
        .append("\" xmlns:p=\"").append(PPTX_NS_P).append("\">")
    sb.append("<p:cSld><p:bg><p:bgPr><a:solidFill><a:srgbClr val=\"FFFFFF\"/></a:solidFill>")
        .append("<a:effectLst/></p:bgPr></p:bg>")
    sb.append("<p:spTree>").append(pptxGroupShape())
    sb.append(
        pptxPlaceholder(
            2, "Notes Placeholder 1", "body", 1,
            metrics.margin, metrics.margin, metrics.cx - metrics.margin * 2, metrics.cy - metrics.margin * 2,
            emptyList(), 1200, "000000", theme.font, "l", "t", false, false
        )
    )
    sb.append("</p:spTree></p:cSld>")
    sb.append("<p:clrMap bg1=\"lt1\" tx1=\"dk1\" bg2=\"lt2\" tx2=\"dk2\" accent1=\"accent1\" accent2=\"accent2\" ")
    sb.append("accent3=\"accent3\" accent4=\"accent4\" accent5=\"accent5\" accent6=\"accent6\" hlink=\"hlink\" folHlink=\"folHlink\"/>")
    sb.append("<p:notesStyle><a:lvl1pPr marL=\"0\" indent=\"0\" algn=\"l\"><a:defRPr sz=\"1200\" b=\"0\">")
        .append("<a:solidFill><a:srgbClr val=\"000000\"/></a:solidFill><a:latin typeface=\"")
        .append(pptxAtt(theme.font)).append("\"/></a:defRPr></a:lvl1pPr></p:notesStyle>")
    sb.append("</p:notesMaster>")
    return sb.toString()
}

private fun pptxCoreXml(deck: PptxDeck): String {
    val stamp = pptxTimestamp()
    val sb = StringBuilder()
    sb.append("<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" ")
    sb.append("xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:dcterms=\"http://purl.org/dc/terms/\" ")
    sb.append("xmlns:dcmitype=\"http://purl.org/dc/dcmitype/\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">")
    sb.append("<dc:title>").append(pptxEsc(deck.title)).append("</dc:title>")
    sb.append("<dc:creator>").append(pptxEsc(deck.author.ifBlank { "Lucent" })).append("</dc:creator>")
    sb.append("<cp:lastModifiedBy>").append(pptxEsc(deck.author.ifBlank { "Lucent" })).append("</cp:lastModifiedBy>")
    sb.append("<dcterms:created xsi:type=\"dcterms:W3CDTF\">").append(stamp).append("</dcterms:created>")
    sb.append("<dcterms:modified xsi:type=\"dcterms:W3CDTF\">").append(stamp).append("</dcterms:modified>")
    sb.append("</cp:coreProperties>")
    return sb.toString()
}

private fun pptxAppXml(deck: PptxDeck, notesCount: Int): String {
    val sb = StringBuilder()
    sb.append("<Properties xmlns=\"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties\" ")
    sb.append("xmlns:vt=\"http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes\">")
    sb.append("<Application>Lucent</Application>")
    sb.append("<PresentationFormat>").append(pptxSizeLabel(deck.cx, deck.cy)).append("</PresentationFormat>")
    sb.append("<Slides>").append(deck.slides.size).append("</Slides>")
    sb.append("<Notes>").append(notesCount).append("</Notes>")
    sb.append("<HeadingPairs><vt:vector size=\"2\" baseType=\"variant\">")
    sb.append("<vt:variant><vt:lpstr>Slide Titles</vt:lpstr></vt:variant>")
    sb.append("<vt:variant><vt:i4>").append(deck.slides.size).append("</vt:i4></vt:variant>")
    sb.append("</vt:vector></HeadingPairs>")
    sb.append("<TitlesOfParts><vt:vector size=\"").append(deck.slides.size).append("\" baseType=\"lpstr\">")
    deck.slides.forEach { slide ->
        sb.append("<vt:lpstr>").append(pptxEsc(slide.title.ifBlank { "Slide" })).append("</vt:lpstr>")
    }
    sb.append("</vt:vector></TitlesOfParts>")
    sb.append("<Company/><AppVersion>16.0000</AppVersion></Properties>")
    return sb.toString()
}

private fun pptxDeckFromEntries(entries: Map<String, ByteArray>, file: File): PptxDeck {
    val deck = PptxDeck()
    val presentation = entries["ppt/presentation.xml"]?.let { pptxParse(it, file.name) }
        ?: throw IllegalArgumentException("${file.name} has no ppt/presentation.xml")
    val root = presentation.documentElement
    val size = pptxChild(root, "sldSz")
    if (size != null) {
        deck.cx = pptxAttr(size, "cx").toLongOrNull() ?: deck.cx
        deck.cy = pptxAttr(size, "cy").toLongOrNull() ?: deck.cy
    }
    val core = entries["docProps/core.xml"]?.let { runCatching { Ooxml.parse(it) }.getOrNull() }
    if (core != null) {
        deck.title = pptxDescend(core.documentElement, "title").firstOrNull()?.let { pptxText(it) } ?: ""
        deck.author = pptxDescend(core.documentElement, "creator").firstOrNull()?.let { pptxText(it) } ?: ""
    }
    val theme = entries["ppt/theme/theme1.xml"]?.let { runCatching { Ooxml.parse(it) }.getOrNull() }
    if (theme != null) pptxReadTheme(theme.documentElement, deck.theme)
    val presentationRels = pptxRels(entries, "ppt/_rels/presentation.xml.rels")
    val ids = pptxChildren(pptxChild(root, "sldIdLst"), "sldId")
    ids.forEach { element ->
        val target = presentationRels[pptxNsAttr(element, "id")] ?: return@forEach
        val part = pptxResolve("ppt", target)
        val bytes = entries[part] ?: return@forEach
        deck.slides.add(pptxReadSlide(entries, part, bytes))
    }
    if (deck.slides.isEmpty()) {
        val found = entries.keys.filter { it.startsWith("ppt/slides/slide") && it.endsWith(".xml") }.sorted()
        found.forEach { part ->
            val bytes = entries[part] ?: return@forEach
            deck.slides.add(pptxReadSlide(entries, part, bytes))
        }
    }
    deck.subtitle = deck.slides.firstOrNull { it.layout == "title" }?.subtitle ?: ""
    return deck
}

private fun pptxReadTheme(root: Element, theme: PptxTheme) {
    val scheme = pptxDescend(root, "clrScheme").firstOrNull() ?: return
    listOf("accent1", "accent2", "dk2", "lt1", "dk1").forEach { name ->
        val value = pptxColourOf(pptxDescend(scheme, name).firstOrNull()) ?: return@forEach
        when (name) {
            "accent1" -> theme.accent1 = value
            "accent2" -> theme.accent2 = value
            "dk2" -> theme.titleColour = value
            "lt1" -> theme.background = value
            "dk1" -> theme.bodyColour = value
        }
    }
    val minor = pptxDescend(pptxDescend(root, "minorFont").firstOrNull() ?: root, "latin").firstOrNull()
    val face = pptxAttr(minor, "typeface").trim()
    if (face.isNotEmpty()) theme.font = face
}

private fun pptxColourOf(node: Element?): String? {
    if (node == null) return null
    val srgb = pptxDescend(node, "srgbClr").firstOrNull()
    if (srgb != null) {
        val value = pptxAttr(srgb, "val").trim()
        if (value.length == 6) return value.uppercase(Locale.US)
    }
    val sys = pptxDescend(node, "sysClr").firstOrNull()
    if (sys != null) {
        val value = pptxAttr(sys, "lastClr").trim()
        if (value.length == 6) return value.uppercase(Locale.US)
    }
    return null
}

private fun pptxReadSlide(entries: Map<String, ByteArray>, part: String, bytes: ByteArray): PptxSlide {
    val slide = PptxSlide()
    val rels = pptxRels(entries, pptxRelsName(part))
    val document = runCatching { Ooxml.parse(bytes) }.getOrNull()
    val root = document?.documentElement ?: return slide
    val tree = pptxDescend(root, "spTree").firstOrNull() ?: return slide
    rels.forEach { (id, target) ->
        if (target.contains("slideLayout")) {
            val name = target.substringAfterLast('/')
            val index = name.removePrefix("slideLayout").removeSuffix(".xml").toIntOrNull() ?: 0
            slide.layout = pptxLayoutName(index)
        }
        if (target.contains("notesSlide")) {
            val notes = entries[pptxResolve("ppt/slides", target)]
            if (notes != null) slide.notes = pptxNotesText(notes)
        }
    }
    pptxDirectChildren(tree).forEach { shape ->
        when (pptxLocal(shape.nodeName)) {
            "sp" -> pptxReadShape(shape, slide)
            "pic" -> pptxReadPicture(shape, slide, rels)
            "graphicFrame" -> pptxReadFrame(shape, slide, rels, entries)
            "grpSp" -> pptxDirectChildren(shape).forEach { inner ->
                when (pptxLocal(inner.nodeName)) {
                    "sp" -> pptxReadShape(inner, slide)
                    "pic" -> pptxReadPicture(inner, slide, rels)
                    "graphicFrame" -> pptxReadFrame(inner, slide, rels, entries)
                    else -> {
                    }
                }
            }
            else -> {
            }
        }
    }
    return slide
}

private fun pptxReadShape(shape: Element, slide: PptxSlide) {
    val nv = pptxChild(pptxChild(shape, "nvSpPr"), "nvPr")
    val ph = pptxChild(nv, "ph")
    val lines = pptxParagraphTexts(shape).filter { it.isNotBlank() }
    if (ph == null) {
        if (lines.isEmpty()) return
        val caption = slide.image?.caption.orEmpty()
        lines.forEach { line -> if (line != caption) slide.bullets.add(line) }
        return
    }
    val type = pptxAttr(ph, "type").ifBlank { "body" }
    val text = pptxShapeText(shape)
    when (type) {
        "title", "ctrTitle" -> if (slide.title.isBlank()) slide.title = text.replace('\n', ' ').trim()
        "subTitle" -> if (slide.subtitle.isBlank()) slide.subtitle = text.replace('\n', ' ').trim()
        else -> {
            val idx = pptxAttr(ph, "idx").toIntOrNull() ?: 0
            val target = if (slide.layout == "two_content" && idx == 2) slide.bulletsRight else slide.bullets
            lines.forEach { line -> target.add(line) }
        }
    }
}

private fun pptxReadPicture(picture: Element, slide: PptxSlide, rels: Map<String, String>) {
    val nv = pptxDescend(picture, "cNvPr").firstOrNull()
    val name = pptxAttr(nv, "name").ifBlank { "Picture" }
    val caption = pptxAttr(nv, "descr").trim()
    val blip = pptxDescend(picture, "blip").firstOrNull()
    val rid = pptxNsAttr(blip, "embed")
    val target = rels[rid] ?: ""
    val media = if (target.isEmpty()) "" else target.substringAfterLast('/')
    val xfrm = pptxDescend(picture, "xfrm").firstOrNull()
    val off = pptxChild(xfrm, "off")
    val ext = pptxChild(xfrm, "ext")
    val image = PptxImage(
        path = media,
        x = pptxAttr(off, "x").toDoubleOrNull()?.div(PPTX_EMU_PER_INCH) ?: 0.6,
        y = pptxAttr(off, "y").toDoubleOrNull()?.div(PPTX_EMU_PER_INCH) ?: 1.6,
        w = pptxAttr(ext, "cx").toDoubleOrNull()?.div(PPTX_EMU_PER_INCH) ?: 8.0,
        h = pptxAttr(ext, "cy").toDoubleOrNull()?.div(PPTX_EMU_PER_INCH) ?: 4.5,
        caption = caption,
        media = media
    )
    image.path = media.ifBlank { name }
    if (slide.image == null) slide.image = image
}

private fun pptxReadFrame(frame: Element, slide: PptxSlide, rels: Map<String, String>, entries: Map<String, ByteArray>) {
    val table = pptxDescend(frame, "tbl").firstOrNull()
    if (table != null) {
        if (slide.table == null) slide.table = pptxReadTable(table)
        return
    }
    val chartRef = pptxDescend(frame, "chart").firstOrNull() ?: return
    val rid = pptxNsAttr(chartRef, "id")
    val target = rels[rid] ?: return
    val bytes = entries[pptxResolve("ppt/slides", target)] ?: return
    if (slide.chart == null) slide.chart = pptxReadChart(bytes)
}

private fun pptxReadTable(table: Element): PptxTable {
    val model = PptxTable()
    val rows = pptxDescend(table, "tr")
    rows.forEachIndexed { index, row ->
        val cells = pptxDirectChildren(row).filter { pptxLocal(it.nodeName) == "tc" }.map { cell ->
            pptxParagraphTexts(cell).joinToString(" ").trim()
        }
        if (index == 0) model.header = cells.toMutableList() else model.rows.add(cells.toMutableList())
    }
    return model
}

private fun pptxReadChart(bytes: ByteArray): PptxChart {
    val chart = PptxChart()
    val document = runCatching { Ooxml.parse(bytes) }.getOrNull() ?: return chart
    val root = document.documentElement
    chart.type = when {
        pptxDescend(root, "barChart").isNotEmpty() -> "bar"
        pptxDescend(root, "lineChart").isNotEmpty() -> "line"
        pptxDescend(root, "pieChart").isNotEmpty() -> "pie"
        pptxDescend(root, "doughnutChart").isNotEmpty() -> "doughnut"
        pptxDescend(root, "areaChart").isNotEmpty() -> "area"
        else -> "bar"
    }
    chart.title = pptxDescend(root, "title").firstOrNull()?.let { pptxText(it).trim() } ?: ""
    val series = pptxDescend(root, "ser")
    series.forEachIndexed { index, element ->
        val name = pptxChild(element, "tx")?.let { pptxDescend(it, "v").firstOrNull()?.let { node -> pptxText(node) } }
        val values = pptxChild(element, "val")?.let { pptxNumericPoints(it) } ?: emptyList()
        chart.series.add(PptxSeries(name?.trim().orEmpty().ifEmpty { "Series ${index + 1}" }, values.toMutableList()))
        if (chart.categories.isEmpty()) {
            val cat = pptxChild(element, "cat") ?: pptxChild(element, "xVal")
            if (cat != null) chart.categories = pptxPointTexts(cat).toMutableList()
        }
    }
    return chart
}

private fun pptxNotesText(bytes: ByteArray): String {
    val document = runCatching { Ooxml.parse(bytes) }.getOrNull() ?: return ""
    val root = document.documentElement
    val tree = pptxDescend(root, "spTree").firstOrNull() ?: return ""
    val lines = mutableListOf<String>()
    pptxDescend(tree, "sp").forEach { shape ->
        val nv = pptxChild(pptxChild(shape, "nvSpPr"), "nvPr")
        val type = pptxAttr(pptxChild(nv, "ph"), "type")
        if (type == "body" || type.isEmpty()) lines.addAll(pptxParagraphTexts(shape))
    }
    return lines.joinToString("\n").trim()
}

private fun pptxShapeText(shape: Element): String {
    val body = pptxChild(shape, "txBody") ?: return ""
    return pptxText(body).trim()
}

private fun pptxParagraphTexts(node: Element): List<String> {
    val body = pptxChild(node, "txBody") ?: return emptyList()
    return pptxDirectChildren(body).filter { pptxLocal(it.nodeName) == "p" }.map { paragraph ->
        pptxFlatten(paragraph).trim()
    }
}

private fun pptxFlatten(paragraph: Element): String {
    val sb = StringBuilder()
    pptxDirectChildren(paragraph).forEach { child ->
        when (pptxLocal(child.nodeName)) {
            "r" -> sb.append(pptxText(child))
            "br" -> sb.append(' ')
            "fld" -> sb.append(pptxText(child))
            else -> {
            }
        }
    }
    return sb.toString()
}

private fun pptxNumericPoints(node: Element): List<Double> {
    val cache = pptxDescend(node, "numCache").firstOrNull() ?: pptxDescend(node, "numLit").firstOrNull() ?: node
    val points = pptxDescend(cache, "pt")
    val values = mutableListOf<Double>()
    points.forEach { point ->
        val value = pptxDescend(point, "v").firstOrNull()?.let { pptxText(it).trim() } ?: return@forEach
        values.add(value.toDoubleOrNull() ?: 0.0)
    }
    if (values.isEmpty()) {
        pptxDescend(cache, "v").forEach { node2 ->
            val value = pptxText(node2).trim().toDoubleOrNull()
            if (value != null) values.add(value)
        }
    }
    return values
}

private fun pptxPointTexts(node: Element): List<String> {
    val cache = pptxDescend(node, "strCache").firstOrNull()
        ?: pptxDescend(node, "numCache").firstOrNull()
        ?: pptxDescend(node, "strLit").firstOrNull()
        ?: node
    return pptxDescend(cache, "pt").mapNotNull { point ->
        pptxDescend(point, "v").firstOrNull()?.let { pptxText(it).trim() }
    }
}

private fun pptxDescribeSlide(sb: StringBuilder, slide: PptxSlide) {
    if (slide.title.isNotBlank()) sb.append(slide.title).append('\n')
    if (slide.subtitle.isNotBlank()) sb.append(slide.subtitle).append('\n')
    slide.bullets.forEach { sb.append("- ").append(it).append('\n') }
    slide.bulletsRight.forEach { sb.append("- ").append(it).append('\n') }
    val table = slide.table
    if (table != null && (table.header.isNotEmpty() || table.rows.isNotEmpty())) {
        val rows = mutableListOf<List<String>>()
        if (table.header.isNotEmpty()) rows.add(table.header)
        rows.addAll(table.rows)
        val width = rows.maxOf { it.size }
        rows.forEachIndexed { index, row ->
            sb.append('|')
            for (column in 0 until width) {
                val cell = row.getOrElse(column) { "" }.replace("|", "\\|").replace("\n", " ")
                sb.append(' ').append(cell).append(" |")
            }
            sb.append('\n')
            if (index == 0) {
                sb.append('|')
                for (column in 0 until width) sb.append(" --- |")
                sb.append('\n')
            }
        }
    }
    val image = slide.image
    if (image != null) {
        sb.append("[image ").append(image.path.ifBlank { "picture" })
        if (image.caption.isNotBlank()) sb.append(": ").append(image.caption)
        sb.append("]\n")
    }
    val chart = slide.chart
    if (chart != null) {
        sb.append("[chart ").append(chart.type).append(": categories ")
        sb.append(pptxCategories(chart).joinToString(", "))
        chart.series.forEach { series ->
            sb.append("; ").append(series.name).append(": ")
            sb.append(series.values.joinToString(", ") { pptxNum(it) })
        }
        sb.append("]\n")
    }
    if (slide.notes.isNotBlank()) {
        sb.append("Notes: ").append(slide.notes.replace("\n", "\n  ")).append('\n')
    }
}

private fun pptxApplyOp(deck: PptxDeck, op: JSONObject): String {
    val name = op.optString("op", "").trim().lowercase(Locale.US)
    return when (name) {
        "append_slide" -> {
            val slide = pptxSlideFromJson(op, "bullets")
            deck.slides.add(slide)
            "appended slide ${deck.slides.size} (${slide.layout})"
        }
        "replace" -> {
            val find = op.optString("find", "")
            if (find.isEmpty()) throw IllegalArgumentException("The replace op needs a find value")
            val replacement = op.optString("replace", "")
            val all = op.optBoolean("all", false)
            val count = pptxReplace(deck, find, replacement, all)
            if (count == 0) {
                "replace: no match for \"$find\""
            } else {
                "replaced $count occurrence(s) of \"$find\""
            }
        }
        "set_title" -> {
            val slide = pptxSlideAt(deck, op.optInt("slide", 0))
            slide.title = op.optString("text", "")
            "slide ${op.optInt("slide", 0)} title set"
        }
        "set_notes" -> {
            val slide = pptxSlideAt(deck, op.optInt("slide", 0))
            slide.notes = op.optString("text", "")
            "slide ${op.optInt("slide", 0)} notes set"
        }
        "add_bullet" -> {
            val slide = pptxSlideAt(deck, op.optInt("slide", 0))
            val text = op.optString("text", "").trim()
            if (text.isEmpty()) throw IllegalArgumentException("The add_bullet op needs text")
            if (op.optInt("column", 1) == 2) slide.bulletsRight.add(text) else slide.bullets.add(text)
            "bullet added to slide ${op.optInt("slide", 0)}"
        }
        "insert_image" -> {
            val slide = pptxSlideAt(deck, op.optInt("slide", 0))
            val path = op.optString("path", "").trim()
            if (path.isEmpty()) throw IllegalArgumentException("The insert_image op needs a path")
            slide.image = pptxImageFromJson(op)
            if (slide.layout == "title") slide.layout = "image"
            "image inserted on slide ${op.optInt("slide", 0)}"
        }
        "delete_slide" -> {
            val number = op.optInt("slide", 0)
            val slide = pptxSlideAt(deck, number)
            deck.slides.remove(slide)
            "deleted slide $number"
        }
        "set_theme_colour", "set_theme_color" -> {
            val key = op.optString("name", "").trim()
            val value = op.optString("value", "").trim()
            pptxSetTheme(deck.theme, key, value)
            "theme $key set to $value"
        }
        else -> throw IllegalArgumentException("Unknown op: ${op.optString("op", "")}")
    }
}

private fun pptxSlideAt(deck: PptxDeck, number: Int): PptxSlide {
    if (number < 1 || number > deck.slides.size) {
        throw IllegalArgumentException("Slide $number is out of range (the deck has ${deck.slides.size} slides)")
    }
    return deck.slides[number - 1]
}

private fun pptxReplace(deck: PptxDeck, find: String, replacement: String, all: Boolean): Int {
    val slots = mutableListOf<PptxSlot>()
    slots.add(PptxSlot({ deck.title }, { deck.title = it }))
    slots.add(PptxSlot({ deck.subtitle }, { deck.subtitle = it }))
    deck.slides.forEach { slide ->
        slots.add(PptxSlot({ slide.title }, { slide.title = it }))
        slots.add(PptxSlot({ slide.subtitle }, { slide.subtitle = it }))
        slots.add(PptxSlot({ slide.notes }, { slide.notes = it }))
        slide.bullets.forEachIndexed { index, _ ->
            slots.add(PptxSlot({ slide.bullets[index] }, { slide.bullets[index] = it }))
        }
        slide.bulletsRight.forEachIndexed { index, _ ->
            slots.add(PptxSlot({ slide.bulletsRight[index] }, { slide.bulletsRight[index] = it }))
        }
        slide.table?.let { table ->
            table.header.forEachIndexed { index, _ ->
                slots.add(PptxSlot({ table.header[index] }, { table.header[index] = it }))
            }
            table.rows.forEachIndexed { row, _ ->
                table.rows[row].forEachIndexed { column, _ ->
                    slots.add(PptxSlot({ table.rows[row][column] }, { table.rows[row][column] = it }))
                }
            }
        }
        slide.chart?.let { chart ->
            slots.add(PptxSlot({ chart.title }, { chart.title = it }))
            chart.categories.forEachIndexed { index, _ ->
                slots.add(PptxSlot({ chart.categories[index] }, { chart.categories[index] = it }))
            }
            chart.series.forEachIndexed { index, _ ->
                slots.add(PptxSlot({ chart.series[index].name }, { chart.series[index].name = it }))
            }
        }
    }
    var count = 0
    slots.forEach { slot ->
        val text = slot.read()
        if (!text.contains(find)) return@forEach
        if (all) {
            count += pptxCount(text, find)
            slot.write(text.replace(find, replacement))
        } else {
            slot.write(text.replaceFirst(find, replacement))
            count++
        }
    }
    return count
}

private class PptxSlot(val read: () -> String, val write: (String) -> Unit)

private fun pptxCount(text: String, find: String): Int {
    if (find.isEmpty()) return 0
    var count = 0
    var index = text.indexOf(find)
    while (index >= 0) {
        count++
        index = text.indexOf(find, index + find.length)
    }
    return count
}

private fun pptxSetTheme(theme: PptxTheme, key: String, value: String) {
    val colour = pptxColour(value, "")
    if (key.equals("font", ignoreCase = true)) {
        if (value.isBlank()) throw IllegalArgumentException("The theme font needs a name")
        theme.font = value
        return
    }
    if (colour.isEmpty()) throw IllegalArgumentException("$value is not a colour like #00B0F0")
    when (key) {
        "accent1" -> theme.accent1 = colour
        "accent2" -> theme.accent2 = colour
        "background" -> theme.background = colour
        "title_colour", "title_color" -> theme.titleColour = colour
        "body_colour", "body_color" -> theme.bodyColour = colour
        else -> throw IllegalArgumentException("Unknown theme colour: $key")
    }
}

private fun pptxRels(entries: Map<String, ByteArray>, part: String): Map<String, String> {
    val bytes = entries[part] ?: return emptyMap()
    val document = runCatching { Ooxml.parse(bytes) }.getOrNull() ?: return emptyMap()
    val out = LinkedHashMap<String, String>()
    pptxDescend(document.documentElement, "Relationship").forEach { element ->
        val id = pptxAttr(element, "Id")
        val target = pptxAttr(element, "Target")
        if (id.isNotEmpty() && target.isNotEmpty()) out[id] = target
    }
    return out
}

private fun pptxRelsName(part: String): String {
    val slash = part.lastIndexOf('/')
    if (slash < 0) return "_rels/$part.rels"
    return part.substring(0, slash) + "/_rels/" + part.substring(slash + 1) + ".rels"
}

private fun pptxResolve(base: String, target: String): String {
    val raw = target.trim()
    if (raw.startsWith("/")) return raw.removePrefix("/")
    val segments = mutableListOf<String>()
    base.split("/").forEach { if (it.isNotEmpty()) segments.add(it) }
    raw.split("/").forEach { segment ->
        when (segment) {
            "", "." -> {
            }
            ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.size - 1)
            else -> segments.add(segment)
        }
    }
    return segments.joinToString("/")
}

private fun pptxParse(bytes: ByteArray, name: String): org.w3c.dom.Document = try {
    Ooxml.parse(bytes)
} catch (e: Exception) {
    throw IllegalArgumentException("$name holds damaged XML: ${e.message}")
}

private fun pptxLayoutIndex(layout: String): Int = when (layout) {
    "title" -> 1
    "section" -> 3
    "blank" -> 4
    "two_content" -> 5
    else -> 2
}

private fun pptxLayoutName(index: Int): String = when (index) {
    1 -> "title"
    3 -> "section"
    4 -> "blank"
    5 -> "two_content"
    else -> "bullets"
}

private fun pptxLayoutKey(value: String): String = when (value.trim().lowercase(Locale.US).replace('-', '_')) {
    "title", "title_slide" -> "title"
    "section", "section_header" -> "section"
    "blank" -> "blank"
    "two_content", "twocontent", "comparison" -> "two_content"
    "image", "picture" -> "image"
    "table" -> "table"
    "chart" -> "chart"
    else -> "bullets"
}

private fun pptxChartType(value: String): String = when (value.trim().lowercase(Locale.US)) {
    "line" -> "line"
    "pie" -> "pie"
    "area" -> "area"
    "doughnut", "donut" -> "doughnut"
    else -> "bar"
}

private fun pptxSizeKey(value: String): String {
    val clean = value.trim().lowercase(Locale.US).replace(" ", "")
    return if (clean.startsWith("4") || clean == "standard" || clean == "narrow") "4:3" else "16:9"
}

private fun pptxSizeLabel(cx: Long, cy: Long): String = when {
    cx * 9 == cy * 16 -> "16:9"
    cx * 3 == cy * 4 -> "4:3"
    else -> "$cx x $cy EMU"
}

private fun pptxColour(value: String, fallback: String): String {
    val clean = value.trim().removePrefix("#").uppercase(Locale.US)
    if (clean.length == 6 && clean.all { it in "0123456789ABCDEF" }) return clean
    if (clean.length == 3 && clean.all { it in "0123456789ABCDEF" }) {
        return "" + clean[0] + clean[0] + clean[1] + clean[1] + clean[2] + clean[2]
    }
    return fallback
}

private fun pptxEmu(inches: Double): Long = Math.round(inches * PPTX_EMU_PER_INCH)

private fun pptxNum(value: Double): String {
    if (value.isNaN() || value.isInfinite()) return "0"
    val rounded = Math.round(value * 10000.0) / 10000.0
    if (rounded == Math.floor(rounded)) return rounded.toLong().toString()
    return String.format(Locale.US, "%.4f", rounded).trimEnd('0').trimEnd('.')
}

private fun pptxNumText(value: String): String {
    val parsed = value.trim().toDoubleOrNull() ?: return "0"
    return pptxNum(parsed)
}

private fun pptxColumn(index: Int): String {
    var value = index
    val sb = StringBuilder()
    while (value > 0) {
        val remainder = (value - 1) % 26
        sb.insert(0, ('A' + remainder))
        value = (value - 1) / 26
    }
    return if (sb.isEmpty()) "A" else sb.toString()
}

private fun pptxTimestamp(): String {
    val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    format.timeZone = TimeZone.getTimeZone("UTC")
    return format.format(Date())
}

private fun pptxXml(body: String): ByteArray = (PPTX_XML_HEAD + body).toByteArray(StandardCharsets.UTF_8)

private fun pptxEsc(value: String): String = Ooxml.escape(pptxClean(value))

private fun pptxAtt(value: String): String {
    val clean = pptxClean(value)
    val sb = StringBuilder(clean.length)
    clean.forEach { ch ->
        when (ch) {
            '&' -> sb.append("&amp;")
            '<' -> sb.append("&lt;")
            '>' -> sb.append("&gt;")
            '"' -> sb.append("&quot;")
            '\'' -> sb.append("&apos;")
            else -> sb.append(ch)
        }
    }
    return sb.toString()
}

private fun pptxClean(value: String): String {
    val sb = StringBuilder(value.length)
    value.forEach { ch ->
        if (ch == '\t' || ch == '\n' || ch == '\r' || ch.code >= 0x20) sb.append(ch)
    }
    return sb.toString()
}

private fun pptxHuman(bytes: Long): String = when {
    bytes >= 1048576 -> String.format(Locale.US, "%.1f MiB", bytes / 1048576.0)
    bytes >= 1024 -> String.format(Locale.US, "%.1f KiB", bytes / 1024.0)
    else -> "$bytes B"
}

private fun pptxImageExtension(bytes: ByteArray, path: String): String {
    if (bytes.size > 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte()) return "png"
    if (bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) return "jpeg"
    if (bytes.size > 3 && bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte()) return "gif"
    val extension = path.substringAfterLast('.', "").lowercase(Locale.US)
    return when (extension) {
        "png" -> "png"
        "jpg", "jpeg" -> "jpeg"
        "gif" -> "gif"
        else -> throw IllegalArgumentException("Only PNG, JPEG and GIF images can be embedded, not $extension")
    }
}

private fun pptxLocal(tag: String): String {
    val colon = tag.indexOf(':')
    return if (colon < 0) tag else tag.substring(colon + 1)
}

private fun pptxChild(node: Node?, tag: String): Element? {
    if (node == null) return null
    var child = node.firstChild
    while (child != null) {
        if (child is Element && pptxLocal(child.nodeName) == tag) return child
        child = child.nextSibling
    }
    return null
}

private fun pptxChildren(node: Node?, tag: String): List<Element> {
    if (node == null) return emptyList()
    val out = mutableListOf<Element>()
    var child = node.firstChild
    while (child != null) {
        if (child is Element && pptxLocal(child.nodeName) == tag) out.add(child)
        child = child.nextSibling
    }
    return out
}

private fun pptxDirectChildren(node: Node?): List<Element> {
    if (node == null) return emptyList()
    val out = mutableListOf<Element>()
    var child = node.firstChild
    while (child != null) {
        if (child is Element) out.add(child)
        child = child.nextSibling
    }
    return out
}

private fun pptxDescend(node: Node?, tag: String): List<Element> {
    if (node == null) return emptyList()
    val out = mutableListOf<Element>()
    pptxCollect(node, tag, out)
    return out
}

private fun pptxCollect(node: Node, tag: String, out: MutableList<Element>) {
    var child = node.firstChild
    while (child != null) {
        if (child is Element) {
            if (pptxLocal(child.nodeName) == tag) out.add(child)
            pptxCollect(child, tag, out)
        }
        child = child.nextSibling
    }
}

private fun pptxAttr(node: Element?, name: String): String {
    if (node == null) return ""
    val attribute = node.attributes?.getNamedItem(name) ?: return ""
    return attribute.nodeValue ?: ""
}

private fun pptxNsAttr(node: Element?, name: String): String {
    if (node == null) return ""
    val attributes = node.attributes ?: return ""
    for (i in 0 until attributes.length) {
        val attribute = attributes.item(i) ?: continue
        val local = pptxLocal(attribute.nodeName)
        if (local == name) return attribute.nodeValue ?: ""
    }
    return ""
}

private fun pptxText(node: Node?): String = if (node == null) "" else Ooxml.textOf(node)
