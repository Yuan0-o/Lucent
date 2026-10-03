package com.lucent.app.harness.ooxml

import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File

private const val XL_WORKBOOK_PART = "xl/workbook.xml"
private const val XL_WORKBOOK_RELS_PART = "xl/_rels/workbook.xml.rels"
private const val XL_STYLES_PART = "xl/styles.xml"
private const val XL_SHARED_STRINGS_PART = "xl/sharedStrings.xml"
private const val XL_CORE_PART = "docProps/core.xml"
private const val XL_APP_PART = "docProps/app.xml"

private const val REL_XL_WORKSHEET = NS_OFFICE_RELATIONSHIPS + "/worksheet"
private const val REL_XL_STYLES = NS_OFFICE_RELATIONSHIPS + "/styles"
private const val REL_XL_SHARED_STRINGS = NS_OFFICE_RELATIONSHIPS + "/sharedStrings"
private const val REL_XL_DRAWING = NS_OFFICE_RELATIONSHIPS + "/drawing"
private const val REL_XL_CHART = NS_OFFICE_RELATIONSHIPS + "/chart"

private const val CT_XL_WORKBOOK = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"
private const val CT_XL_WORKSHEET = "application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"
private const val CT_XL_STYLES = "application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"
private const val CT_XL_SHARED_STRINGS = "application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"
private const val CT_XL_DRAWING = "application/vnd.openxmlformats-officedocument.drawing+xml"
private const val CT_XL_CHART = "application/vnd.openxmlformats-officedocument.drawingml.chart+xml"
private const val CT_XL_CORE = "application/vnd.openxmlformats-package.core-properties+xml"
private const val CT_XL_APP = "application/vnd.openxmlformats-officedocument.extended-properties+xml"

private const val CATEGORY_AXIS = "111111111"
private const val VALUE_AXIS = "222222222"

private const val DRAWING_NAMESPACES =
    "xmlns:xdr=\"$NS_SPREADSHEET_DRAWING\" xmlns:a=\"$NS_DRAWING\" xmlns:c=\"$NS_CHART\" xmlns:r=\"$NS_OFFICE_RELATIONSHIPS\""

private val WORKSHEET_ORDER = listOf(
    "sheetPr", "dimension", "sheetViews", "sheetFormatPr", "cols", "sheetData", "sheetCalcPr",
    "sheetProtection", "protectedRanges", "scenarios", "autoFilter", "sortState", "dataConsolidate",
    "customSheetViews", "mergeCells", "phoneticPr", "conditionalFormatting", "dataValidations",
    "hyperlinks", "printOptions", "pageMargins", "pageSetup", "headerFooter", "rowBreaks", "colBreaks",
    "customProperties", "cellWatches", "ignoredErrors", "smartTags", "drawing", "legacyDrawing",
    "legacyDrawingHF", "picture", "oleObjects", "controls", "webPublishItems", "tableParts", "extLst"
)

private val STYLESHEET_ORDER = listOf(
    "numFmts", "fonts", "fills", "borders", "cellStyleXfs", "cellXfs", "cellStyles", "dxfs",
    "tableStyles", "colors", "extLst"
)

private val WORKBOOK_ORDER = listOf(
    "fileVersion", "fileSharing", "workbookPr", "workbookProtection", "bookViews", "sheets",
    "functionGroups", "externalReferences", "definedNames", "calcPr", "oleSize", "customWorkbookViews",
    "pivotCaches", "smartTagPr", "smartTagTypes", "webPublishing", "fileRecoveryPr",
    "webPublishObjects", "extLst"
)

private val BUILTIN_FORMATS = mapOf(
    "General" to 0, "0" to 1, "0.00" to 2, "#,##0" to 3, "#,##0.00" to 4, "0%" to 9, "0.00%" to 10,
    "0.00E+00" to 11, "# ?/?" to 12, "# ??/??" to 13, "mm-dd-yy" to 14, "d-mmm-yy" to 15,
    "d-mmm" to 16, "mmm-yy" to 17, "h:mm AM/PM" to 18, "h:mm:ss AM/PM" to 19, "h:mm" to 20,
    "h:mm:ss" to 21, "m/d/yy h:mm" to 22, "@" to 49
)

private val HORIZONTAL_ALIGNMENTS = setOf(
    "general", "left", "center", "right", "fill", "justify", "centerContinuous", "distributed"
)

private val VERTICAL_ALIGNMENTS = setOf("top", "center", "bottom", "justify", "distributed")

private val CELL_OPERATORS = setOf(
    "lessThan", "lessThanOrEqual", "equal", "notEqual", "greaterThanOrEqual", "greaterThan",
    "between", "notBetween", "containsText", "notContains", "beginsWith", "endsWith"
)

private data class Ref(val row: Int, val col: Int)

private data class RangeRef(val r1: Int, val c1: Int, val r2: Int, val c2: Int)

private class CellData(var text: String? = null, var number: Double? = null, var flag: Boolean? = null, var formula: String = "")

private class SpreadsheetNs(private val prefix: String) {

    fun tag(name: String): String = if (prefix.isEmpty()) name else "$prefix:$name"

    val declaration: String
        get() = if (prefix.isEmpty()) "xmlns=\"$NS_SPREADSHEET\"" else "xmlns:$prefix=\"$NS_SPREADSHEET\""

    companion object {
        fun of(document: Document): SpreadsheetNs {
            val name = document.documentElement?.nodeName ?: ""
            return SpreadsheetNs(name.substringBefore(':', ""))
        }
    }
}

object Xlsx {

    fun create(specJson: String, out: File): String {
        val spec = jsonObject(specJson, "spreadsheet spec")
        val sheets = spec.optJSONArray("sheets")
            ?: throw IllegalArgumentException("The spreadsheet spec needs a \"sheets\" array")
        if (sheets.length() == 0) throw IllegalArgumentException("The spreadsheet spec needs at least one sheet")
        val parts = LinkedHashMap<String, ByteArray>()
        val contentTypes = parse(minimalContentTypesBytes())
        val workbook = parse(emptyWorkbookBytes())
        val workbookRels = parse(emptyRelationshipsBytes())
        val styles = parse(XL_STYLES_SKELETON.toByteArray(Charsets.UTF_8))
        val book = StyleBook(styles)
        addOrderedXml(workbook, workbook.documentElement, "<workbookPr/>", "workbookPr", WORKBOOK_ORDER, "xmlns=\"$NS_SPREADSHEET\"")
        addOrderedXml(
            workbook, workbook.documentElement,
            "<bookViews><workbookView activeTab=\"0\"/></bookViews>", "bookViews", WORKBOOK_ORDER, "xmlns=\"$NS_SPREADSHEET\""
        )
        val names = mutableListOf<String>()
        var chartCount = 0
        var drawingCount = 0
        for (index in 0 until sheets.length()) {
            val sheetSpec = sheets.optJSONObject(index)
                ?: throw IllegalArgumentException("Sheet ${index + 1} in the spec is not a JSON object")
            val name = validSheetName(stringOf(sheetSpec, "name", "Sheet${index + 1}"), names)
            names.add(name)
            val sheetDoc = parse(emptyWorksheetBytes())
            populateSheet(sheetDoc, book, sheetSpec)
            val charts = sheetSpec.optJSONArray("charts")
            if (charts != null && charts.length() > 0) {
                drawingCount++
                val drawingDoc = parse(emptyDrawingBytes())
                val drawingRels = parse(emptyRelationshipsBytes())
                for (position in 0 until charts.length()) {
                    val chartSpec = charts.optJSONObject(position) ?: continue
                    chartCount++
                    val chartPart = "xl/charts/chart$chartCount.xml"
                    parts[chartPart] = documentBytes(chartNode(chartSpec, name, chartCount))
                    addOverride(contentTypes, "/$chartPart", CT_XL_CHART)
                    val chartRel = addRelationship(drawingRels, REL_XL_CHART, "../charts/chart$chartCount.xml")
                    addOrderedXml(
                        drawingDoc, drawingDoc.documentElement,
                        anchorNode(chartSpec, chartRel, position + 1).render(), "twoCellAnchor",
                        listOf("twoCellAnchor", "oneCellAnchor", "absoluteAnchor"), DRAWING_NAMESPACES
                    )
                }
                parts["xl/drawings/drawing$drawingCount.xml"] = serialize(drawingDoc)
                parts["xl/drawings/_rels/drawing$drawingCount.xml.rels"] = serialize(drawingRels)
                addOverride(contentTypes, "/xl/drawings/drawing$drawingCount.xml", CT_XL_DRAWING)
                val sheetRels = parse(emptyRelationshipsBytes())
                val drawingRel = addRelationship(sheetRels, REL_XL_DRAWING, "../drawings/drawing$drawingCount.xml")
                parts["xl/worksheets/_rels/sheet${index + 1}.xml.rels"] = serialize(sheetRels)
                addOrderedXml(
                    sheetDoc, sheetDoc.documentElement,
                    "<drawing r:id=\"$drawingRel\"/>", "drawing", WORKSHEET_ORDER,
                    "xmlns:r=\"$NS_OFFICE_RELATIONSHIPS\""
                )
            }
            updateDimension(sheetDoc)
            val sheetPart = "xl/worksheets/sheet${index + 1}.xml"
            parts[sheetPart] = serialize(sheetDoc)
            addOverride(contentTypes, "/$sheetPart", CT_XL_WORKSHEET)
            val sheetRel = addRelationship(workbookRels, REL_XL_WORKSHEET, "worksheets/sheet${index + 1}.xml")
            val container = ensureOrdered(workbook, workbook.documentElement, "sheets", WORKBOOK_ORDER)
            val element = workbook.createElement("sheet")
            element.setAttribute("name", name)
            element.setAttribute("sheetId", (index + 1).toString())
            element.setAttribute("r:id", sheetRel)
            container.appendChild(element)
        }
        addRelationship(workbookRels, REL_XL_STYLES, "styles.xml")
        addOverride(contentTypes, "/xl/styles.xml", CT_XL_STYLES)
        addOverride(contentTypes, "/$XL_WORKBOOK_PART", CT_XL_WORKBOOK)
        addOverride(contentTypes, "/$XL_CORE_PART", CT_XL_CORE)
        addOverride(contentTypes, "/$XL_APP_PART", CT_XL_APP)
        addOrderedXml(
            workbook, workbook.documentElement,
            "<calcPr calcId=\"191029\" fullCalcOnLoad=\"1\"/>", "calcPr", WORKBOOK_ORDER, "xmlns=\"$NS_SPREADSHEET\""
        )
        val title = stringOf(spec, "title")
        val author = stringOf(spec, "author")
        parts[CONTENT_TYPES_PART] = serialize(contentTypes)
        parts["_rels/.rels"] = documentBytes(sheetPackageRels())
        parts[XL_CORE_PART] = documentBytes(sheetCoreProperties(title, author))
        parts[XL_APP_PART] = XL_APP_XML.toByteArray(Charsets.UTF_8)
        parts[XL_WORKBOOK_PART] = serialize(workbook)
        parts[XL_WORKBOOK_RELS_PART] = serialize(workbookRels)
        parts[XL_STYLES_PART] = serialize(styles)
        writZip(out, parts.entries.map { it.key to it.value })
        return "${names.size} ${word(names.size, "sheet")}: ${names.joinToString(", ")}"
    }

    fun read(file: File, sheet: String = "", range: String = "", maxRows: Int = 200): String {
        val parts = readZip(file)
        val reader = WorkbookReader(parts)
        val name = reader.pick(sheet)
        val clipped = if (range.isBlank()) {
            null
        } else {
            parseRange(range) ?: throw IllegalArgumentException("Bad range: $range")
        }
        val grid = reader.grid(name, clipped, maxRows)
        val head = StringBuilder()
        val all = reader.names()
        head.append("Workbook: ").append(all.size).append(' ').append(word(all.size, "sheet"))
        head.append(" (").append(all.joinToString(", ")).append(")\n")
        head.append("Sheet: ").append(name)
        if (grid.rows.isEmpty()) {
            head.append(" is empty")
        } else {
            head.append(": rows ").append(grid.firstRow).append('-').append(grid.lastRow)
            head.append(", columns ").append(columnName(grid.firstColumn)).append('-').append(columnName(grid.lastColumn))
        }
        head.append('\n')
        if (grid.formulas.isNotEmpty()) {
            head.append("Formulas: ").append(grid.formulas.take(12).joinToString(", "))
            if (grid.formulas.size > 12) head.append(" (+").append(grid.formulas.size - 12).append(" more)")
            head.append('\n')
        }
        val body = grid.rows.joinToString("\n") { line -> line.joinToString(",") { csvField(it) } }
        var result = head.toString() + "\n" + body
        if (grid.rows.size < grid.totalRows) {
            result += "\n… " + (grid.totalRows - grid.rows.size) + " more " + word(grid.totalRows - grid.rows.size, "row")
        }
        return result.trimEnd() + "\n"
    }

    fun edit(file: File, opsJson: String): String {
        if (!file.exists()) throw IllegalArgumentException("${file.name} does not exist")
        val ops = jsonArray(opsJson, "spreadsheet edit operations")
        if (ops.length() == 0) throw IllegalArgumentException("Give at least one edit operation")
        val parts = readZip(file).toMutableMap()
        val book = MutableBook(parts)
        val shared: ((String) -> Int)? = if (book.useSharedStrings()) {
            { text -> book.sharedId(text) }
        } else {
            null
        }
        val applied = LinkedHashSet<String>()
        for (index in 0 until ops.length()) {
            val op = ops.optJSONObject(index)
                ?: throw IllegalArgumentException("Operation ${index + 1} is not a JSON object")
            val kind = stringOf(op, "op").trim().lowercase()
            when (kind) {
                "set" -> {
                    val refText = stringOf(op, "ref")
                    if (refText.isBlank()) throw IllegalArgumentException("The set op needs a \"ref\" cell such as B2")
                    val sheetDoc = book.document(stringOf(op, "sheet"))
                    val data = cellData(op.opt("value"))
                    val formula = stringOf(op, "formula")
                    if (formula.isNotEmpty()) data.formula = formula
                    val styleIndex = if (op.has("style")) book.styleBook.xf(op.optJSONObject("style")) else -1
                    setCell(sheetDoc, parseRef(refText), data, styleIndex, shared)
                    updateDimension(sheetDoc)
                }
                "append_rows" -> {
                    val rows = op.optJSONArray("rows")
                        ?: throw IllegalArgumentException("The append_rows op needs a \"rows\" array")
                    val sheetDoc = book.document(stringOf(op, "sheet"))
                    val start = lastRow(sheetDoc) + 1
                    for (rowIndex in 0 until rows.length()) {
                        val values = rows.optJSONArray(rowIndex) ?: continue
                        for (column in 0 until values.length()) {
                            setCell(sheetDoc, Ref(start + rowIndex, column + 1), cellData(values.opt(column)), -1, shared)
                        }
                    }
                    updateDimension(sheetDoc)
                }
                "add_sheet" -> {
                    val name = stringOf(op, "name")
                    if (name.isBlank()) throw IllegalArgumentException("The add_sheet op needs a \"name\"")
                    book.create(name)
                }
                "rename_sheet" -> book.rename(stringOf(op, "from"), stringOf(op, "to"))
                "delete_sheet" -> book.delete(stringOf(op, "name"))
                "set_column_width" -> {
                    val sheetDoc = book.document(stringOf(op, "sheet"))
                    val width = op.optDouble("width", 0.0)
                    if (width <= 0.0) throw IllegalArgumentException("The set_column_width op needs a positive \"width\"")
                    setColumnWidth(sheetDoc, stringOf(op, "column"), width)
                }
                "merge" -> {
                    val sheetDoc = book.document(stringOf(op, "sheet"))
                    appendMerge(sheetDoc, stringOf(op, "range"))
                    updateDimension(sheetDoc)
                }
                "freeze" -> setFreeze(book.document(stringOf(op, "sheet")), stringOf(op, "cell", "A1"))
                "autofilter" -> {
                    val sheetDoc = book.document(stringOf(op, "sheet"))
                    if (op.optBoolean("on", true)) {
                        val declared = stringOf(op, "range")
                        setAutoFilter(sheetDoc, if (declared.isBlank()) autoFilterRange(sheetDoc) else declared)
                    } else {
                        clearAutoFilter(sheetDoc)
                    }
                }
                else -> throw IllegalArgumentException("Unknown spreadsheet op: $kind")
            }
            applied.add(kind)
        }
        book.save()
        writZip(file, parts.entries.map { it.key to it.value })
        return "Applied ${ops.length()} operation(s): ${applied.joinToString(", ")}"
    }

    fun csvOut(file: File, sheet: String = ""): String {
        val parts = readZip(file)
        val reader = WorkbookReader(parts)
        val grid = reader.grid(reader.pick(sheet), null, 0)
        return grid.rows.joinToString("\n") { line -> line.joinToString(",") { csvField(it) } }
    }

    fun csvIn(file: File, csv: String, sheet: String = "", startCell: String = "A1"): String {
        if (csv.isBlank()) throw IllegalArgumentException("The CSV text is empty")
        if (!file.exists()) throw IllegalArgumentException("${file.name} does not exist")
        val rows = parseCsv(csv)
        if (rows.isEmpty()) throw IllegalArgumentException("The CSV text has no rows")
        val start = parseRef(startCell)
        val parts = readZip(file).toMutableMap()
        val book = MutableBook(parts)
        val shared: ((String) -> Int)? = if (book.useSharedStrings()) {
            { text -> book.sharedId(text) }
        } else {
            null
        }
        val name = if (sheet.isBlank()) book.first() else sheet
        val sheetDoc = book.resolveOrCreate(name)
        for (rowIndex in rows.indices) {
            val fields = rows[rowIndex]
            for (columnIndex in fields.indices) {
                val field = fields[columnIndex]
                if (field.isEmpty()) continue
                val target = Ref(start.row + rowIndex, start.col + columnIndex)
                setCell(sheetDoc, target, textCellData(field), -1, shared)
            }
        }
        updateDimension(sheetDoc)
        book.save()
        writZip(file, parts.entries.map { it.key to it.value })
        return "Imported ${rows.size} ${word(rows.size, "row")} into sheet $name at ${columnName(start.col)}${start.row}"
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

    private fun word(count: Int, name: String): String = if (count == 1) name else "${name}s"
}

private class StyleBook(private val styles: Document) {

    private val ns = SpreadsheetNs.of(styles)
    private val fontIds = HashMap<String, Int>()
    private val fillIds = HashMap<String, Int>()
    private val borderIds = HashMap<String, Int>()
    private val formatIds = HashMap<String, Int>()
    private val xfIds = HashMap<String, Int>()
    private val dxfIds = HashMap<String, Int>()

    fun xf(style: JSONObject?): Int {
        if (style == null || style.length() == 0) return 0
        val key = style.toString()
        val existing = xfIds[key]
        if (existing != null) return existing
        val created = createXf(style)
        xfIds[key] = created
        return created
    }

    fun dxf(colour: String, fill: String): Int {
        val rgb = colourValue(colour)
        val fillRgb = colourValue(fill)
        if (rgb.isEmpty() && fillRgb.isEmpty()) return 0
        val key = "$rgb|$fillRgb"
        val existing = dxfIds[key]
        if (existing != null) return existing
        val builder = node(ns.tag("dxf"))
        if (rgb.isNotEmpty()) builder.child(ns.tag("font")).child(ns.tag("color")).attr("rgb", rgb)
        if (fillRgb.isNotEmpty()) {
            builder.child(ns.tag("fill")).child(ns.tag("patternFill")).child(ns.tag("bgColor")).attr("rgb", fillRgb)
        }
        val container = ensureOrdered(styles, styles.documentElement, ns.tag("dxfs"), STYLESHEET_ORDER)
        val element = appendXml(styles, container, builder.render(), ns.declaration) ?: return 0
        syncCount(container)
        val index = indexOfChild(container, element)
        dxfIds[key] = index
        return index
    }

    private fun createXf(style: JSONObject): Int {
        val font = fontId(style)
        val fill = fillId(stringOf(style, "fill"))
        val border = if (style.optBoolean("border", false)) borderId() else 0
        val formatCode = stringOf(style, "format")
        val format = if (formatCode.isBlank()) 0 else numFmtId(formatCode)
        val alignment = alignmentAttributes(style)
        val builder = node(ns.tag("xf"))
        builder.attr("numFmtId", format).attr("fontId", font).attr("fillId", fill).attr("borderId", border)
        builder.attr("xfId", 0)
        if (format != 0) builder.attr("applyNumberFormat", 1)
        if (font != 0) builder.attr("applyFont", 1)
        if (fill != 0) builder.attr("applyFill", 1)
        if (border != 0) builder.attr("applyBorder", 1)
        if (alignment.isNotEmpty()) {
            builder.attr("applyAlignment", 1)
            val element = builder.child(ns.tag("alignment"))
            alignment.forEach { entry -> element.attr(entry.key, entry.value) }
        }
        val container = ensureOrdered(styles, styles.documentElement, ns.tag("cellXfs"), STYLESHEET_ORDER)
        val created = appendXml(styles, container, builder.render(), ns.declaration) ?: return 0
        syncCount(container)
        return indexOfChild(container, created)
    }

    private fun fontId(style: JSONObject): Int {
        val bold = style.optBoolean("bold", false)
        val italic = style.optBoolean("italic", false)
        val strike = style.optBoolean("strike", false)
        val underline = style.optBoolean("underline", false)
        val size = style.optDouble("size", 0.0)
        val colour = stringOf(style, "colour").ifEmpty { stringOf(style, "color") }
        val name = stringOf(style, "font")
        if (!bold && !italic && !strike && !underline && size <= 0.0 && colour.isBlank() && name.isBlank()) return 0
        val key = "$bold|$italic|$strike|$underline|$size|$colour|$name"
        val existing = fontIds[key]
        if (existing != null) return existing
        val rgb = colourValue(colour).ifEmpty { "FF000000" }
        val builder = node(ns.tag("font"))
        if (bold) builder.child(ns.tag("b"))
        if (italic) builder.child(ns.tag("i"))
        if (strike) builder.child(ns.tag("strike"))
        if (underline) builder.child(ns.tag("u"))
        builder.child(ns.tag("sz")).attr("val", numberText(if (size > 0.0) size else 11.0))
        builder.child(ns.tag("color")).attr("rgb", rgb)
        builder.child(ns.tag("name")).attr("val", if (name.isBlank()) "Calibri" else name)
        builder.child(ns.tag("family")).attr("val", 2)
        val container = ensureOrdered(styles, styles.documentElement, ns.tag("fonts"), STYLESHEET_ORDER)
        val created = appendXml(styles, container, builder.render(), ns.declaration) ?: return 0
        syncCount(container)
        val index = indexOfChild(container, created)
        fontIds[key] = index
        return index
    }

    private fun fillId(colour: String): Int {
        if (colour.isBlank()) return 0
        val rgb = colourValue(colour)
        if (rgb.isEmpty()) return 0
        val existing = fillIds[rgb]
        if (existing != null) return existing
        val builder = node(ns.tag("fill"))
        builder.child(ns.tag("patternFill")).attr("patternType", "solid").apply {
            child(ns.tag("fgColor")).attr("rgb", rgb)
            child(ns.tag("bgColor")).attr("indexed", 64)
        }
        val container = ensureOrdered(styles, styles.documentElement, ns.tag("fills"), STYLESHEET_ORDER)
        val created = appendXml(styles, container, builder.render(), ns.declaration) ?: return 0
        syncCount(container)
        val index = indexOfChild(container, created)
        fillIds[rgb] = index
        return index
    }

    private fun borderId(): Int {
        val key = "thin"
        val existing = borderIds[key]
        if (existing != null) return existing
        val builder = node(ns.tag("border"))
        listOf("left", "right", "top", "bottom").forEach { side ->
            builder.child(ns.tag(side)).attr("style", "thin").child(ns.tag("color")).attr("rgb", "FFBFBFBF")
        }
        builder.child(ns.tag("diagonal"))
        val container = ensureOrdered(styles, styles.documentElement, ns.tag("borders"), STYLESHEET_ORDER)
        val created = appendXml(styles, container, builder.render(), ns.declaration) ?: return 0
        syncCount(container)
        val index = indexOfChild(container, created)
        borderIds[key] = index
        return index
    }

    private fun numFmtId(code: String): Int {
        val builtin = BUILTIN_FORMATS[code]
        if (builtin != null) return builtin
        val existing = formatIds[code]
        if (existing != null) return existing
        val container = ensureOrdered(styles, styles.documentElement, ns.tag("numFmts"), STYLESHEET_ORDER)
        var id = 164
        children(container, "numFmt").forEach { item ->
            val used = attr(item, "numFmtId").toIntOrNull() ?: 0
            if (used >= id) id = used + 1
        }
        val builder = node(ns.tag("numFmt")).attr("numFmtId", id).attr("formatCode", code)
        appendXml(styles, container, builder.render(), ns.declaration)
        syncCount(container)
        formatIds[code] = id
        return id
    }

    private fun alignmentAttributes(style: JSONObject): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val horizontal = alignmentValue(stringOf(style, "align"), HORIZONTAL_ALIGNMENTS)
        if (horizontal.isNotEmpty()) out["horizontal"] = horizontal
        val vertical = alignmentValue(stringOf(style, "valign"), VERTICAL_ALIGNMENTS)
        if (vertical.isNotEmpty()) out["vertical"] = vertical
        if (style.optBoolean("wrap", false)) out["wrapText"] = "1"
        return out
    }

    private fun alignmentValue(value: String, allowed: Set<String>): String {
        val clean = value.trim()
        if (clean.isEmpty()) return ""
        val match = allowed.firstOrNull { it.equals(clean, ignoreCase = true) }
        return match ?: ""
    }
}

private class SheetGrid(
    val rows: List<List<String>>,
    val formulas: List<String>,
    val firstRow: Int,
    val firstColumn: Int,
    val lastRow: Int,
    val lastColumn: Int,
    val totalRows: Int
)

private class WorkbookReader(private val parts: Map<String, ByteArray>) {

    private val workbook: Document
    private val shared: List<String>
    private val sheetParts = LinkedHashMap<String, String>()

    init {
        val bytes = parts[XL_WORKBOOK_PART]
            ?: throw IllegalArgumentException("This file is not an Excel workbook (xl/workbook.xml is missing)")
        workbook = parse(bytes)
        val rels = parts[XL_WORKBOOK_RELS_PART]?.let { raw ->
            try {
                parse(raw)
            } catch (e: Exception) {
                null
            }
        }
        shared = readSharedStrings(parts)
        children(workbook.documentElement, "sheets").forEach { container ->
            children(container, "sheet").forEach { sheet ->
                val name = attr(sheet, "name")
                val target = relationshipTarget(rels, attr(sheet, "r:id"))
                if (name.isNotEmpty() && target.isNotEmpty()) sheetParts[name] = target
            }
        }
    }

    fun names(): List<String> = sheetParts.keys.toList()

    fun pick(name: String): String {
        if (name.isBlank()) {
            return sheetParts.keys.firstOrNull()
                ?: throw IllegalArgumentException("The workbook has no sheets")
        }
        if (sheetParts.containsKey(name)) return name
        val match = sheetParts.keys.firstOrNull { it.equals(name, ignoreCase = true) }
        if (match != null) return match
        throw IllegalArgumentException("No sheet named \"$name\". Sheets: ${names().joinToString(", ")}")
    }

    fun grid(sheet: String, range: RangeRef?, maxRows: Int): SheetGrid {
        val part = sheetParts[sheet]
            ?: throw IllegalArgumentException("Sheet $sheet is missing from the workbook")
        val bytes = parts[part] ?: throw IllegalArgumentException("Sheet $sheet is missing from the workbook")
        val document = parse(bytes)
        val byRow = HashMap<Int, Map<Int, String>>()
        val formulas = mutableListOf<String>()
        children(document.documentElement, "sheetData").forEach { sheetData ->
            var rowPosition = 0
            children(sheetData, "row").forEach { row ->
                rowPosition++
                val declared = attr(row, "r").toIntOrNull() ?: rowPosition
                var cellPosition = 0
                val values = HashMap<Int, String>()
                children(row, "c").forEach { cell ->
                    cellPosition++
                    val parsed = parseRefOrNull(attr(cell, "r"))
                    val rowIndex = parsed?.row ?: declared
                    val column = parsed?.col ?: cellPosition
                    val formula = children(cell, "f").firstOrNull()?.let { textOf(it) } ?: ""
                    var rendered = cellText(cell, shared)
                    if (formula.isNotEmpty()) {
                        formulas.add(columnName(column) + rowIndex + "=" + formula)
                        if (rendered.isEmpty()) rendered = "=$formula"
                    }
                    if (rendered.isNotEmpty() && rowIndex > 0 && column > 0) values[column] = rendered
                }
                if (values.isNotEmpty()) byRow[declared] = values
            }
        }
        var minRow = Int.MAX_VALUE
        var maxRow = 0
        var minColumn = Int.MAX_VALUE
        var maxColumn = 0
        byRow.forEach { entry ->
            if (entry.key > 0) {
                minRow = minOf(minRow, entry.key)
                maxRow = maxOf(maxRow, entry.key)
            }
            entry.value.keys.forEach { column ->
                minColumn = minOf(minColumn, column)
                maxColumn = maxOf(maxColumn, column)
            }
        }
        if (maxRow == 0 || maxColumn == 0) {
            return SheetGrid(emptyList(), formulas, 0, 0, 0, 0, 0)
        }
        if (range != null) {
            minRow = maxOf(minRow, range.r1)
            maxRow = minOf(maxRow, range.r2)
            minColumn = maxOf(minColumn, range.c1)
            maxColumn = minOf(maxColumn, range.c2)
        }
        if (maxRow < minRow || maxColumn < minColumn) {
            return SheetGrid(emptyList(), formulas, 0, 0, 0, 0, 0)
        }
        val total = maxRow - minRow + 1
        val limit = if (maxRows > 0) minOf(maxRows, total) else total
        val out = mutableListOf<List<String>>()
        for (offset in 0 until limit) {
            val rowIndex = minRow + offset
            val source = byRow[rowIndex] ?: emptyMap()
            val line = mutableListOf<String>()
            for (column in minColumn..maxColumn) line.add(source[column] ?: "")
            out.add(line)
        }
        return SheetGrid(out, formulas, minRow, minColumn, maxRow, maxColumn, total)
    }
}

private class MutableBook(private val parts: MutableMap<String, ByteArray>) {

    val workbook: Document
    val workbookRels: Document
    val styles: Document
    val styleBook: StyleBook

    private val contentTypes: Document
    private val sheetDocs = LinkedHashMap<String, Document>()
    private val sheetParts = LinkedHashMap<String, String>()
    private var shared: Document? = null

    init {
        val bytes = parts[XL_WORKBOOK_PART]
            ?: throw IllegalArgumentException("This file is not an Excel workbook (xl/workbook.xml is missing)")
        workbook = parse(bytes)
        workbookRels = parse(parts[XL_WORKBOOK_RELS_PART] ?: emptyRelationshipsBytes())
        styles = parse(parts[XL_STYLES_PART] ?: XL_STYLES_SKELETON.toByteArray(Charsets.UTF_8))
        styleBook = StyleBook(styles)
        contentTypes = parse(parts[CONTENT_TYPES_PART] ?: minimalContentTypesBytes())
        shared = parts[XL_SHARED_STRINGS_PART]?.let { raw ->
            try {
                parse(raw)
            } catch (e: Exception) {
                null
            }
        }
        children(workbook.documentElement, "sheets").forEach { container ->
            children(container, "sheet").forEach { sheet ->
                val name = attr(sheet, "name")
                val target = relationshipTarget(workbookRels, attr(sheet, "r:id"))
                if (name.isNotEmpty() && target.isNotEmpty()) sheetParts[name] = target
            }
        }
    }

    fun names(): List<String> = sheetParts.keys.toList()

    fun first(): String = sheetParts.keys.firstOrNull() ?: throw IllegalArgumentException("The workbook has no sheets")

    fun pick(name: String): String {
        if (name.isBlank()) return first()
        if (sheetParts.containsKey(name)) return name
        val match = sheetParts.keys.firstOrNull { it.equals(name, ignoreCase = true) }
        if (match != null) return match
        throw IllegalArgumentException("No sheet named \"$name\". Sheets: ${names().joinToString(", ")}")
    }

    fun document(name: String): Document {
        val key = pick(name)
        val existing = sheetDocs[key]
        if (existing != null) return existing
        val part = sheetParts[key] ?: throw IllegalArgumentException("Sheet $key is missing from the workbook")
        val bytes = parts[part] ?: throw IllegalArgumentException("Sheet $key is missing from the workbook")
        val document = parse(bytes)
        sheetDocs[key] = document
        return document
    }

    fun resolveOrCreate(name: String): Document {
        val existing = sheetParts.keys.firstOrNull { it.equals(name, ignoreCase = true) }
        if (existing != null) return document(existing)
        return create(name)
    }

    fun create(name: String): Document {
        val clean = validSheetName(name, sheetParts.keys.toList())
        var index = 1
        while (parts.containsKey("xl/worksheets/sheet$index.xml")) index++
        val partName = "xl/worksheets/sheet$index.xml"
        val document = parse(emptyWorksheetBytes())
        parts[partName] = serialize(document)
        addOverride(contentTypes, "/$partName", CT_XL_WORKSHEET)
        val relId = addRelationship(workbookRels, REL_XL_WORKSHEET, "worksheets/sheet$index.xml")
        ensureRelationshipPrefix(workbook)
        val container = ensureOrdered(workbook, workbook.documentElement, "sheets", WORKBOOK_ORDER)
        val element = workbook.createElement("sheet")
        element.setAttribute("name", clean)
        element.setAttribute("sheetId", nextSheetId().toString())
        element.setAttribute("r:id", relId)
        container.appendChild(element)
        sheetParts[clean] = partName
        sheetDocs[clean] = document
        return document
    }

    fun rename(from: String, to: String) {
        val key = pick(from)
        if (key == to.trim()) return
        val clean = validSheetName(to, sheetParts.keys.toList())
        val element = sheetElement(key) ?: throw IllegalArgumentException("Sheet $key is missing from the workbook")
        element.setAttribute("name", clean)
        val part = sheetParts.remove(key)
        val document = sheetDocs.remove(key)
        if (part != null) sheetParts[clean] = part
        if (document != null) sheetDocs[clean] = document
    }

    fun delete(name: String) {
        val key = pick(name)
        if (sheetParts.size <= 1) throw IllegalArgumentException("A workbook needs at least one sheet")
        val element = sheetElement(key) ?: throw IllegalArgumentException("Sheet $key is missing from the workbook")
        val relId = attr(element, "r:id")
        element.parentNode?.removeChild(element)
        if (relId.isNotEmpty()) {
            children(workbookRels.documentElement, "Relationship")
                .firstOrNull { attr(it, "Id") == relId }
                ?.let { workbookRels.documentElement.removeChild(it) }
        }
        val part = sheetParts.remove(key)
        sheetDocs.remove(key)
        if (part != null) {
            parts.remove(part)
            parts.remove(relsPartFor(part))
            removeOverride(contentTypes, "/$part")
        }
    }

    fun useSharedStrings(): Boolean = shared != null

    fun sharedId(text: String): Int {
        val document = shared ?: throw IllegalArgumentException("This workbook has no shared string table")
        val container = document.documentElement
        val items = children(container, "si")
        val match = items.indexOfFirst { textOf(it) == text }
        if (match >= 0) return match
        appendXml(
            document, container,
            "<si><t xml:space=\"preserve\">${escapeXml(text)}</t></si>", "xmlns=\"$NS_SPREADSHEET\""
        )
        val total = children(container, "si").size
        container.setAttribute("count", total.toString())
        container.setAttribute("uniqueCount", total.toString())
        return total - 1
    }

    fun save() {
        parts[XL_WORKBOOK_PART] = serialize(workbook)
        parts[XL_WORKBOOK_RELS_PART] = serialize(workbookRels)
        parts[XL_STYLES_PART] = serialize(styles)
        parts[CONTENT_TYPES_PART] = serialize(contentTypes)
        sheetParts.forEach { entry ->
            val document = sheetDocs[entry.key]
            if (document != null) parts[entry.value] = serialize(document)
        }
        val sharedDocument = shared
        if (sharedDocument != null) parts[XL_SHARED_STRINGS_PART] = serialize(sharedDocument)
    }

    private fun sheetElement(name: String): Element? =
        children(workbook.documentElement, "sheets")
            .flatMap { children(it, "sheet") }
            .firstOrNull { attr(it, "name") == name }

    private fun nextSheetId(): Int {
        var highest = 0
        children(workbook.documentElement, "sheets").forEach { container ->
            children(container, "sheet").forEach { sheet ->
                val id = attr(sheet, "sheetId").toIntOrNull() ?: 0
                if (id > highest) highest = id
            }
        }
        return highest + 1
    }
}

private fun populateSheet(document: Document, book: StyleBook, spec: JSONObject) {
    val startRow = spec.optInt("row_start", 1).coerceAtLeast(1)
    val columns = spec.optJSONArray("columns")
    if (columns != null) {
        for (index in 0 until columns.length()) {
            val column = columns.optJSONObject(index) ?: continue
            val width = column.optDouble("width", 0.0)
            if (width > 0.0) setColumnWidth(document, columnName(index + 1), width)
        }
    }
    val heights = spec.optJSONObject("heights") ?: spec.optJSONObject("row_heights")
    if (heights != null) {
        heights.keys().forEach { key ->
            val row = key.toIntOrNull()
            val height = heights.optDouble(key, 0.0)
            if (row != null && row > 0 && height > 0.0) setRowHeight(document, row, height)
        }
    }
    val rows = spec.optJSONArray("rows")
    if (rows != null) {
        for (index in 0 until rows.length()) {
            val values = rows.optJSONArray(index) ?: continue
            for (column in 0 until values.length()) {
                setCell(document, Ref(startRow + index, column + 1), cellData(values.opt(column)), 0, null)
            }
        }
    }
    val cells = spec.optJSONArray("cells")
    if (cells != null) {
        for (index in 0 until cells.length()) {
            val cell = cells.optJSONObject(index)
                ?: throw IllegalArgumentException("Cell ${index + 1} in the sheet spec is not a JSON object")
            val refText = stringOf(cell, "ref")
            if (refText.isBlank()) throw IllegalArgumentException("Cell ${index + 1} needs a \"ref\" such as B2")
            val data = cellData(cell.opt("value"))
            val formula = stringOf(cell, "formula")
            if (formula.isNotEmpty()) data.formula = formula
            setCell(document, parseRef(refText), data, book.xf(cell.optJSONObject("style")), null)
        }
    }
    val merges = spec.optJSONArray("merges")
    if (merges != null) {
        for (index in 0 until merges.length()) {
            val range = merges.optString(index, "")
            if (range.isNotBlank()) appendMerge(document, range)
        }
    }
    val freeze = stringOf(spec, "freeze")
    if (freeze.isNotBlank()) setFreeze(document, freeze)
    val filter = spec.opt("autofilter")
    if (filter == true) {
        setAutoFilter(document, autoFilterRange(document))
    } else if (filter is String && filter.isNotBlank()) {
        setAutoFilter(document, filter)
    }
    val conditional = spec.optJSONArray("conditional")
    if (conditional != null) {
        for (index in 0 until conditional.length()) {
            val rule = conditional.optJSONObject(index)
                ?: throw IllegalArgumentException("Conditional rule ${index + 1} is not a JSON object")
            addConditional(document, rule, book)
        }
    }
}

private fun setCell(document: Document, ref: Ref, data: CellData, styleIndex: Int, shared: ((String) -> Int)?) {
    val ns = SpreadsheetNs.of(document)
    val row = ensureRow(document, ref.row)
    val cell = ensureCell(document, row, ref)
    while (cell.firstChild != null) cell.removeChild(cell.firstChild)
    if (styleIndex >= 0) {
        if (styleIndex > 0) cell.setAttribute("s", styleIndex.toString()) else cell.removeAttribute("s")
    }
    val formula = data.formula.trim().removePrefix("=")
    val text = data.text
    val number = data.number
    val flag = data.flag
    if (formula.isNotEmpty()) {
        cell.removeAttribute("t")
        appendXml(document, cell, "<f>${escapeXml(formula)}</f>", ns.declaration)
        if (number != null) {
            appendXml(document, cell, "<v>${numberText(number)}</v>", ns.declaration)
        } else if (text != null) {
            cell.setAttribute("t", "str")
            appendXml(document, cell, "<v>${escapeXml(text)}</v>", ns.declaration)
        }
        return
    }
    when {
        number != null -> {
            cell.removeAttribute("t")
            appendXml(document, cell, "<v>${numberText(number)}</v>", ns.declaration)
        }
        flag != null -> {
            cell.setAttribute("t", "b")
            appendXml(document, cell, "<v>${if (flag) "1" else "0"}</v>", ns.declaration)
        }
        text != null -> {
            val index = shared?.invoke(text)
            if (index != null) {
                cell.setAttribute("t", "s")
                appendXml(document, cell, "<v>$index</v>", ns.declaration)
            } else {
                cell.setAttribute("t", "inlineStr")
                appendXml(
                    document, cell,
                    "<is><t xml:space=\"preserve\">${escapeXml(text)}</t></is>", ns.declaration
                )
            }
        }
        else -> {
            cell.removeAttribute("t")
        }
    }
}

private fun ensureRow(document: Document, rowIndex: Int): Element {
    val ns = SpreadsheetNs.of(document)
    val sheetData = ensureOrdered(document, document.documentElement, ns.tag("sheetData"), WORKSHEET_ORDER)
    val rows = children(sheetData, "row")
    val existing = rows.firstOrNull { attr(it, "r").toIntOrNull() == rowIndex }
    if (existing != null) return existing
    val element = document.createElement(ns.tag("row"))
    element.setAttribute("r", rowIndex.toString())
    val anchor = rows.firstOrNull { (attr(it, "r").toIntOrNull() ?: 0) > rowIndex }
    if (anchor != null) sheetData.insertBefore(element, anchor) else sheetData.appendChild(element)
    return element
}

private fun ensureCell(document: Document, row: Element, ref: Ref): Element {
    val ns = SpreadsheetNs.of(document)
    val entries = cellEntries(row)
    val existing = entries.firstOrNull { it.second == ref.col }
    if (existing != null) return existing.first
    val element = document.createElement(ns.tag("c"))
    element.setAttribute("r", columnName(ref.col) + ref.row)
    val anchor = entries.firstOrNull { it.second > ref.col }
    if (anchor != null) row.insertBefore(element, anchor.first) else row.appendChild(element)
    return element
}

private fun cellEntries(row: Element): List<Pair<Element, Int>> {
    val out = mutableListOf<Pair<Element, Int>>()
    var position = 0
    var child = row.firstChild
    while (child != null) {
        if (child is Element && localName(child) == "c") {
            position++
            val declared = parseRefOrNull(attr(child, "r"))
            out.add(child to (declared?.col ?: position))
        }
        child = child.nextSibling
    }
    return out
}

private fun setColumnWidth(document: Document, column: String, width: Double) {
    val index = columnIndex(column)
    if (index <= 0) throw IllegalArgumentException("Bad column: $column")
    val ns = SpreadsheetNs.of(document)
    val container = ensureOrdered(document, document.documentElement, ns.tag("cols"), WORKSHEET_ORDER)
    val existing = children(container, "col").firstOrNull {
        val min = attr(it, "min").toIntOrNull() ?: 0
        val max = attr(it, "max").toIntOrNull() ?: 0
        index in min..max
    }
    if (existing != null) {
        existing.setAttribute("width", numberText(width))
        existing.setAttribute("customWidth", "1")
        return
    }
    val created = document.createElement(ns.tag("col"))
    created.setAttribute("min", index.toString())
    created.setAttribute("max", index.toString())
    created.setAttribute("width", numberText(width))
    created.setAttribute("customWidth", "1")
    container.appendChild(created)
}

private fun setRowHeight(document: Document, rowIndex: Int, height: Double) {
    val row = ensureRow(document, rowIndex)
    row.setAttribute("ht", numberText(height))
    row.setAttribute("customHeight", "1")
}

private fun appendMerge(document: Document, range: String) {
    val parsed = parseRange(range) ?: throw IllegalArgumentException("Bad merge range: $range")
    val text = rangeText(parsed)
    val ns = SpreadsheetNs.of(document)
    val container = ensureOrdered(document, document.documentElement, ns.tag("mergeCells"), WORKSHEET_ORDER)
    val exists = children(container, "mergeCell").any { attr(it, "ref").equals(text, ignoreCase = true) }
    if (exists) return
    val created = document.createElement(ns.tag("mergeCell"))
    created.setAttribute("ref", text)
    container.appendChild(created)
    container.setAttribute("count", children(container, "mergeCell").size.toString())
}

private fun setFreeze(document: Document, cell: String) {
    val ref = parseRef(cell)
    val ns = SpreadsheetNs.of(document)
    val views = ensureOrdered(document, document.documentElement, ns.tag("sheetViews"), WORKSHEET_ORDER)
    val existing = children(views, "sheetView").firstOrNull()
    val view = if (existing != null) {
        existing
    } else {
        val created = document.createElement(ns.tag("sheetView"))
        created.setAttribute("workbookViewId", "0")
        views.appendChild(created)
        created
    }
    removeChildren(view, "pane")
    removeChildren(view, "selection")
    if (ref.row <= 1 && ref.col <= 1) return
    val xSplit = ref.col - 1
    val ySplit = ref.row - 1
    val pane = when {
        xSplit > 0 && ySplit > 0 -> "bottomRight"
        xSplit > 0 -> "topRight"
        else -> "bottomLeft"
    }
    val topLeft = columnName(ref.col) + ref.row
    val builder = node(ns.tag("pane"))
    if (xSplit > 0) builder.attr("xSplit", xSplit)
    if (ySplit > 0) builder.attr("ySplit", ySplit)
    builder.attr("topLeftCell", topLeft).attr("activePane", pane).attr("state", "frozen")
    appendXml(document, view, builder.render(), ns.declaration)
    val selection = node(ns.tag("selection")).attr("pane", pane).attr("activeCell", topLeft).attr("sqref", topLeft)
    appendXml(document, view, selection.render(), ns.declaration)
}

private fun setAutoFilter(document: Document, range: String) {
    val parsed = parseRange(range) ?: throw IllegalArgumentException("Bad autofilter range: $range")
    val ns = SpreadsheetNs.of(document)
    val container = ensureOrdered(document, document.documentElement, ns.tag("autoFilter"), WORKSHEET_ORDER)
    container.setAttribute("ref", rangeText(parsed))
}

private fun clearAutoFilter(document: Document) {
    val existing = children(document.documentElement, "autoFilter").firstOrNull() ?: return
    document.documentElement.removeChild(existing)
}

private fun addConditional(document: Document, rule: JSONObject, book: StyleBook) {
    val range = stringOf(rule, "range")
    val parsed = parseRange(range) ?: throw IllegalArgumentException("Bad conditional formatting range: $range")
    val type = stringOf(rule, "type", "cellIs").trim().lowercase()
    val ns = SpreadsheetNs.of(document)
    var priority = 1
    children(document.documentElement, "conditionalFormatting").forEach { container ->
        priority += children(container, "cfRule").size
    }
    val container = node(ns.tag("conditionalFormatting")).attr("sqref", rangeText(parsed))
    when (type) {
        "cellis", "cell_is" -> {
            val operator = operatorValue(stringOf(rule, "operator", "greaterThan"))
            val formulas = formulaList(rule.opt("formula"))
            if (formulas.isEmpty()) throw IllegalArgumentException("A cellIs conditional rule needs a \"formula\"")
            val dxfId = book.dxf(
                stringOf(rule, "colour").ifEmpty { stringOf(rule, "color") },
                stringOf(rule, "fill")
            )
            val created = container.child(ns.tag("cfRule"))
            created.attr("type", "cellIs").attr("dxfId", dxfId).attr("priority", priority).attr("operator", operator)
            formulas.forEach { formula -> created.child(ns.tag("formula")).text(formula) }
        }
        "colorscale", "color_scale" -> {
            val created = container.child(ns.tag("cfRule"))
            created.attr("type", "colorScale").attr("priority", priority)
            val scale = created.child(ns.tag("colorScale"))
            scale.child(ns.tag("cfvo")).attr("type", "min")
            val mid = stringOf(rule, "mid")
            if (mid.isNotBlank()) scale.child(ns.tag("cfvo")).attr("type", "percentile").attr("val", 50)
            scale.child(ns.tag("cfvo")).attr("type", "max")
            scale.child(ns.tag("color")).attr("rgb", colourValue(stringOf(rule, "min", "#FFFFFF")).ifEmpty { "FFFFFFFF" })
            if (mid.isNotBlank()) {
                scale.child(ns.tag("color")).attr("rgb", colourValue(mid).ifEmpty { "FFFFEB84" })
            }
            scale.child(ns.tag("color")).attr("rgb", colourValue(stringOf(rule, "max", "#000000")).ifEmpty { "FF000000" })
        }
        else -> throw IllegalArgumentException("Unsupported conditional formatting type: $type")
    }
    addOrderedXml(
        document, document.documentElement, container.render(), "conditionalFormatting",
        WORKSHEET_ORDER, ns.declaration
    )
}

private fun updateDimension(document: Document) {
    val ns = SpreadsheetNs.of(document)
    val parsed = usedRange(document)
    val text = if (parsed == null) "A1" else rangeText(parsed)
    val existing = children(document.documentElement, "dimension").firstOrNull()
    if (existing != null) {
        existing.setAttribute("ref", text)
        return
    }
    val created = document.createElement(ns.tag("dimension"))
    created.setAttribute("ref", text)
    insertOrdered(document.documentElement, created, "dimension", WORKSHEET_ORDER)
}

private fun usedRange(document: Document): RangeRef? {
    var minRow = Int.MAX_VALUE
    var maxRow = 0
    var minColumn = Int.MAX_VALUE
    var maxColumn = 0
    children(document.documentElement, "sheetData").forEach { sheetData ->
        var rowPosition = 0
        children(sheetData, "row").forEach { row ->
            rowPosition++
            val declared = attr(row, "r").toIntOrNull() ?: rowPosition
            var cellPosition = 0
            children(row, "c").forEach { cell ->
                cellPosition++
                val parsed = parseRefOrNull(attr(cell, "r"))
                val rowIndex = parsed?.row ?: declared
                val column = parsed?.col ?: cellPosition
                if (rowIndex > 0 && column > 0) {
                    minRow = minOf(minRow, rowIndex)
                    maxRow = maxOf(maxRow, rowIndex)
                    minColumn = minOf(minColumn, column)
                    maxColumn = maxOf(maxColumn, column)
                }
            }
        }
    }
    children(document.documentElement, "mergeCells").forEach { container ->
        children(container, "mergeCell").forEach { cell ->
            val parsed = parseRange(attr(cell, "ref"))
            if (parsed != null) {
                minRow = minOf(minRow, parsed.r1)
                maxRow = maxOf(maxRow, parsed.r2)
                minColumn = minOf(minColumn, parsed.c1)
                maxColumn = maxOf(maxColumn, parsed.c2)
            }
        }
    }
    if (maxRow == 0 || maxColumn == 0) return null
    return RangeRef(minRow, minColumn, maxRow, maxColumn)
}

private fun autoFilterRange(document: Document): String {
    val parsed = usedRange(document)
    return if (parsed == null) "A1" else rangeText(RangeRef(parsed.r1, parsed.c1, parsed.r1, parsed.c2))
}

private fun lastRow(document: Document): Int {
    var last = 0
    children(document.documentElement, "sheetData").forEach { sheetData ->
        var position = 0
        children(sheetData, "row").forEach { row ->
            position++
            val declared = attr(row, "r").toIntOrNull() ?: position
            if (children(row, "c").isNotEmpty()) last = maxOf(last, declared)
        }
    }
    return last
}

private fun chartNode(spec: JSONObject, sheetName: String, index: Int): XmlBuilder {
    val type = stringOf(spec, "type", "bar").trim().lowercase()
    val series = spec.optJSONArray("series")
    if (series == null || series.length() == 0) {
        throw IllegalArgumentException("Chart $index needs a \"series\" array")
    }
    val categories = stringOf(spec, "categories")
    val root = node("c:chartSpace")
    root.attr("xmlns:c", NS_CHART).attr("xmlns:a", NS_DRAWING).attr("xmlns:r", NS_OFFICE_RELATIONSHIPS)
    root.child("c:roundedCorners").attr("val", 0)
    val chart = root.child("c:chart")
    val title = stringOf(spec, "title")
    if (title.isNotBlank()) {
        chart.child("c:title").apply {
            child("c:tx").child("c:rich").apply {
                child("a:bodyPr")
                child("a:lstStyle")
                child("a:p").apply {
                    child("a:r").apply {
                        child("a:rPr").attr("lang", "en-US")
                        child("a:t").text(title)
                    }
                }
            }
            child("c:layout")
        }
        chart.child("c:autoTitleDeleted").attr("val", 0)
    }
    val plot = chart.child("c:plotArea")
    plot.child("c:layout")
    val group = when (type) {
        "line" -> node("c:lineChart").apply {
            child("c:grouping").attr("val", "standard")
            child("c:varyColors").attr("val", 0)
        }
        "pie" -> node("c:pieChart").apply { child("c:varyColors").attr("val", 1) }
        else -> node("c:barChart").apply {
            child("c:barDir").attr("val", "col")
            child("c:grouping").attr("val", "clustered")
            child("c:varyColors").attr("val", 0)
        }
    }
    for (position in 0 until series.length()) {
        val item = series.optJSONObject(position) ?: continue
        group.add(chartSeries(item, position, sheetName, categories))
    }
    if (type == "line") group.child("c:marker").attr("val", 1)
    if (type != "pie") {
        group.child("c:axId").attr("val", CATEGORY_AXIS)
        group.child("c:axId").attr("val", VALUE_AXIS)
    }
    plot.add(group)
    if (type != "pie") {
        plot.add(categoryAxis())
        plot.add(valueAxis())
    }
    chart.child("c:legend").child("c:legendPos").attr("val", "b")
    chart.child("c:plotVisOnly").attr("val", 1)
    chart.child("c:dispBlanksAs").attr("val", "gap")
    return root
}

private fun chartSeries(series: JSONObject, index: Int, sheetName: String, categories: String): XmlBuilder {
    val values = stringOf(series, "values")
    if (values.isBlank()) {
        throw IllegalArgumentException("Chart series ${index + 1} needs a \"values\" range such as B2:B9")
    }
    val out = node("c:ser")
    out.child("c:idx").attr("val", index)
    out.child("c:order").attr("val", index)
    val name = stringOf(series, "name")
    if (name.isNotBlank()) out.child("c:tx").child("c:v").text(name)
    val categoryRange = stringOf(series, "categories").ifEmpty { categories }
    if (categoryRange.isNotBlank()) {
        out.child("c:cat").child("c:strRef").child("c:f").text(sheetRange(sheetName, categoryRange))
    }
    out.child("c:val").child("c:numRef").child("c:f").text(sheetRange(sheetName, values))
    return out
}

private fun categoryAxis(): XmlBuilder {
    val axis = node("c:catAx")
    axis.child("c:axId").attr("val", CATEGORY_AXIS)
    axis.child("c:scaling").child("c:orientation").attr("val", "minMax")
    axis.child("c:delete").attr("val", 0)
    axis.child("c:axPos").attr("val", "b")
    axis.child("c:crossAx").attr("val", VALUE_AXIS)
    return axis
}

private fun valueAxis(): XmlBuilder {
    val axis = node("c:valAx")
    axis.child("c:axId").attr("val", VALUE_AXIS)
    axis.child("c:scaling").child("c:orientation").attr("val", "minMax")
    axis.child("c:delete").attr("val", 0)
    axis.child("c:axPos").attr("val", "l")
    axis.child("c:crossAx").attr("val", CATEGORY_AXIS)
    return axis
}

private fun anchorNode(spec: JSONObject, relId: String, index: Int): XmlBuilder {
    val anchor = parseRef(stringOf(spec, "anchor", "E2"))
    val width = spec.optDouble("width", 12.0).toInt().coerceIn(2, 200)
    val height = spec.optDouble("height", 8.0).toInt().coerceIn(2, 200)
    val fromColumn = anchor.col - 1
    val fromRow = anchor.row - 1
    val out = node("xdr:twoCellAnchor")
    out.child("xdr:from").apply {
        child("xdr:col").text(fromColumn.toString())
        child("xdr:colOff").text("0")
        child("xdr:row").text(fromRow.toString())
        child("xdr:rowOff").text("0")
    }
    out.child("xdr:to").apply {
        child("xdr:col").text((fromColumn + width).toString())
        child("xdr:colOff").text("0")
        child("xdr:row").text((fromRow + height).toString())
        child("xdr:rowOff").text("0")
    }
    out.child("xdr:graphicFrame").attr("macro", "").apply {
        child("xdr:nvGraphicFramePr").apply {
            child("xdr:cNvPr").attr("id", index + 1).attr("name", "Chart $index")
            child("xdr:cNvGraphicFramePr")
        }
        child("xdr:xfrm").apply {
            child("a:off").attr("x", 0).attr("y", 0)
            child("a:ext").attr("cx", 0).attr("cy", 0)
        }
        child("a:graphic").child("a:graphicData").attr("uri", NS_CHART).apply {
            child("c:chart").attr("r:id", relId)
        }
    }
    out.child("xdr:clientData")
    return out
}

private fun sheetRange(sheetName: String, range: String): String {
    val absolute = range.trim().split(":").joinToString(":") { part -> absoluteRef(part) }
    val quoted = if (sheetName.any { !(it.isLetterOrDigit() || it == '_' || it == '.') }) {
        "'" + sheetName.replace("'", "''") + "'"
    } else {
        sheetName
    }
    return "$quoted!$absolute"
}

private fun absoluteRef(text: String): String {
    val clean = text.trim().replace("\$", "")
    val letters = clean.takeWhile { it in 'A'..'Z' || it in 'a'..'z' }.uppercase()
    val digits = clean.dropWhile { it in 'A'..'Z' || it in 'a'..'z' }
    return "\$$letters\$$digits"
}

private fun cellText(cell: Element, shared: List<String>): String {
    val type = attr(cell, "t").lowercase()
    return when (type) {
        "s" -> {
            val index = textOf(children(cell, "v").firstOrNull()).trim().toIntOrNull() ?: -1
            if (index in shared.indices) shared[index] else ""
        }
        "inlinestr" -> textOf(children(cell, "is").firstOrNull())
        "str" -> textOf(children(cell, "v").firstOrNull())
        "b" -> if (textOf(children(cell, "v").firstOrNull()).trim() == "1") "TRUE" else "FALSE"
        "e" -> textOf(children(cell, "v").firstOrNull())
        else -> {
            val inline = children(cell, "is").firstOrNull()
            if (inline != null) textOf(inline) else textOf(children(cell, "v").firstOrNull())
        }
    }
}

private fun readSharedStrings(parts: Map<String, ByteArray>): List<String> {
    val bytes = parts[XL_SHARED_STRINGS_PART] ?: return emptyList()
    val document = try {
        parse(bytes)
    } catch (e: Exception) {
        return emptyList()
    }
    return children(document.documentElement, "si").map { item -> textOf(item) }
}

private fun relationshipTarget(rels: Document?, relId: String): String {
    if (rels == null || relId.isEmpty()) return ""
    val rel = children(rels.documentElement, "Relationship").firstOrNull { attr(it, "Id") == relId } ?: return ""
    val target = attr(rel, "Target").trim().replace('\\', '/')
    if (target.isEmpty()) return ""
    if (target.startsWith("/")) return target.removePrefix("/")
    if (target.startsWith("xl/")) return target
    return "xl/$target"
}

private fun relsPartFor(part: String): String {
    val slash = part.lastIndexOf('/')
    if (slash < 0) return "_rels/$part.rels"
    val folder = part.substring(0, slash)
    val name = part.substring(slash + 1)
    return "$folder/_rels/$name.rels"
}

private fun addOverride(contentTypes: Document, partName: String, contentType: String) {
    val exists = children(contentTypes.documentElement, "Override").any { attr(it, "PartName") == partName }
    if (exists) return
    val element = contentTypes.createElement("Override")
    element.setAttribute("PartName", partName)
    element.setAttribute("ContentType", contentType)
    contentTypes.documentElement.appendChild(element)
}

private fun removeOverride(contentTypes: Document, partName: String) {
    val existing = children(contentTypes.documentElement, "Override").firstOrNull { attr(it, "PartName") == partName }
    if (existing != null) contentTypes.documentElement.removeChild(existing)
}

private fun addRelationship(rels: Document, type: String, target: String): String {
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
    rels.documentElement.appendChild(element)
    return id
}

private fun ensureRelationshipPrefix(document: Document) {
    val root = document.documentElement ?: return
    if (attr(root, "xmlns:r").isEmpty()) root.setAttribute("xmlns:r", NS_OFFICE_RELATIONSHIPS)
}

private fun addOrderedXml(
    document: Document,
    parent: Element,
    xml: String,
    tag: String,
    order: List<String>,
    namespaces: String
): Element? {
    val parsed = parseFragment(xml, namespaces).firstOrNull() ?: return null
    val imported = document.importNode(parsed, true) as? Element ?: return null
    insertOrdered(parent, imported, tag, order)
    return imported
}

private fun indexOfChild(parent: Element, child: Element): Int {
    var index = 0
    var node = parent.firstChild
    while (node != null) {
        if (node is Element) {
            if (node === child) return index
            index++
        }
        node = node.nextSibling
    }
    return -1
}

private fun syncCount(container: Element) {
    container.setAttribute("count", directChildren(container).size.toString())
}

private fun columnName(index: Int): String {
    var value = index
    val out = StringBuilder()
    while (value > 0) {
        val remainder = (value - 1) % 26
        out.insert(0, ('A' + remainder))
        value = (value - 1) / 26
    }
    return if (out.isEmpty()) "A" else out.toString()
}

private fun columnIndex(name: String): Int {
    var value = 0
    name.trim().uppercase().forEach { ch ->
        if (ch in 'A'..'Z') value = value * 26 + (ch - 'A' + 1)
    }
    return value
}

private fun parseRef(text: String): Ref {
    val clean = text.trim().replace("\$", "").uppercase()
    if (clean.isEmpty()) throw IllegalArgumentException("Empty cell reference")
    val letters = clean.takeWhile { it in 'A'..'Z' }
    val digits = clean.dropWhile { it in 'A'..'Z' }
    val column = columnIndex(letters)
    val row = digits.toIntOrNull() ?: 0
    if (column <= 0 || row <= 0 || row > 1048576) throw IllegalArgumentException("Bad cell reference: $text")
    return Ref(row, column)
}

private fun parseRefOrNull(text: String): Ref? {
    if (text.isBlank()) return null
    return try {
        parseRef(text)
    } catch (e: IllegalArgumentException) {
        null
    }
}

private fun parseRange(text: String): RangeRef? {
    val clean = text.trim().replace("\$", "")
    if (clean.isEmpty()) return null
    val parts = clean.split(":")
    return try {
        when (parts.size) {
            1 -> {
                val single = parseRef(parts[0])
                RangeRef(single.row, single.col, single.row, single.col)
            }
            2 -> {
                val first = parseRef(parts[0])
                val second = parseRef(parts[1])
                RangeRef(
                    minOf(first.row, second.row), minOf(first.col, second.col),
                    maxOf(first.row, second.row), maxOf(first.col, second.col)
                )
            }
            else -> null
        }
    } catch (e: IllegalArgumentException) {
        null
    }
}

private fun rangeText(range: RangeRef): String {
    val start = columnName(range.c1) + range.r1
    val end = columnName(range.c2) + range.r2
    return if (start == end) start else "$start:$end"
}

private fun numberText(value: Double): String {
    if (value.isNaN() || value.isInfinite()) return "0"
    if (value == Math.floor(value) && Math.abs(value) < 1.0E15) return value.toLong().toString()
    return java.math.BigDecimal(value.toString()).stripTrailingZeros().toPlainString()
}

private fun colourValue(text: String): String {
    val clean = text.trim().removePrefix("#").uppercase()
    if (clean.length == 6 && clean.all { it in "0123456789ABCDEF" }) return "FF$clean"
    if (clean.length == 8 && clean.all { it in "0123456789ABCDEF" }) return clean
    return ""
}

private fun cellData(value: Any?): CellData = when (value) {
    null -> CellData()
    JSONObject.NULL -> CellData()
    is Boolean -> CellData(flag = value)
    is Number -> CellData(number = value.toDouble())
    else -> CellData(text = value.toString())
}

private fun textCellData(text: String): CellData {
    val number = numericValue(text.trim())
    return if (number != null) CellData(number = number) else CellData(text = text)
}

private fun numericValue(text: String): Double? {
    if (text.isEmpty()) return null
    if (text.length > 1 && text.startsWith("0") && !text.startsWith("0.")) return null
    if (text.startsWith("+")) return null
    val value = text.toDoubleOrNull() ?: return null
    if (value.isNaN() || value.isInfinite()) return null
    return value
}

private fun operatorValue(value: String): String {
    val clean = value.trim()
    if (clean.isEmpty()) return "greaterThan"
    return CELL_OPERATORS.firstOrNull { it.equals(clean, ignoreCase = true) } ?: "greaterThan"
}

private fun formulaList(value: Any?): List<String> {
    if (value == null || value == JSONObject.NULL) return emptyList()
    if (value is JSONArray) {
        val out = mutableListOf<String>()
        for (index in 0 until value.length()) {
            val item = value.opt(index) ?: continue
            if (item != JSONObject.NULL) out.add(item.toString())
        }
        return out
    }
    val text = value.toString()
    if (text.isBlank()) return emptyList()
    if (text.contains(',')) return text.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    return listOf(text)
}

private fun validSheetName(name: String, used: List<String>): String {
    val clean = name.trim()
    if (clean.isEmpty()) throw IllegalArgumentException("A sheet name cannot be empty")
    if (clean.length > 31) throw IllegalArgumentException("A sheet name cannot be longer than 31 characters")
    if (clean.any { it == '[' || it == ']' || it == ':' || it == '*' || it == '?' || it == '/' || it == '\\' }) {
        throw IllegalArgumentException("A sheet name cannot contain [ ] : * ? / or backslash")
    }
    if (used.any { it.equals(clean, ignoreCase = true) }) {
        throw IllegalArgumentException("A sheet named \"$clean\" already exists")
    }
    return clean
}

private fun parseCsv(text: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val field = StringBuilder()
    var quoted = false
    var index = 0
    val clean = text.replace("\r\n", "\n").replace('\r', '\n')
    while (index < clean.length) {
        val ch = clean[index]
        if (quoted) {
            if (ch == '"') {
                if (index + 1 < clean.length && clean[index + 1] == '"') {
                    field.append('"')
                    index += 2
                    continue
                }
                quoted = false
                index++
                continue
            }
            field.append(ch)
            index++
            continue
        }
        when (ch) {
            '"' -> {
                quoted = true
                index++
            }
            ',' -> {
                row.add(field.toString())
                field.setLength(0)
                index++
            }
            '\n' -> {
                row.add(field.toString())
                field.setLength(0)
                rows.add(row)
                row = mutableListOf()
                index++
            }
            else -> {
                field.append(ch)
                index++
            }
        }
    }
    row.add(field.toString())
    if (row.size > 1 || row[0].isNotEmpty()) rows.add(row)
    return rows
}

private fun csvField(value: String): String {
    val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' } || value != value.trim()
    if (!needsQuotes) return value
    return "\"" + value.replace("\"", "\"\"") + "\""
}

private fun emptyWorkbookBytes(): ByteArray = documentBytes(
    node("workbook").attr("xmlns", NS_SPREADSHEET).attr("xmlns:r", NS_OFFICE_RELATIONSHIPS)
)

private fun emptyWorksheetBytes(): ByteArray = documentBytes(
    node("worksheet").attr("xmlns", NS_SPREADSHEET).attr("xmlns:r", NS_OFFICE_RELATIONSHIPS)
)

private fun emptyDrawingBytes(): ByteArray = documentBytes(
    node("xdr:wsDr")
        .attr("xmlns:xdr", NS_SPREADSHEET_DRAWING)
        .attr("xmlns:a", NS_DRAWING)
        .attr("xmlns:c", NS_CHART)
        .attr("xmlns:r", NS_OFFICE_RELATIONSHIPS)
)

private fun emptyRelationshipsBytes(): ByteArray =
    documentBytes(node("Relationships").attr("xmlns", NS_PACKAGE_RELATIONSHIPS))

private fun minimalContentTypesBytes(): ByteArray {
    val root = node("Types").attr("xmlns", NS_CONTENT_TYPES)
    root.child("Default").attr("Extension", "rels")
        .attr("ContentType", "application/vnd.openxmlformats-package.relationships+xml")
    root.child("Default").attr("Extension", "xml").attr("ContentType", "application/xml")
    return documentBytes(root)
}

private fun sheetPackageRels(): XmlBuilder {
    val root = node("Relationships").attr("xmlns", NS_PACKAGE_RELATIONSHIPS)
    root.child("Relationship").attr("Id", "rId1").attr("Type", REL_OFFICE_DOCUMENT).attr("Target", XL_WORKBOOK_PART)
    root.child("Relationship").attr("Id", "rId2").attr("Type", REL_CORE_PROPERTIES).attr("Target", XL_CORE_PART)
    root.child("Relationship").attr("Id", "rId3").attr("Type", REL_EXTENDED_PROPERTIES).attr("Target", XL_APP_PART)
    return root
}

private fun sheetCoreProperties(title: String, author: String): XmlBuilder {
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

private const val XL_APP_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties" xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes">
    <Application>Lucent</Application>
    <DocSecurity>0</DocSecurity>
    <ScaleCrop>false</ScaleCrop>
    <LinksUpToDate>false</LinksUpToDate>
    <SharedDoc>false</SharedDoc>
    <HyperlinksChanged>false</HyperlinksChanged>
    <AppVersion>16.0000</AppVersion>
</Properties>
"""

private const val XL_STYLES_SKELETON = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
    <fonts count="1">
        <font>
            <sz val="11"/>
            <color rgb="FF000000"/>
            <name val="Calibri"/>
            <family val="2"/>
        </font>
    </fonts>
    <fills count="2">
        <fill><patternFill patternType="none"/></fill>
        <fill><patternFill patternType="gray125"/></fill>
    </fills>
    <borders count="1">
        <border><left/><right/><top/><bottom/><diagonal/></border>
    </borders>
    <cellStyleXfs count="1">
        <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
    </cellStyleXfs>
    <cellXfs count="1">
        <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    </cellXfs>
    <cellStyles count="1">
        <cellStyle name="Normal" xfId="0" builtinId="0"/>
    </cellStyles>
    <dxfs count="0"/>
    <tableStyles count="0" defaultTableStyle="TableStyleMedium2" defaultPivotStyle="PivotStyleLight16"/>
</styleSheet>
"""
