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

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfDocument
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
 * documents this simple. The PDF uses Android's own [PdfDocument], so it needs no library at all.
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
        // INTEGRATION (phase 3): the doodle stroke JSON, non-empty only for doodle notes. Only the
        // PDF writer renders it — PDF is the one export format here that can draw vector strokes.
        // Every other format keeps exporting the caption text, exactly as before.
        val doodle: String = "",
        val checklist: List<Pair<Boolean, String>>,
        val checklistLabel: String,
        val attachments: List<String>
    )

    private fun noteBlock(note: Note): Block {
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
            doodle = if (note.isDoodle) note.doodle else "",
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
            val content = if (n.isChecklist) {
                Checklist.parse(n.checklist).joinToString("\n") { "${if (it.done) "[x]" else "[ ]"} ${it.text}" }
            } else n.body.trim()
            val tags = NoteTags.parse(n.tags).joinToString(" ") { "#" + NoteTags.label(it) }
            listOf(
                n.title.ifBlank { com.lucent.app.i18n.S.untitled },
                formatTime(n.updatedAt),
                tags,
                if (n.pinned) "Yes" else "",
                if (n.archived) "Yes" else "",
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
                if (t.pinned) "Yes" else "",
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

    private fun notesPdf(notes: List<Note>): ByteArray =
        pdf(com.lucent.app.i18n.S.exportDocNotesTitle, notes.size, "note", notes.map { noteBlock(it) })

    private fun tasksPdf(tasks: List<Task>): ByteArray =
        pdf(com.lucent.app.i18n.S.exportDocTasksTitle, tasks.size, "task", tasks.map { taskBlock(it) })

    // A4 at 72dpi, in points.
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 42f

    private fun pdf(heading: String, count: Int, noun: String, blocks: List<Block>): ByteArray {
        val doc = PdfDocument()

        val titlePaint = Paint().apply { color = Color.BLACK; textSize = 20f; isFakeBoldText = true; isAntiAlias = true }
        val itemTitlePaint = Paint().apply { color = Color.BLACK; textSize = 15f; isFakeBoldText = true; isAntiAlias = true }
        val metaPaint = Paint().apply { color = Color.DKGRAY; textSize = 10f; isAntiAlias = true }
        val bodyPaint = Paint().apply { color = Color.BLACK; textSize = 11f; isAntiAlias = true }
        val labelPaint = Paint().apply { color = Color.BLACK; textSize = 11f; isFakeBoldText = true; isAntiAlias = true }

        val state = PdfState(doc)
        state.newPage()

        state.drawWrapped(heading, titlePaint, 26f)
        state.drawWrapped(((if (noun == "note") com.lucent.app.i18n.S.exportDocNoteCount(count) else com.lucent.app.i18n.S.exportDocTaskCount(count)) + ", " + com.lucent.app.i18n.S.exportDocExportedAt(formatTime(System.currentTimeMillis()))), metaPaint, 14f)
        state.drawWrapped(com.lucent.app.i18n.S.exportDocAttachmentsNote, metaPaint, 16f)

        if (blocks.isEmpty()) {
            state.drawWrapped((if (noun == "note") com.lucent.app.i18n.S.exportDocNoNotes else com.lucent.app.i18n.S.exportDocNoTasks), metaPaint, 14f)
        } else {
            for (b in blocks) {
                state.space(10f)
                state.drawWrapped(b.title, itemTitlePaint, 20f)
                if (b.meta.isNotBlank()) state.drawWrapped(b.meta, metaPaint, 14f)
                if (b.body.isNotBlank()) {
                    // INTEGRATION (C task 20): styled when the note carries a sidecar, and byte-for-byte
                    // the old path when it does not — so an unformatted export is unchanged.
                    if (b.bodySpans.isBlank()) {
                        for (line in b.body.split("\n")) state.drawWrapped(line, bodyPaint, 15f)
                    } else {
                        for (runs in RichText.lineRuns(b.body, b.bodySpans)) {
                            state.drawWrappedRich(runs, bodyPaint, 15f) { run -> richPaintFor(run, bodyPaint) }
                        }
                    }
                }
                // Phase 3: doodle notes draw their strokes as real vector lines. See drawDoodle for
                // the sizing reasoning.
                if (b.doodle.isNotBlank()) state.drawDoodle(b.doodle)
                if (b.checklist.isNotEmpty()) {
                    state.drawWrapped("${b.checklistLabel}:", labelPaint, 15f)
                    for ((done, text) in b.checklist) state.drawWrapped("${if (done) "\u2611" else "\u2610"} $text", bodyPaint, 15f)
                }
                if (b.attachments.isNotEmpty()) state.drawWrapped(com.lucent.app.i18n.S.exportDocAttachmentsLine(b.attachments.joinToString(", ")), metaPaint, 15f)
            }
        }

        state.finish()
        val baos = ByteArrayOutputStream()
        doc.writeTo(baos)
        doc.close()
        return baos.toByteArray()
    }

    // Tracks the current page and vertical cursor, starting new pages as content overflows.
    private class PdfState(val doc: PdfDocument) {
        private var page: PdfDocument.Page? = null
        private var y = MARGIN
        private var pageNum = 0

        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNum++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
            y = MARGIN
        }

        fun space(dy: Float) { y += dy }

        // Draw [text] word-wrapped to the page width at the current cursor, advancing by [lineAdvance]
        // per rendered line and flowing onto a new page when the bottom margin is reached.
        fun drawWrapped(text: String, paint: Paint, lineAdvance: Float) {
            val maxWidth = PAGE_W - 2 * MARGIN
            val words = if (text.isEmpty()) listOf("") else text.split(" ")
            var line = StringBuilder()
            fun flush() {
                if (y + lineAdvance > PAGE_H - MARGIN) newPage()
                page?.canvas?.drawText(line.toString(), MARGIN, y, paint)
                y += lineAdvance
            }
            for (w in words) {
                val candidate = if (line.isEmpty()) w else "$line $w"
                if (paint.measureText(candidate) > maxWidth && line.isNotEmpty()) {
                    flush()
                    line = StringBuilder(w)
                } else {
                    line = StringBuilder(candidate)
                }
            }
            flush()
        }


        /**
         * INTEGRATION (C-group task 20) — [drawWrapped]'s rich-text sibling.
         *
         * Same wrapping rules and the same page-break behaviour; the difference is that a line is
         * assembled from [RichText.StyledRun]s and drawn segment by segment, with a filled rectangle
         * behind any highlighted stretch.
         *
         * Wrapping measures with the SAME paint each segment will be drawn with. Measuring
         * everything with the body paint and then drawing some of it bold is how a line ends up a
         * few points past the right margin — visible, wrong, and only on the notes that use
         * formatting, which is exactly the sort of bug that ships.
         */
        fun drawWrappedRich(
            runs: List<RichText.StyledRun>,
            base: Paint,
            lineAdvance: Float,
            paintFor: (RichText.StyledRun) -> Paint
        ) {
            val maxWidth = PAGE_W - 2 * MARGIN

            // Split every run at spaces so wrapping can break between words while keeping each
            // fragment's style attached to it.
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

            var line = ArrayList<Piece>()
            fun lineWidth(extra: Piece?): Float {
                var w = 0f
                line.forEach { w += paintFor(it.run).measureText(it.text) }
                if (extra != null) w += paintFor(extra.run).measureText(extra.text)
                return w
            }
            fun flush() {
                if (line.isEmpty()) { y += lineAdvance; return }
                if (y + lineAdvance > PAGE_H - MARGIN) newPage()
                var x = MARGIN
                val canvas = page?.canvas
                line.forEach { piece ->
                    val paint = paintFor(piece.run)
                    val w = paint.measureText(piece.text)
                    if (piece.run.highlight >= 0 && canvas != null) {
                        // Drawn first, so the glyphs land on top of it. Sized from the paint's own
                        // metrics rather than a guessed constant, so it still fits when the body
                        // text size changes.
                        val fm = paint.fontMetrics
                        val hl = Paint().apply {
                            color = RichText.HIGHLIGHT_ARGB[
                                piece.run.highlight.coerceIn(0, RichText.HIGHLIGHT_ARGB.size - 1)]
                            alpha = 110
                            isAntiAlias = true
                        }
                        canvas.drawRect(x, y + fm.ascent, x + w, y + fm.descent, hl)
                    }
                    canvas?.drawText(piece.text, x, y, paint)
                    x += w
                }
                y += lineAdvance
                line = ArrayList()
            }

            pieces.forEach { p ->
                // A leading space on a wrapped line is dropped, matching how the plain writer joins
                // words with a single separator rather than preserving the original run of spaces.
                if (line.isEmpty() && p.text == " ") return@forEach
                if (line.isNotEmpty() && lineWidth(p) > maxWidth) flush()
                line.add(p)
            }
            flush()
        }


        /**
         * INTEGRATION (phase 3) — render a doodle's strokes into the PDF as vector lines.
         *
         * ### Sizing
         *
         * Stroke points are stored as FRACTIONS of the drawing canvas (see ui/DoodleCanvas.kt), and
         * the app itself renders them into a full-width box of fixed dp height — meaning the same
         * doodle already displays at slightly different aspect ratios on a phone and on a wide
         * desktop window. There is therefore no single "true" aspect to preserve; this writer uses
         * the full content width at a 5:3 box, which closely matches the phone editor where most
         * doodles are drawn. Stroke widths are fractions of the canvas WIDTH, same as the app.
         *
         * ### Page fit
         *
         * The box either fits in the space remaining on the current page or starts a fresh one —
         * a drawing split across a page boundary is two half-drawings, which is worse than a gap.
         */
        fun drawDoodle(doodleJson: String) {
            val strokes = com.lucent.app.ui.Doodle.parse(doodleJson)
            if (strokes.isEmpty()) return
            val boxW = PAGE_W - 2 * MARGIN
            val boxH = boxW * 0.6f
            if (y + boxH > PAGE_H - MARGIN) newPage()
            val canvas = page?.canvas ?: return
            val top = y
            strokes.forEach { stroke ->
                if (stroke.points.isEmpty()) return@forEach
                val paint = Paint().apply {
                    color = stroke.color
                    style = Paint.Style.STROKE
                    strokeWidth = (stroke.width * boxW).coerceAtLeast(0.5f)
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    isAntiAlias = true
                }
                val path = Path()
                stroke.points.forEachIndexed { i, pt ->
                    val px = MARGIN + pt.x * boxW
                    val py = top + pt.y * boxH
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                // A single tap is a dot: a zero-length path draws nothing under STROKE, so give it
                // a hair of length instead of special-casing a circle.
                if (stroke.points.size == 1) {
                    val pt = stroke.points.first()
                    path.lineTo(MARGIN + pt.x * boxW + 0.1f, top + pt.y * boxH)
                }
                canvas.drawPath(path, paint)
            }
            y = top + boxH + 10f
        }

        fun finish() { page?.let { doc.finishPage(it) }; page = null }
    }


    /**
     * The Paint for one styled run, derived from the body paint so size and colour stay in step.
     *
     * `isFakeBoldText` and `textSkewX` are used rather than real font faces because this writer has
     * always drawn with the platform default typeface and has no font files to reach for. They are
     * the same approximations the DOCX path settles for on "light", and they are honest: the reader
     * sees heavier and slanted text, which is what was marked.
     *
     * "Light" has no synthetic equivalent at all, so it is rendered as a slightly smaller regular
     * weight. That is a real loss of fidelity and is recorded here rather than hidden.
     */
    private fun richPaintFor(run: RichText.StyledRun, base: Paint): Paint = Paint(base).apply {
        isFakeBoldText = run.bold
        if (run.italic) textSkewX = -0.25f
        if (run.light) textSize = base.textSize * 0.94f
        // Text colour. Left untouched when the run carries the default, so body text keeps whatever
        // colour the page decided on rather than being forced to black by the styling path.
        RichText.textColorArgb(run.color)?.let { color = it }
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
        attachments: List<Attachment>
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry(documentName))
            zos.write(documentBytes)
            zos.closeEntry()

            val usedNames = hashSetOf(documentName)
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
