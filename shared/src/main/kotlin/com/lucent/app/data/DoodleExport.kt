package com.lucent.app.data

object DoodleExport {

    data class Canvas(
        val ownerId: Long,
        val index: Int,
        val strokesJson: String,
        val fileName: String
    )

    fun attachmentKey(itemId: Long, name: String): String = "$itemId\u0000$name"

    fun canvasKey(itemId: Long, index: Int): String = "$itemId\u0000\u0001canvas:$index"

    fun canvasesOf(note: Note): List<Canvas> {
        if (!note.isDoodle) return emptyList()
        val pages = com.lucent.app.ui.DoodlePages.parse(note.doodle)
        val stem = fileStem(note.title)
        val out = ArrayList<Canvas>(pages.size)
        pages.forEachIndexed { i, page ->
            if (com.lucent.app.ui.Doodle.isEmpty(page)) return@forEachIndexed
            val name = if (pages.size == 1) "$stem.pdf" else "$stem - canvas ${i + 1}.pdf"
            out.add(Canvas(ownerId = note.id, index = i, strokesJson = page, fileName = name))
        }
        return out
    }

    fun canvasCount(note: Note): Int = canvasesOf(note).size

    fun fileStem(title: String): String {
        val cleaned = title.trim().map { ch ->
            if (ch in "\\/:*?\"<>|" || ch.code < 0x20) '-' else ch
        }.joinToString("")
        val trimmed = if (cleaned.length > 60) cleaned.substring(0, 60).trimEnd() else cleaned
        return trimmed.ifBlank { "doodle" }
    }
}
