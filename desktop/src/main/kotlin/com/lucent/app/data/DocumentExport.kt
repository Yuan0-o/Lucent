package com.lucent.app.data


import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt

enum class ExportFormat(val label: String, val extension: String, val mime: String) {
    MARKDOWN("Markdown (.md)", "md", "text/markdown"),
    WORD("Word (.docx)", "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    PDF("PDF (.pdf)", "pdf", "application/pdf"),
    EXCEL("Excel (.xlsx)", "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
}

object DocumentExport {

    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private fun formatTime(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(stamp)


    fun exportNotes(notes: List<Note>, format: ExportFormat): ByteArray {
        val live = notes.filter { it.trashedAt == null }
            .sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })
        return when (format) {
            ExportFormat.MARKDOWN -> MarkdownExport.render(notes).toByteArray(Charsets.UTF_8)
            ExportFormat.WORD -> notesDocx(live)
            ExportFormat.PDF -> notesPdf(live)
            ExportFormat.EXCEL -> notesXlsx(live)
        }
    }

    fun exportTasks(tasks: List<Task>, format: ExportFormat): ByteArray {
        val live = tasks.filter { it.trashedAt == null }
            .sortedWith(
                compareByDescending<Task> { it.pinned }.thenBy { it.isDone }.thenByDescending { it.createdAt }
            )
        return when (format) {
            ExportFormat.MARKDOWN -> MarkdownExport.renderTasks(tasks).toByteArray(Charsets.UTF_8)
            ExportFormat.WORD -> tasksDocx(live)
            ExportFormat.PDF -> tasksPdf(live)
            ExportFormat.EXCEL -> tasksXlsx(live)
        }
    }


    private data class Block(
        val title: String,
        val meta: String,
        val body: String,
        val bodySpans: String = "",
        val doodlePages: List<String> = emptyList(),
        val doodleNames: List<String> = emptyList(),
        val checklist: List<Pair<Boolean, String>>,
        val checklistLabel: String,
        val attachments: List<String>
    )

    private fun noteBlock(note: Note): Block {
        val doodleCanvases = DoodleExport.canvasesOf(note)
        val meta = buildList {
            add(com.lucent.app.i18n.S.exportDocUpdated(formatTime(note.updatedAt)))
            if (note.pinned) add(com.lucent.app.i18n.S.exportDocPinned)
            if (note.archived) add(com.lucent.app.i18n.S.exportDocArchived)
            val tags = NoteTags.parse(note.tags)
            if (tags.isNotEmpty()) add(tags.joinToString(" ") { "#" + NoteTags.label(it) })
        }.joinToString(" · ")
        val checklist = if (note.isChecklist) Checklist.parse(note.checklist).map { it.done to it.text } else emptyList()
        return Block(
            title = note.title.ifBlank { com.lucent.app.i18n.S.untitled },
            meta = meta,
            body = note.body.trim(),
            bodySpans = note.bodySpans,
            doodlePages = doodleCanvases.map { it.strokesJson },
            doodleNames = doodleCanvases.map { it.fileName },
            checklist = checklist,
            checklistLabel = if (note.isChecklist) com.lucent.app.i18n.S.exportDocChecklist else "",
            attachments = Attachments.parse(note.attachments).map { it.name }
        )
    }

    private fun taskBlock(task: Task): Block {
        val meta = buildList {
            add(com.lucent.app.i18n.S.exportDocCreated(formatTime(task.createdAt)))
            task.dueAt?.let { add(com.lucent.app.i18n.S.exportDocDue(formatTime(it))) }
            if (task.pinned) add(com.lucent.app.i18n.S.exportDocPinned)
            TaskPriority.fromValue(task.priority).takeIf { it != TaskPriority.NONE }?.let { add(com.lucent.app.i18n.S.exportDocPriority(it.label)) }
            RepeatRule.fromKey(task.repeatRule).takeIf { it != RepeatRule.NONE }?.let { add(com.lucent.app.i18n.S.exportDocRepeats(it.label)) }
            add(if (task.isDone) com.lucent.app.i18n.S.exportDocDone else com.lucent.app.i18n.S.exportDocOpen)
        }.joinToString(" · ")
        val box = if (task.isDone) "\u2611" else "\u2610"
        return Block(
            title = "$box ${task.title.ifBlank { com.lucent.app.i18n.S.exportDocUntitledTask }}",
            meta = meta,
            body = task.notes.trim(),
            bodySpans = task.notesSpans,
            checklist = Checklist.parse(task.subtasks).map { it.done to it.text },
            checklistLabel = com.lucent.app.i18n.S.exportDocSubtasks,
            attachments = Attachments.parse(task.attachments).map { it.name }
        )
    }


    private fun notesDocx(notes: List<Note>): ByteArray =
        docx(com.lucent.app.i18n.S.exportDocNotesTitle, notes.size, "note", notes.map { noteBlock(it) })

    private fun tasksDocx(tasks: List<Task>): ByteArray =
        docx(com.lucent.app.i18n.S.exportDocTasksTitle, tasks.size, "task", tasks.map { taskBlock(it) })

    private fun docx(heading: String, count: Int, noun: String, blocks: List<Block>): ByteArray {
        val body = StringBuilder()
        body.append(docxPara(heading, bold = true, sizeHalfPt = 40))
        body.append(docxPara(((if (noun == "note") com.lucent.app.i18n.S.exportDocNoteCount(count) else com.lucent.app.i18n.S.exportDocTaskCount(count)) + ", " + com.lucent.app.i18n.S.exportDocExportedAt(formatTime(System.currentTimeMillis()))), italic = true, sizeHalfPt = 18))
        body.append(docxPara(com.lucent.app.i18n.S.exportDocAttachmentsNote, italic = true, sizeHalfPt = 18))

        if (blocks.isEmpty()) {
            body.append(docxPara((if (noun == "note") com.lucent.app.i18n.S.exportDocNoNotes else com.lucent.app.i18n.S.exportDocNoTasks), italic = true))
        } else {
            for (b in blocks) {
                body.append(docxPara(b.title, bold = true, sizeHalfPt = 30, spaceBeforeTwips = 240))
                if (b.meta.isNotBlank()) body.append(docxPara(b.meta, italic = true, sizeHalfPt = 18))
                if (b.body.isNotBlank()) body.append(docxRichPara(b.body, b.bodySpans))
                if (b.doodleNames.isNotEmpty()) {
                    body.append(docxPara(com.lucent.app.i18n.S.exportDocDoodleCanvases(b.doodleNames.size), bold = true, sizeHalfPt = 20))
                    body.append(docxPara(com.lucent.app.i18n.S.exportDocDoodleLine(b.doodleNames.joinToString(", ")), italic = true, sizeHalfPt = 18))
                }
                if (b.checklist.isNotEmpty()) {
                    body.append(docxPara("${b.checklistLabel}:", bold = true, sizeHalfPt = 20))
                    for ((done, text) in b.checklist) {
                        body.append(docxPara("${if (done) "\u2611" else "\u2610"} $text"))
                    }
                }
                if (b.attachments.isNotEmpty()) {
                    body.append(docxPara(com.lucent.app.i18n.S.exportDocAttachmentsLine(b.attachments.joinToString(", ")), bold = true, sizeHalfPt = 18))
                }
            }
        }

        val documentXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>${body}<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/></w:sectPr></w:body></w:document>"""

        return zip(
            "[Content_Types].xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>""",
            "_rels/.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""",
            "word/document.xml" to documentXml
        )
    }

    private fun docxPara(
        text: String,
        bold: Boolean = false,
        italic: Boolean = false,
        sizeHalfPt: Int? = null,
        spaceBeforeTwips: Int = 40
    ): String {
        val rPr = buildString {
            if (bold || italic || sizeHalfPt != null) {
                append("<w:rPr>")
                if (bold) append("<w:b/>")
                if (italic) append("<w:i/>")
                if (sizeHalfPt != null) append("<w:sz w:val=\"$sizeHalfPt\"/>")
                append("</w:rPr>")
            }
        }
        val runs = StringBuilder()
        val lines = if (text.isEmpty()) listOf("") else text.split("\n")
        lines.forEachIndexed { i, line ->
            if (i > 0) runs.append("<w:r><w:br/></w:r>")
            runs.append("<w:r>").append(rPr).append("<w:t xml:space=\"preserve\">").append(xmlEscape(line)).append("</w:t></w:r>")
        }
        return "<w:p><w:pPr><w:spacing w:before=\"$spaceBeforeTwips\" w:after=\"40\"/></w:pPr>$runs</w:p>"
    }


    private fun docxRichPara(text: String, spansJson: String, spaceBeforeTwips: Int = 40): String {
        val spans = RichText.load(spansJson, text)
        if (spans.isEmpty()) return docxPara(text, spaceBeforeTwips = spaceBeforeTwips)

        val highlightNames = listOf(
            "yellow", "green", "cyan", "magenta", "darkYellow", "darkMagenta", "darkCyan", "red"
        )

        fun styleAt(i: Int): DocxRunStyle {
            var bold = false; var light = false; var italic = false; var hl = -1
            var col = RichText.TEXT_COLOR_DEFAULT
            var size = RichText.TEXT_SIZE_DEFAULT
            spans.forEach { s ->
                if (i >= s.start && i < s.end) when (s.kind) {
                    RichSpan.Kind.BOLD -> bold = true
                    RichSpan.Kind.LIGHT -> light = true
                    RichSpan.Kind.ITALIC -> italic = true
                    RichSpan.Kind.HIGHLIGHT -> hl = s.color
                    RichSpan.Kind.COLOR -> col = s.color
                    RichSpan.Kind.SIZE -> size = s.color
                }
            }
            return DocxRunStyle(bold && !light, italic, hl, col, size)
        }

        val runs = StringBuilder()
        text.split("\n").forEachIndexed { lineIndex, line ->
            if (lineIndex > 0) runs.append("<w:r><w:br/></w:r>")
            val lineStart = text.split("\n").take(lineIndex).sumOf { it.length + 1 }
            var i = 0
            while (i < line.length) {
                val style = styleAt(lineStart + i)
                var j = i + 1
                while (j < line.length && styleAt(lineStart + j) == style) j++
                val bold = style.bold; val italic = style.italic; val hl = style.highlight
                val argb = RichText.textColorArgb(style.color)
                val sizeHalfPt = (DOCX_RICH_BASE_PT * RichText.textSizeScale(style.size)).roundToInt() * 2
                val sized = style.size != RichText.TEXT_SIZE_DEFAULT
                val rPr = buildString {
                    if (bold || italic || hl >= 0 || argb != null || sized) {
                        append("<w:rPr>")
                        if (bold) append("<w:b/>")
                        if (italic) append("<w:i/>")
                        if (argb != null) append("<w:color w:val=\"" + hex6(argb) + "\"/>")
                        if (sized) append("<w:sz w:val=\"$sizeHalfPt\"/><w:szCs w:val=\"$sizeHalfPt\"/>")
                        if (hl >= 0) append("<w:highlight w:val=\"" +
                            highlightNames[hl.coerceIn(0, highlightNames.lastIndex)] + "\"/>")
                        append("</w:rPr>")
                    }
                }
                runs.append("<w:r>").append(rPr)
                    .append("<w:t xml:space=\"preserve\">")
                    .append(xmlEscape(line.substring(i, j)))
                    .append("</w:t></w:r>")
                i = j
            }
        }
        return "<w:p><w:pPr><w:spacing w:before=\"$spaceBeforeTwips\" w:after=\"40\"/></w:pPr>$runs</w:p>"
    }


    private fun notesXlsx(notes: List<Note>): ByteArray {
        val header = listOf(com.lucent.app.i18n.S.exportColTitle, com.lucent.app.i18n.S.exportColUpdated, com.lucent.app.i18n.S.exportColTags, com.lucent.app.i18n.S.exportColPinned, com.lucent.app.i18n.S.exportColArchived, com.lucent.app.i18n.S.exportColContent, com.lucent.app.i18n.S.exportColAttachments)
        val rows = notes.map { n ->
            val canvases = DoodleExport.canvasesOf(n)
            val content = if (n.isChecklist) {
                Checklist.parse(n.checklist).joinToString("\n") { "${if (it.done) "[x]" else "[ ]"} ${it.text}" }
            } else if (canvases.isNotEmpty()) {
                listOf(
                    com.lucent.app.i18n.S.exportDocDoodleCanvases(canvases.size),
                    com.lucent.app.i18n.S.exportDocDoodleLine(canvases.joinToString(", ") { it.fileName })
                ).plus(n.body.trim().takeIf { it.isNotBlank() } ?: "").filter { it.isNotBlank() }.joinToString("\n")
            } else n.body.trim()
            val tags = NoteTags.parse(n.tags).joinToString(" ") { "#" + NoteTags.label(it) }
            listOf(
                n.title.ifBlank { com.lucent.app.i18n.S.untitled },
                formatTime(n.updatedAt),
                tags,
                if (n.pinned) com.lucent.app.i18n.S.exportDocYes else "",
                if (n.archived) com.lucent.app.i18n.S.exportDocYes else "",
                content,
                Attachments.parse(n.attachments).joinToString(", ") { it.name }
            )
        }
        return xlsx(com.lucent.app.i18n.S.tabNotes, header, rows)
    }

    private fun tasksXlsx(tasks: List<Task>): ByteArray {
        val header = listOf(com.lucent.app.i18n.S.exportColTitle, com.lucent.app.i18n.S.exportColStatus, com.lucent.app.i18n.S.exportColCreated, com.lucent.app.i18n.S.exportColDue, com.lucent.app.i18n.S.exportColPriority, com.lucent.app.i18n.S.exportColRepeat, com.lucent.app.i18n.S.exportColPinned, com.lucent.app.i18n.S.exportColDetails, com.lucent.app.i18n.S.exportColSubtasks, com.lucent.app.i18n.S.exportColAttachments)
        val rows = tasks.map { t ->
            val subtasks = Checklist.parse(t.subtasks).joinToString("\n") { "${if (it.done) "[x]" else "[ ]"} ${it.text}" }
            listOf(
                t.title.ifBlank { com.lucent.app.i18n.S.exportDocUntitledTask },
                if (t.isDone) com.lucent.app.i18n.S.exportDocDone else com.lucent.app.i18n.S.exportDocOpen,
                formatTime(t.createdAt),
                t.dueAt?.let { formatTime(it) } ?: "",
                TaskPriority.fromValue(t.priority).takeIf { it != TaskPriority.NONE }?.label ?: "",
                RepeatRule.fromKey(t.repeatRule).takeIf { it != RepeatRule.NONE }?.label ?: "",
                if (t.pinned) com.lucent.app.i18n.S.exportDocYes else "",
                t.notes.trim(),
                subtasks,
                Attachments.parse(t.attachments).joinToString(", ") { it.name }
            )
        }
        return xlsx(com.lucent.app.i18n.S.tabTasks, header, rows)
    }

    private fun xlsx(sheetName: String, header: List<String>, rows: List<List<String>>): ByteArray {
        val sheetData = StringBuilder("<sheetData>")
        sheetData.append(xlsxRow(1, header))
        rows.forEachIndexed { i, cells -> sheetData.append(xlsxRow(i + 2, cells)) }
        sheetData.append("</sheetData>")

        val cols = StringBuilder("<cols>")
        for (c in header.indices) cols.append("<col min=\"${c + 1}\" max=\"${c + 1}\" width=\"24\" customWidth=\"1\"/>")
        cols.append("</cols>")

        val sheetXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">$cols$sheetData</worksheet>"""

        return zip(
            "[Content_Types].xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""",
            "_rels/.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""",
            "xl/workbook.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="${xmlEscape(sheetName)}" sheetId="1" r:id="rId1"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""",
            "xl/worksheets/sheet1.xml" to sheetXml
        )
    }

    private fun xlsxRow(rowNum: Int, cells: List<String>): String {
        val sb = StringBuilder("<row r=\"$rowNum\">")
        cells.forEachIndexed { i, value ->
            val ref = colLetter(i) + rowNum
            sb.append("<c r=\"$ref\" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(xmlEscape(value)).append("</t></is></c>")
        }
        sb.append("</row>")
        return sb.toString()
    }

    private fun colLetter(index: Int): String {
        var i = index
        val sb = StringBuilder()
        while (i >= 0) {
            sb.insert(0, ('A' + (i % 26)))
            i = i / 26 - 1
        }
        return sb.toString()
    }


    private fun notesPdf(notes: List<Note>): ByteArray =
        pdf(com.lucent.app.i18n.S.exportDocNotesTitle, notes.size, "note", notes.map { noteBlock(it) })

    private fun tasksPdf(tasks: List<Task>): ByteArray =
        pdf(com.lucent.app.i18n.S.exportDocTasksTitle, tasks.size, "task", tasks.map { taskBlock(it) })

    private const val PAGE_W = 595f
    private const val PAGE_H = 842f
    private const val MARGIN = 42f
    private const val DOCX_RICH_BASE_PT = 12f

    private data class PdfStyle(val size: Float, val bold: Boolean, val gray: Boolean = false)

    private fun pdf(heading: String, count: Int, noun: String, blocks: List<Block>): ByteArray {
        PDDocument().use { doc ->
            val fonts = loadPdfFonts(doc)
            val state = PdfState(doc, fonts)
            state.newPage()

            val titleStyle = PdfStyle(20f, bold = true)
            val itemTitleStyle = PdfStyle(15f, bold = true)
            val metaStyle = PdfStyle(10f, bold = false, gray = true)
            val bodyStyle = PdfStyle(11f, bold = false)
            val labelStyle = PdfStyle(11f, bold = true)

            state.drawWrapped(heading, titleStyle, 26f)
            state.drawWrapped(((if (noun == "note") com.lucent.app.i18n.S.exportDocNoteCount(count) else com.lucent.app.i18n.S.exportDocTaskCount(count)) + ", " + com.lucent.app.i18n.S.exportDocExportedAt(formatTime(System.currentTimeMillis()))), metaStyle, 14f)
            state.drawWrapped(com.lucent.app.i18n.S.exportDocAttachmentsNote, metaStyle, 16f)

            if (blocks.isEmpty()) {
                state.drawWrapped((if (noun == "note") com.lucent.app.i18n.S.exportDocNoNotes else com.lucent.app.i18n.S.exportDocNoTasks), metaStyle, 14f)
            } else {
                for (b in blocks) {
                    state.space(10f)
                    state.drawWrapped(b.title, itemTitleStyle, 20f)
                    if (b.meta.isNotBlank()) state.drawWrapped(b.meta, metaStyle, 14f)
                    if (b.body.isNotBlank()) {
                        if (b.bodySpans.isBlank()) {
                            for (line in b.body.split("\n")) state.drawWrapped(line, bodyStyle, 15f)
                        } else {
                            for (runs in RichText.lineRuns(b.body, b.bodySpans)) {
                                state.drawWrappedRich(runs, bodyStyle, 15f)
                            }
                        }
                    }
                    b.doodlePages.forEach { state.drawDoodle(it) }
                    if (b.doodleNames.isNotEmpty()) {
                        state.drawWrapped(com.lucent.app.i18n.S.exportDocDoodleLine(b.doodleNames.joinToString(", ")), metaStyle, 14f)
                    }
                    if (b.checklist.isNotEmpty()) {
                        state.drawWrapped("${b.checklistLabel}:", labelStyle, 15f)
                        for ((done, text) in b.checklist) state.drawWrapped("${if (done) "\u2611" else "\u2610"} $text", bodyStyle, 15f)
                    }
                    if (b.attachments.isNotEmpty()) state.drawWrapped(com.lucent.app.i18n.S.exportDocAttachmentsLine(b.attachments.joinToString(", ")), metaStyle, 15f)
                }
            }

            state.finish()
            val baos = ByteArrayOutputStream()
            doc.save(baos)
            return baos.toByteArray()
        }
    }

    private const val BUNDLED_CJK_FONT = "/fonts/LucentCJK.otf"

    @Volatile var cjkFontMissing: Boolean = false
        private set

    private fun loadPdfFonts(doc: PDDocument): List<PDFont> {
        cjkFontMissing = false
        val faces = mutableListOf<PDFont>()
        val context = android.content.DesktopContext
        for (slot in FontStore.fonts(context)) {
            try {
                FontStore.fontFile(context, slot.id)?.inputStream()?.use { stream ->
                    faces.add(PDType0Font.load(doc, stream, true))
                }
            } catch (_: Throwable) {
            }
        }
        var haveCjk = false
        try {
            DocumentExport::class.java.getResourceAsStream(BUNDLED_CJK_FONT)?.use { stream ->
                faces.add(PDType0Font.load(doc, stream, true))
                haveCjk = true
            }
        } catch (_: Throwable) {
        }
        if (!haveCjk) cjkFontMissing = true
        faces.add(PDType1Font(Standard14Fonts.FontName.HELVETICA))
        return faces
    }

    private class PdfState(val doc: PDDocument, val fonts: List<PDFont>) {
        private var content: PDPageContentStream? = null
        private var y = MARGIN

        fun newPage() {
            content?.close()
            val page = PDPage(PDRectangle(PAGE_W, PAGE_H))
            doc.addPage(page)
            content = PDPageContentStream(doc, page)
            y = MARGIN
        }

        fun space(dy: Float) { y += dy }

        private fun fontFor(text: String): PDFont {
            for (f in fonts) {
                try {
                    f.encode(text)
                    return f
                } catch (_: Throwable) {
                }
            }
            return fonts.last()
        }

        private fun width(font: PDFont, text: String, size: Float): Float = try {
            font.getStringWidth(text) / 1000f * size
        } catch (_: Throwable) {
            text.length * size * 0.6f
        }

        private fun encodable(font: PDFont, text: String): String {
            return buildString {
                for (ch in text) {
                    val s = ch.toString()
                    val ok = try { font.encode(s); true } catch (_: Throwable) { false }
                    append(if (ok) s else "\u00B7")
                }
            }
        }

        private fun drawLine(text: String, style: PdfStyle) {
            val stream = content ?: return
            val font = fontFor(text)
            val safe = if (font === fonts.last()) encodable(font, text) else text
            try {
                stream.beginText()
                if (style.gray) stream.setNonStrokingColor(0.33f, 0.33f, 0.33f)
                else stream.setNonStrokingColor(0f, 0f, 0f)
                stream.setFont(font, style.size)
                stream.newLineAtOffset(MARGIN, PAGE_H - y)
                stream.showText(safe)
                if (style.bold) {
                    stream.newLineAtOffset(0.35f, 0f)
                    stream.showText(safe)
                }
                stream.endText()
            } catch (_: Throwable) {
                try { stream.endText() } catch (_: Throwable) {}
            }
        }


        fun drawWrappedRich(runs: List<RichText.StyledRun>, base: PdfStyle, lineAdvance: Float) {
            val maxWidth = PAGE_W - 2 * MARGIN

            data class Piece(val text: String, val run: RichText.StyledRun)
            val pieces = ArrayList<Piece>()
            runs.forEach { r ->
                if (r.text.isEmpty()) return@forEach
                r.text.split(" ").forEachIndexed { i, w ->
                    if (i > 0) pieces.add(Piece(" ", r))
                    if (w.isNotEmpty()) pieces.add(Piece(w, r))
                }
            }
            if (pieces.isEmpty()) { y += lineAdvance; return }

            fun sizeOf(run: RichText.StyledRun) = (if (run.light) base.size * 0.94f else base.size) * run.sizeScale
            fun widthOf(p: Piece) = width(fontFor(p.text), p.text, sizeOf(p.run))

            var line = ArrayList<Piece>()
            fun flush() {
                if (line.isEmpty()) { y += lineAdvance; return }
                if (y + lineAdvance > PAGE_H - MARGIN) newPage()
                val stream = content
                if (stream != null) {
                    var x = MARGIN
                    line.forEach { piece ->
                        val w = widthOf(piece)
                        if (piece.run.highlight >= 0) {
                            val argb = RichText.HIGHLIGHT_ARGB[
                                piece.run.highlight.coerceIn(0, RichText.HIGHLIGHT_ARGB.size - 1)]
                            val r = ((argb shr 16) and 0xFF) / 255f
                            val g = ((argb shr 8) and 0xFF) / 255f
                            val b = (argb and 0xFF) / 255f
                            try {
                                stream.setNonStrokingColor(
                                    r + (1f - r) * 0.55f, g + (1f - g) * 0.55f, b + (1f - b) * 0.55f
                                )
                                val size = sizeOf(piece.run)
                                stream.addRect(x, PAGE_H - y - size * 0.22f, w, size * 1.02f)
                                stream.fill()
                            } catch (_: Throwable) {
                            }
                        }
                        x += w
                    }
                    x = MARGIN
                    line.forEach { piece ->
                        val font = fontFor(piece.text)
                        val safe = if (font === fonts.last()) encodable(font, piece.text) else piece.text
                        val size = sizeOf(piece.run)
                        try {
                            stream.beginText()
                            val rgb = RichText.textColorArgb(piece.run.color)
                            if (rgb != null) {
                                stream.setNonStrokingColor(
                                    ((rgb shr 16) and 0xFF) / 255f,
                                    ((rgb shr 8) and 0xFF) / 255f,
                                    (rgb and 0xFF) / 255f
                                )
                            } else {
                                stream.setNonStrokingColor(0f, 0f, 0f)
                            }
                            stream.setFont(font, size)
                            stream.newLineAtOffset(x, PAGE_H - y)
                            stream.showText(safe)
                            if (piece.run.bold) {
                                stream.newLineAtOffset(0.35f, 0f)
                                stream.showText(safe)
                            }
                            stream.endText()
                        } catch (_: Throwable) {
                            try { stream.endText() } catch (_: Throwable) {}
                        }
                        x += widthOf(piece)
                    }
                }
                y += lineAdvance
                line = ArrayList()
            }

            pieces.forEach { p ->
                if (line.isEmpty() && p.text == " ") return@forEach
                if (line.isNotEmpty() && line.sumOf { widthOf(it).toDouble() }.toFloat() + widthOf(p) > maxWidth) flush()
                line.add(p)
            }
            flush()
        }


        fun drawDoodle(doodleJson: String) {
            val strokes = com.lucent.app.ui.Doodle.parse(doodleJson)
            if (strokes.isEmpty()) return
            val boxW = PAGE_W - 2 * MARGIN
            val boxH = boxW * 0.6f
            if (y + boxH > PAGE_H - MARGIN) newPage()
            val stream = content ?: return
            val top = y
            strokes.forEach { stroke ->
                if (stroke.points.isEmpty()) return@forEach
                try {
                    val argb = stroke.color
                    stream.setStrokingColor(
                        ((argb shr 16) and 0xFF) / 255f,
                        ((argb shr 8) and 0xFF) / 255f,
                        (argb and 0xFF) / 255f
                    )
                    stream.setLineWidth((stroke.width * boxW).coerceAtLeast(0.5f))
                    stream.setLineCapStyle(1)
                    stream.setLineJoinStyle(1)
                    stroke.points.forEachIndexed { i, pt ->
                        val px = MARGIN + pt.x * boxW
                        val py = PAGE_H - (top + pt.y * boxH)
                        if (i == 0) stream.moveTo(px, py) else stream.lineTo(px, py)
                    }
                    if (stroke.points.size == 1) {
                        val pt = stroke.points.first()
                        stream.lineTo(MARGIN + pt.x * boxW + 0.1f, PAGE_H - (top + pt.y * boxH))
                    }
                    stream.stroke()
                } catch (_: Throwable) {
                }
            }
            y = top + boxH + 10f
        }

        fun drawWrapped(text: String, style: PdfStyle, lineAdvance: Float) {
            val maxWidth = PAGE_W - 2 * MARGIN
            val font = fontFor(text)

            fun flushLine(line: String) {
                if (y + lineAdvance > PAGE_H - MARGIN) newPage()
                drawLine(line, style)
                y += lineAdvance
            }

            fun emitLong(word: String) {
                var current = StringBuilder()
                for (ch in word) {
                    val candidate = current.toString() + ch
                    if (width(font, candidate, style.size) > maxWidth && current.isNotEmpty()) {
                        flushLine(current.toString())
                        current = StringBuilder().append(ch)
                    } else {
                        current = StringBuilder(candidate)
                    }
                }
                if (current.isNotEmpty()) flushLine(current.toString())
            }

            val words = if (text.isEmpty()) listOf("") else text.split(" ")
            var line = StringBuilder()
            for (w in words) {
                val candidate = if (line.isEmpty()) w else "$line $w"
                if (width(font, candidate, style.size) > maxWidth && line.isNotEmpty()) {
                    flushLine(line.toString())
                    line = StringBuilder(w)
                } else {
                    line = StringBuilder(candidate)
                }
                if (width(font, line.toString(), style.size) > maxWidth) {
                    emitLong(line.toString())
                    line = StringBuilder()
                }
            }
            flushLine(line.toString())
        }

        fun finish() { content?.close(); content = null }
    }


    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            for ((name, content) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    private fun xmlEscape(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> if (ch.code < 0x20 && ch != '\t' && ch != '\n' && ch != '\r') sb.append(' ') else sb.append(ch)
            }
        }
        return sb.toString()
    }


    fun zipWithAttachments(
        context: android.content.Context,
        documentName: String,
        documentBytes: ByteArray,
        attachments: List<Attachment>,
        extraFiles: List<Pair<String, ByteArray>> = emptyList()
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry(documentName))
            zos.write(documentBytes)
            zos.closeEntry()

            val usedNames = hashSetOf(documentName)
            for ((name, bytes) in extraFiles) {
                val entryName = "attachments/" + uniqueEntryName(name.ifBlank { "canvas.pdf" }, usedNames)
                zos.putNextEntry(ZipEntry(entryName))
                zos.write(bytes)
                zos.closeEntry()
            }
            for (att in attachments) {
                val bytes = Attachments.readBytes(context, att, maxBytes = 256L * 1024 * 1024) ?: continue
                val entryName = "attachments/" + uniqueEntryName(att.name.ifBlank { "file" }, usedNames)
                zos.putNextEntry(ZipEntry(entryName))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    fun doodlePdf(canvas: DoodleExport.Canvas, heading: String = ""): ByteArray {
        PDDocument().use { doc ->
            val fonts = loadPdfFonts(doc)
            val state = PdfState(doc, fonts)
            state.newPage()
            if (heading.isNotBlank()) state.drawWrapped(heading, PdfStyle(13f, bold = true), 20f)
            state.space(4f)
            state.drawDoodle(canvas.strokesJson)
            state.finish()
            val baos = ByteArrayOutputStream()
            doc.save(baos)
            return baos.toByteArray()
        }
    }

    fun doodlesPdf(canvases: List<DoodleExport.Canvas>, heading: String = ""): ByteArray {
        PDDocument().use { doc ->
            val fonts = loadPdfFonts(doc)
            val state = PdfState(doc, fonts)
            canvases.forEachIndexed { i, canvas ->
                if (i > 0) state.newPage()
                if (heading.isNotBlank()) state.drawWrapped(heading, PdfStyle(13f, bold = true), 20f)
                state.space(4f)
                state.drawDoodle(canvas.strokesJson)
            }
            state.finish()
            val baos = ByteArrayOutputStream()
            doc.save(baos)
            return baos.toByteArray()
        }
    }

    private fun uniqueEntryName(name: String, used: MutableSet<String>): String {
        if (used.add(name)) return name
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var n = 2
        while (true) {
            val candidate = "$stem ($n)$ext"
            if (used.add(candidate)) return candidate
            n++
        }
    }
}

private data class DocxRunStyle(
    val bold: Boolean,
    val italic: Boolean,
    val highlight: Int,
    val color: Int,
    val size: Int
)

private fun hex6(argb: Int): String = String.format("%06X", argb and 0xFFFFFF)
