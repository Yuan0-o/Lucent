package com.lucent.app.data

import com.lucent.app.i18n.S

object NoteStats {

    private const val WORDS_PER_MINUTE = 200.0

    data class Stats(
        val words: Int,
        val characters: Int,
        val charactersNoSpaces: Int,
        val lines: Int,
        val paragraphs: Int,
        val readingMinutes: Int
    ) {
        val isEmpty: Boolean get() = characters == 0
    }

    fun paragraphsOf(text: String): Int {
        var count = 0
        var inParagraph = false
        for (line in text.lines()) {
            if (line.isBlank()) {
                inParagraph = false
            } else if (!inParagraph) {
                count++
                inParagraph = true
            }
        }
        return count
    }

    private fun isCjk(cp: Int): Boolean =
        cp in 0x4E00..0x9FFF ||
        cp in 0x3400..0x4DBF ||
        cp in 0x3040..0x309F ||
        cp in 0x30A0..0x30FF ||
        cp in 0xAC00..0xD7AF ||
        cp in 0xF900..0xFAFF

    fun of(text: String): Stats {
        if (text.isEmpty()) return Stats(0, 0, 0, 0, 0, 0)

        val characters = text.codePointCount(0, text.length)
        val charactersNoSpaces = characters - text.codePoints().filter { Character.isWhitespace(it) }.count().toInt()
        val lines = text.count { it == '\n' } + 1

        var cjkCount = 0
        var nonCjkWordChars = 0
        var words = 0
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val charCount = Character.charCount(cp)
            when {
                isCjk(cp) -> {
                    cjkCount++
                    if (nonCjkWordChars > 0) { words++; nonCjkWordChars = 0 }
                }
                Character.isLetterOrDigit(cp) -> nonCjkWordChars++
                else -> {
                    if (nonCjkWordChars > 0) { words++; nonCjkWordChars = 0 }
                }
            }
            i += charCount
        }
        if (nonCjkWordChars > 0) words++
        words += cjkCount

        val readingMinutes = if (words == 0) 0 else Math.ceil(words / WORDS_PER_MINUTE).toInt().coerceAtLeast(1)
        return Stats(
            words = words,
            characters = characters,
            charactersNoSpaces = charactersNoSpaces,
            lines = lines,
            paragraphs = paragraphsOf(text),
            readingMinutes = readingMinutes
        )
    }

    fun paragraphCharLabel(text: String): String {
        val stats = of(text)
        if (stats.isEmpty) return ""
        val paraLabel = if (stats.paragraphs == 1) S.statParagraphsOne else S.statParagraphsN(stats.paragraphs)
        val charLabel = if (stats.characters == 1) S.statCharactersOne else S.statCharactersN(stats.characters)
        return "$paraLabel · $charLabel"
    }

    fun label(stats: Stats): String {
        if (stats.isEmpty) return ""
        val wordLabel = if (stats.words == 1) S.statWordsOne else S.statWordsN(stats.words)
        return "$wordLabel · ${S.statMinRead(stats.readingMinutes)}"
    }
}
