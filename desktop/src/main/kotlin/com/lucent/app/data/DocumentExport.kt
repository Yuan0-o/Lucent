package com.lucent.app.data

// C-GROUP TASK 10 — exported documents follow the app's language.
//
// Every user-visible label in a generated PDF/DOCX/XLSX used to be a hardcoded English
// string literal, so a Chinese user exporting a Chinese note received a document headed
// "Lucent notes" with rows labelled "Updated" and "Subtasks". They now read from the same
// catalog the rest of the UI does, so an export matches the language on screen.
//
// Deliberately NOT localized: the internal `noun` discriminator ("note"/"task") used to pick
// between the count/empty strings. It is a code-level switch, never shown, and translating it
// would silently break the branch that reads it.

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

/**
 * The formats a notes/tasks export can be written in.
 *
 * Markdown was the original (and only) option — see [MarkdownExport]. This adds Word, Excel, and PDF
 * so the same "walk away with your data in a format anything can open" promise extends to the file
 * types people actually have to hand in to work, print, or drop into a spreadsheet. [MarkdownExport]
 * still produces the Markdown; the three office formats are produced here.
 */
enum class ExportFormat(val label: String, val extension: String, val mime: String) {
    MARKDOWN("Markdown (.md)", "md", "text/markdown"),
    WORD("Word (.docx)", "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    PDF("PDF (.pdf)", "pdf", "application/pdf"),
    EXCEL("Excel (.xlsx)", "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
}

/**
 * Builds Word (.docx), Excel (.xlsx), and PDF (.pdf) exports of notes and tasks.
 *
 * ### Why it's written by hand
 * The whole app is deliberately dependency-light, and pulling in Apache POI (Word/Excel) would add
 * many megabytes to the APK for what is, in the end, a handful of paragraphs and a table. So the
 * office files are assembled from scratch: a .docx and a .xlsx are just ZIP archives of a few small
 * XML parts (Office Open XML), which [ZipOutputStream] and string-building handle perfectly well for
 * documents this simple. Desktop adaptation: the PDF section uses Apache PDFBox with the app's
 * bundled CJK typefaces embedded, because the JVM has no platform PDF canvas; the docx/xlsx
 * builders and every string in every format are the Android file verbatim.
 *
 * ### What goes in
 * The content mirrors [MarkdownExport] exactly, so every format tells the same story: trashed items
 * are excluded, archived notes and completed tasks are kept and labelled, and each item carries its
 * metadata, its body/details, its checklist/subtasks, and its attachment *names* (bytes can't ride
 * in these formats any more than in Markdown — the .json backup remains the way to carry files).
 * Word and PDF are laid out as a readable document; Excel is laid out as one row per item so it can
 * be sorted and filtered like a spreadsheet.
 */
object DocumentExport {

    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private fun formatTime(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(stamp)

    // ============================ Public entry points ============================

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

    // ============================ Shared content model ============================

    // A single item flattened into the pieces every format needs: a title, a one-line metadata
    // string, the body/details, the checklist/subtask lines, and the attachment names. Building this
    // once keeps the Word/PDF/Excel writers from each re-deriving the same fields.
    private data class Block(
        val title: String,
        val meta: String,
        val body: String,
        // INTEGRATION (C task 20): the sidecar for [body], carried through so the DOCX writer can
        // turn it into runs. Empty for every format that drops it — see docxRichPara.
        val bodySpans: String = "",
        // Round R1, task 3 — the drawn canvases of a doodle note, one stroke array per canvas,
        // plus the file name each one takes in the export archive (see data/DoodleExport).
        //
        // This was a single String holding the doodle COLUMN, handed straight to `Doodle.parse`.
        // That parser understands one shape — a bare array of strokes — but the column has held a
        // multi-canvas container (`{"pages":[...]}`) ever since doodle notes learned to have more
        // than one page. So the parse threw, was swallowed into an empty list, and every note with
        // two or more canvases exported with all of its drawings missing and no error anywhere.
        // Unwrapping once, here, means no writer downstream has to know the column has two shapes.
        val doodlePages: List<String> = emptyList(),
        val doodleNames: List<String> = emptyList(),
        val checklist: List<Pair<Boolean, String>>,
        val checklistLabel: String,
        val attachments: List<String>
    )

    private fun noteBlock(note: Note): Block {
        // Round R1, task 3 — resolved once and shared by the metadata line and the writers below.
        val doodleCanvases = DoodleExport.canvasesOf(note)
        val meta = buildList {
            add(com.lucent.app.i18n.S.exportDocUpdated(formatTime(note.updatedAt)))
            if (note.pinned) add(com.lucent.app.i18n.S.exportDocPinned)
            if (note.archived) add(com.lucent.app.i18n.S.exportDocArchived)
            // Task 3.2 — see MarkdownExport: canonical in the column, local language on the page.
            val tags = NoteTags.parse(note.tags)
            if (tags.isNotEmpty()) add(tags.joinToString(" ") { "#" + NoteTags.label(it) })
        }.joinToString(" · ")
        val checklist = if (note.isChecklist) Checklist.parse(note.checklist).map { it.done to it.text } else emptyList()
        return Block(
            title = note.title.ifBlank { com.lucent.app.i18n.S.untitled },
            meta = meta,
            body = if (note.isChecklist) "" else note.body.trim(),
            bodySpans = if (note.isChecklist) "" else note.bodySpans,
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
        val box = if (task.isDone) "\u2611" else "\u2610" // ☑ / ☐
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

    // ============================ Word (.docx) ============================

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
                    // Round R1, task 3 — Word cannot hold the strokes, but naming the canvases makes
                    // the document agree with the archive sitting next to it instead of implying the
                    // note was empty. Same lines as the Android twin writes.
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

    // One paragraph. Line breaks in [text] become soft breaks within the paragraph, so a multi-line
    // body stays one logical paragraph rather than fragmenting.
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


    /**
     * INTEGRATION (C-group task 20) — one DOCX paragraph carrying rich-text runs.
     *
     * ### The per-format decision C's handoff asked for, made explicitly
     *
     * | format | highlights and weights |
     * |---|---|
     * | DOCX | **kept** — Word has runs, `<w:b/>`, `<w:i/>` and `<w:highlight/>` for exactly this |
     * | Markdown | dropped, words kept — Markdown has no highlight syntax, and inventing `==x==` |
     * |          | would emit something most renderers show as literal equals signs |
     * | plain text / CSV / XLSX | dropped, words kept — no way to express it at all |
     * | PDF | dropped, words kept — see the note in [pdf]; this is the one gap left open |
     *
     * Dropping is never silent: the Settings switch states it before the user turns the feature on.
     *
     * Falls back to the plain writer whenever there is nothing to express, so a note without
     * formatting produces byte-identical XML to before this existed.
     */
    private fun docxRichPara(text: String, spansJson: String, spaceBeforeTwips: Int = 40): String {
        val spans = RichText.load(spansJson, text)
        if (spans.isEmpty()) return docxPara(text, spaceBeforeTwips = spaceBeforeTwips)

        // Word highlight names, in the same order as the five swatches the editor offers. Word only
        // accepts a fixed vocabulary here, so these are the nearest named colours rather than exact
        // matches — a close highlight is the honest rendering; an exact one is not available.
        val highlightNames = listOf("yellow", "green", "cyan", "magenta", "darkYellow")

        // Split at every point where the style changes. Working per character and coalescing is
        // slower than tracking span boundaries, but it is obviously correct in the presence of
        // overlaps (bold under a highlight), and a note body is a few thousand characters.
        fun styleAt(i: Int): DocxRunStyle {
            var bold = false; var light = false; var italic = false; var hl = -1
            var col = RichText.TEXT_COLOR_DEFAULT
            spans.forEach { s ->
                if (i >= s.start && i < s.end) when (s.kind) {
                    RichSpan.Kind.BOLD -> bold = true
                    RichSpan.Kind.LIGHT -> light = true
                    RichSpan.Kind.ITALIC -> italic = true
                    RichSpan.Kind.HIGHLIGHT -> hl = s.color
                    RichSpan.Kind.COLOR -> col = s.color
                }
            }
            // Word has no "light" run property, so light is expressed as "not bold" — the closest
            // thing the format can say. Recorded here rather than dropped silently so the intent
            // survives in the file even though the distinction from regular does not.
            return DocxRunStyle(bold && !light, italic, hl, col)
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
                val rPr = buildString {
                    if (bold || italic || hl >= 0 || argb != null) {
                        append("<w:rPr>")
                        if (bold) append("<w:b/>")
                        if (italic) append("<w:i/>")
                        // Word takes a plain RRGGBB hex; the alpha byte is dropped because a run
                        // colour in OOXML has no transparency to give it to.
                        if (argb != null) append("<w:color w:val=\"" + hex6(argb) + "\"/>")
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

    // ============================ Excel (.xlsx) ============================

    private fun notesXlsx(notes: List<Note>): ByteArray {
        val header = listOf(com.lucent.app.i18n.S.exportColTitle, com.lucent.app.i18n.S.exportColUpdated, com.lucent.app.i18n.S.exportColTags, com.lucent.app.i18n.S.exportColPinned, com.lucent.app.i18n.S.exportColArchived, com.lucent.app.i18n.S.exportColContent, com.lucent.app.i18n.S.exportColAttachments)
        val rows = notes.map { n ->
            // Round R1, task 3 — a doodle note has no body text, so its row used to be blank in
            // the spreadsheet. A spreadsheet cannot hold a drawing, but it can say there is one and
            // name the file the archive carries it in, which is what every other format now does.
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
                // Round R1, task 3 — these two were the last English literals left in any
                // export writer; a Chinese spreadsheet had a column of "Yes" in it.
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
        // Header row.
        sheetData.append(xlsxRow(1, header))
        rows.forEachIndexed { i, cells -> sheetData.append(xlsxRow(i + 2, cells)) }
        sheetData.append("</sheetData>")

        // A rough column width so the sheet opens legibly rather than every column at default width.
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

    // ============================ PDF (.pdf) ============================
    //
    // Desktop implementation over Apache PDFBox. Layout, wording, sizes, and page metrics mirror
    // the Android PdfDocument version: A4, 42pt margins, the same heading/meta/body cascade, the
    // same word-wrap flow with page breaks. Fonts are the app's own bundled TTFs, embedded so
    // Chinese/Japanese/Korean content renders instead of degrading to placeholder glyphs; a line is
    // drawn with the first embedded face able to encode it, falling back per line rather than per
    // document so mixed-language exports come out whole.

    private fun notesPdf(notes: List<Note>): ByteArray =
        pdf(com.lucent.app.i18n.S.exportDocNotesTitle, notes.size, "note", notes.map { noteBlock(it) })

    private fun tasksPdf(tasks: List<Task>): ByteArray =
        pdf(com.lucent.app.i18n.S.exportDocTasksTitle, tasks.size, "task", tasks.map { taskBlock(it) })

    // A4 at 72dpi, in points — same metrics as the Android renderer.
    private const val PAGE_W = 595f
    private const val PAGE_H = 842f
    private const val MARGIN = 42f

    /** A text style: which faces to try (regular set or the same set drawn "bold") and the size. */
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
                        // INTEGRATION (C task 20): styled when the note carries a sidecar, and the
                        // untouched old path when it does not.
                        if (b.bodySpans.isBlank()) {
                            for (line in b.body.split("\n")) state.drawWrapped(line, bodyStyle, 15f)
                        } else {
                            for (runs in RichText.lineRuns(b.body, b.bodySpans)) {
                                state.drawWrappedRich(runs, bodyStyle, 15f)
                            }
                        }
                    }
                    // Phase 3: doodle strokes as vector lines — see drawDoodle. Round R1, task 3:
                    // every canvas, not a parse of the raw column that produced nothing whenever a
                    // note had more than one.
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

    /**
     * Resource path of the bundled CJK face. See [loadPdfFonts].
     *
     * The Windows workflow drops the file here before packaging; see the build note in
     * `desktop/build.gradle.kts`. Absent, everything still works — the PDF is simply Latin-only
     * again, and [cjkFontMissing] is set so the export can say so instead of silently dropping text.
     */
    private const val BUNDLED_CJK_FONT = "/fonts/LucentCJK.otf"

    /**
     * Set when a PDF export needed CJK glyphs and no loaded face could provide them. Read by the
     * export caller to surface [com.lucent.app.i18n.S.exportPdfMissingCjkFont].
     */
    @Volatile var cjkFontMissing: Boolean = false
        private set

    /**
     * The embedded faces, tried in order per line.
     *
     * ### C-group task 10: why a CJK face is bundled on desktop
     *
     * This was a **silent data-loss bug**, not a cosmetic one. The floor of this list was
     * Helvetica, which has no CJK coverage at all, and [PdfState.encodable] *drops* characters the
     * chosen face cannot encode — a deliberate choice so one exotic glyph cannot void a whole line.
     * Put those two together and a Chinese, Japanese or Korean note exported to PDF on a machine
     * with no imported CJK font came out **with its text simply missing**. Not mojibake, which a
     * user would notice and report; blank, which looks like the export worked.
     *
     * Android never had this problem: `android.graphics.Paint` draws through the system typeface,
     * and every Android build ships Noto CJK. PDFBox embeds only what it is given, so the desktop
     * has to be given something.
     *
     * The bundled face is therefore loaded as the LAST resort before Helvetica: a user who imported
     * their own font still gets their font, and everyone else gets glyphs instead of gaps. Loading
     * it last also means the ~16 MB face is only ever embedded in the output PDF when a line
     * actually needs it — PDFBox subsets on save, so a Latin-only document does not carry it.
     *
     * ### If the face is absent
     *
     * A build without the resource behaves exactly as before, except that [cjkFontMissing] is set
     * so the caller can tell the user their characters were dropped and offer .docx instead. A
     * missing optional asset must never fail an export, but it must not fail it *quietly* either.
     */
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
        // The bundled CJK fallback, if this build carries one.
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

    // Tracks the current page and vertical cursor, starting new pages as content overflows — the
    // same shape as the Android PdfState, with PDFBox's bottom-left origin translated internally.
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

        /** Drop characters the chosen font cannot encode so one exotic glyph never voids a line. */
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
                // PDFBox's origin is bottom-left; the cursor tracks top-down like Android's.
                stream.newLineAtOffset(MARGIN, PAGE_H - y)
                stream.showText(safe)
                if (style.bold) {
                    // Faux bold, matching Paint.isFakeBoldText: re-draw nudged right by a hairline.
                    stream.newLineAtOffset(0.35f, 0f)
                    stream.showText(safe)
                }
                stream.endText()
            } catch (_: Throwable) {
                try { stream.endText() } catch (_: Throwable) {}
            }
        }


        /**
         * INTEGRATION (C-group task 20) — [drawWrapped]'s rich-text sibling on the PDFBox side.
         *
         * Same wrapping rules and the same page-break behaviour as the plain writer; the difference
         * is that a line is assembled from [RichText.StyledRun]s and drawn segment by segment, with
         * a filled rectangle behind any highlighted stretch.
         *
         * Two things are deliberately kept from the plain path: the font FALLBACK CHAIN (so a CJK
         * note still finds a face that can encode it, per group C's task 10), and the faux-bold
         * double-draw. A styled export that silently lost CJK coverage would have re-introduced the
         * exact data-loss bug C-group fixed.
         */
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

            fun sizeOf(run: RichText.StyledRun) = if (run.light) base.size * 0.94f else base.size
            fun widthOf(p: Piece) = width(fontFor(p.text), p.text, sizeOf(p.run))

            var line = ArrayList<Piece>()
            fun flush() {
                if (line.isEmpty()) { y += lineAdvance; return }
                if (y + lineAdvance > PAGE_H - MARGIN) newPage()
                val stream = content
                if (stream != null) {
                    var x = MARGIN
                    // Highlights first, as one pass: a fill started between beginText/endText is not
                    // valid content-stream ordering, so the rectangles cannot be interleaved with
                    // the glyph drawing.
                    line.forEach { piece ->
                        val w = widthOf(piece)
                        if (piece.run.highlight >= 0) {
                            val argb = RichText.HIGHLIGHT_ARGB[
                                piece.run.highlight.coerceIn(0, RichText.HIGHLIGHT_ARGB.size - 1)]
                            val r = ((argb shr 16) and 0xFF) / 255f
                            val g = ((argb shr 8) and 0xFF) / 255f
                            val b = (argb and 0xFF) / 255f
                            // Lightened towards white rather than drawn with alpha: a transparency
                            // group would need an ExtGState, and a pale fill reads the same.
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
                    // Then the glyphs.
                    x = MARGIN
                    line.forEach { piece ->
                        val font = fontFor(piece.text)
                        val safe = if (font === fonts.last()) encodable(font, piece.text) else piece.text
                        val size = sizeOf(piece.run)
                        try {
                            stream.beginText()
                            // Text colour, defaulting to black when the run carries none.
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


        /**
         * INTEGRATION (phase 3) — the PDFBox twin of the Android drawDoodle: same 5:3 full-width
         * box, same fraction-based coordinates and stroke widths, same page-fit rule (never split a
         * drawing across a page). PDFBox's origin is bottom-left, so every y is flipped through
         * PAGE_H, and the path is stroked segment by segment inside one moveTo/lineTo chain.
         */
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
                    stream.setLineCapStyle(1)   // round — matches the app's StrokeCap.Round
                    stream.setLineJoinStyle(1)  // round
                    stroke.points.forEachIndexed { i, pt ->
                        val px = MARGIN + pt.x * boxW
                        val py = PAGE_H - (top + pt.y * boxH)
                        if (i == 0) stream.moveTo(px, py) else stream.lineTo(px, py)
                    }
                    if (stroke.points.size == 1) {
                        // A tap is a dot; a zero-length stroked path draws nothing, so nudge it.
                        val pt = stroke.points.first()
                        stream.lineTo(MARGIN + pt.x * boxW + 0.1f, PAGE_H - (top + pt.y * boxH))
                    }
                    stream.stroke()
                } catch (_: Throwable) {
                    // One bad stroke must not void the page — same policy as drawLine above.
                }
            }
            y = top + boxH + 10f
        }

        // Word-wrapped drawing with the Android flow, plus character-level splitting for a single
        // "word" wider than the page — which is what an unbroken CJK sentence is.
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

    // ============================ ZIP + XML helpers ============================

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
                // Strip control characters that are illegal in XML 1.0 (tab/newline/CR are allowed).
                else -> if (ch.code < 0x20 && ch != '\t' && ch != '\n' && ch != '\r') sb.append(' ') else sb.append(ch)
            }
        }
        return sb.toString()
    }

    // ============================ Export bundling with attachments ============================
    //
    // When the user ticks attachments to embed, the chosen document is written into a .zip next to an
    // `attachments/` folder holding the actual files. The document itself is unchanged — it still only
    // *names* its attachments — so a reader gets both the readable export and the files it refers to.

    /**
     * Build a .zip holding [documentBytes] (written as [documentName], e.g. "lucent-notes.md") plus
     * the bytes of every attachment in [attachments], each under `attachments/` with a de-duplicated
     * file name. Attachments that can't be read (missing on disk, oversized) are skipped rather than
     * failing the whole export — reading each one fully before writing its entry means a skip never
     * leaves a half-written entry that would corrupt the archive.
     */
    fun zipWithAttachments(
        context: android.content.Context,
        documentName: String,
        documentBytes: ByteArray,
        attachments: List<Attachment>,
        // Round R1, task 3 — files that are GENERATED rather than read off disk: the doodle-canvas
        // PDFs. They ride in the same `attachments/` folder and through the same de-duplicator,
        // because to the person opening the archive there is no difference between a drawing they
        // made and a file they attached, and inventing a second folder would insist there is.
        // Defaulted, so the task export and every other existing caller is untouched.
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

    /**
     * Round R1, task 3 — one doodle canvas as a PDF of its own; the PDFBox twin of the Android
     * writer. Same reasoning, same page metrics, same heading. See the Android copy for why PDF.
     */
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

    /** Make [name] unique within [used] by inserting " (2)", " (3)", … before the extension. */
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

/**
 * A DOCX run's styling, as one value. Was a `Triple<Boolean, Boolean, Int>` until text colour
 * arrived and made a fourth component necessary — at which point a tuple of four unnamed fields
 * stops being readable and starts being a place for bugs to hide.
 */
private data class DocxRunStyle(
    val bold: Boolean,
    val italic: Boolean,
    val highlight: Int,
    val color: Int
)

/** ARGB to the six-digit RRGGBB hex OOXML and HTML both want. */
private fun hex6(argb: Int): String = String.format("%06X", argb and 0xFFFFFF)
