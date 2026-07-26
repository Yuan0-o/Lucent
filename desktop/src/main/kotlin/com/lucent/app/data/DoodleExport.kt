package com.lucent.app.data

/**
 * Doodle canvases, seen as **attachments** (round R1, task 3).
 *
 * ### The mess this exists to clean up
 *
 * Exporting used to have exactly two kinds of thing in it: the document, and the files hanging off
 * an item. Then doodle notes arrived, and a drawing fitted neither slot. It was not a file, so the
 * export screen never listed it and it could not be ticked; and it was not text, so of the four
 * document formats only the PDF writer could draw it at all. The result was an export screen that
 * silently disagreed with itself — a note made entirely of drawings looked, in the picker, exactly
 * like an empty note, and exporting it to Markdown or Word produced a heading with nothing under it.
 *
 * The fix is to stop treating a drawing as a special case of *content* and start treating it as
 * what it behaves like: **an attachment that happens to be stored in a column**. A canvas has a
 * name, it can be ticked, it can be left out, and when it is ticked it is written into the archive
 * as its own file — a PDF, because that is the one universally-openable format that can carry
 * vector strokes. Everything the export screen already knew how to do with attachments now applies
 * to drawings unchanged.
 *
 * ### Why the keys live here rather than in the screen
 *
 * The picker keys a ticked attachment by `"<itemId>\u0000<file name>"`. Canvases have to share that
 * one set — the select-all rows count across both kinds — so they need a key that cannot collide
 * with any real file name. Both builders therefore live in one place: the screen that writes the
 * keys and the caller that reads them can't drift apart if neither of them owns the format.
 */
object DoodleExport {

    /**
     * One canvas of one note.
     *
     * [strokesJson] is a single page's stroke array (see `ui/DoodlePages`), never the multi-page
     * container — the container is unwrapped here, once, so that no writer downstream has to know
     * that a doodle column can hold either shape. That confusion was itself a live bug: the PDF
     * writer fed the whole column to `Doodle.parse`, which understands only a bare array, so every
     * note with **more than one canvas** exported with its drawings silently missing.
     */
    data class Canvas(
        val ownerId: Long,
        /** 0-based position within the note; the UI shows it 1-based. */
        val index: Int,
        val strokesJson: String,
        /** The name this canvas takes inside the export archive, extension included. */
        val fileName: String
    )

    /** The tick key for a real attachment. Must match [canvasKey]'s keyspace but never collide. */
    fun attachmentKey(itemId: Long, name: String): String = "$itemId\u0000$name"

    /**
     * The tick key for a canvas. The `\u0001` sentinel is not a legal file-name character on any
     * platform Lucent runs on, so a canvas key can never be mistaken for an attachment key no
     * matter what a user names a file.
     */
    fun canvasKey(itemId: Long, index: Int): String = "$itemId\u0000\u0001canvas:$index"

    /**
     * Every canvas of [note] that actually has something drawn on it.
     *
     * Blank canvases are skipped rather than listed and left unticked: an empty page in the picker
     * is a decision the user has to make about nothing, and exporting one would put a blank PDF in
     * the archive. A note that is not a doodle note yields nothing at all — the column may still
     * hold an old drawing, deliberately (switching a note between kinds never destroys the other
     * kind's content), and exporting content the note is no longer showing would be a surprise.
     */
    fun canvasesOf(note: Note): List<Canvas> {
        if (!note.isDoodle) return emptyList()
        val pages = com.lucent.app.ui.DoodlePages.parse(note.doodle)
        val stem = fileStem(note.title)
        val out = ArrayList<Canvas>(pages.size)
        pages.forEachIndexed { i, page ->
            if (com.lucent.app.ui.Doodle.isEmpty(page)) return@forEachIndexed
            // The 1-based number is what the editor's tab strip shows, so it is what the file name
            // uses: "canvas 2" in the archive is the canvas labelled "Canvas 2" in the app.
            val name = if (pages.size == 1) "$stem.pdf" else "$stem - canvas ${i + 1}.pdf"
            out.add(Canvas(ownerId = note.id, index = i, strokesJson = page, fileName = name))
        }
        return out
    }

    /** How many drawn canvases [note] carries; used by the writers that can only state a count. */
    fun canvasCount(note: Note): Int = canvasesOf(note).size

    /**
     * A file-name stem from a note title.
     *
     * Only the characters that are actually illegal in a path are replaced — CJK, spaces and
     * punctuation are all kept, because the whole point of naming the file after the note is that
     * the user recognises it afterwards, and transliterating a Chinese title into `_____` defeats
     * that completely. ZIP entry names are UTF-8, so nothing here needs to be ASCII.
     */
    fun fileStem(title: String): String {
        val cleaned = title.trim().map { ch ->
            if (ch in "\\/:*?\"<>|" || ch.code < 0x20) '-' else ch
        }.joinToString("")
        // A long title makes a path some filesystems reject; 60 characters is comfortably inside
        // every limit and still recognisable.
        val trimmed = if (cleaned.length > 60) cleaned.substring(0, 60).trimEnd() else cleaned
        return trimmed.ifBlank { "doodle" }
    }
}
