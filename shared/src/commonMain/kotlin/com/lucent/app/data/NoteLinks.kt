package com.lucent.app.data

object NoteLinks {

    private val LINK = Regex("""\[\[([^\[\]]+)]]""")

    fun linkTargets(text: String): List<String> =
        LINK.findAll(text).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.toList()

    fun linkTargets(note: Note): List<String> {
        val text = if (note.isChecklist) {
            Checklist.parse(note.checklist).joinToString("\n") { it.text }
        } else {
            note.body
        }
        return linkTargets(text).distinct()
    }

    fun resolve(target: String, notes: List<Note>): Note? {
        val wanted = target.trim()
        if (wanted.isEmpty()) return null
        notes.firstOrNull { it.title.trim().equals(wanted, ignoreCase = true) }?.let { return it }
        val partial = notes.filter { it.title.contains(wanted, ignoreCase = true) }
        return if (partial.size == 1) partial.first() else null
    }

    fun outgoing(note: Note, notes: List<Note>): List<Note> =
        linkTargets(note).mapNotNull { resolve(it, notes) }.filter { it.id != note.id }.distinctBy { it.id }

    fun backlinks(note: Note, notes: List<Note>): List<Note> = notes.filter { candidate ->
        candidate.id != note.id && linkTargets(candidate).any { target ->
            resolve(target, notes)?.id == note.id
        }
    }

    fun brokenLinks(note: Note, notes: List<Note>): List<String> =
        linkTargets(note).filter { resolve(it, notes) == null }
}
