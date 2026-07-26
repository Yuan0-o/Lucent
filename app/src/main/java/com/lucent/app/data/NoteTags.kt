package com.lucent.app.data

import com.lucent.app.i18n.En
import com.lucent.app.i18n.Ja
import com.lucent.app.i18n.Ko
import com.lucent.app.i18n.S
import com.lucent.app.i18n.Tr
import com.lucent.app.i18n.Zh

/**
 * Task 3.2 — the built-in tags, and why they needed a canonical form.
 *
 * ### The bug
 *
 * The five starter tags were offered as **translated display strings** and stored as whatever string
 * the user happened to tap. Tag a note "学习" in Chinese, switch the app to English, and the note
 * still carries "学习" while the chip row now offers "Study" — so the note's own tag appears as a
 * sixth, foreign-looking chip, the "Study" chip reads as unselected on a note that IS tagged study,
 * and tapping it adds a *second* tag meaning the same thing. Switch to a third language and it
 * happens again. The identity of a tag was changing with the interface language, which is not
 * something an identity may do.
 *
 * ### The fix
 *
 * One canonical value per built-in tag, and the interface translates it for display only. The
 * canonical value is the **English label** rather than an invented code like `#study`, because tags
 * are user-visible in exports, backups and search: a Markdown export reading `Study` is a real word
 * someone can act on, where `#tag_1` is a support ticket.
 *
 * [canonical] recognises all four languages' labels, so notes tagged before this existed keep
 * working — they are matched correctly on read and quietly rewritten to the canonical form the next
 * time the note is saved. Nothing has to be migrated, and a tag the user invented themselves is
 * passed through untouched.
 *
 * The translations are read from the language tables rather than restated here. Two lists of the
 * same five words in four languages would agree today and drift the first time one of them is
 * adjusted, and the drift would present as this very bug coming back.
 */
object NoteTags {

    private val TABLES: List<Tr> = listOf(En, Zh, Ja, Ko)

    private fun labelsOf(pick: (Tr) -> String): List<String> = TABLES.map(pick)

    /** Canonical value -> how to read it out of a language table. */
    private val BUILT_IN: List<Pair<String, (Tr) -> String>> = listOf(
        En.tagStudy to { t: Tr -> t.tagStudy },
        En.tagWork to { t: Tr -> t.tagWork },
        En.tagGame to { t: Tr -> t.tagGame },
        En.tagSports to { t: Tr -> t.tagSports },
        En.tagOther to { t: Tr -> t.tagOther }
    )

    /** The five starter tags, in canonical form. Offered on every note. */
    val DEFAULTS: List<String> = BUILT_IN.map { it.first }

    /**
     * Fold any known spelling of a built-in tag — in any of the four languages — down to its
     * canonical value. Anything unrecognised is the user's own tag and is returned as given.
     */
    fun canonical(tag: String): String {
        val t = tag.trim()
        if (t.isEmpty()) return t
        BUILT_IN.forEach { (key, pick) ->
            if (labelsOf(pick).any { it.equals(t, ignoreCase = true) }) return key
        }
        return t
    }

    /** How a tag should be shown right now. Built-ins are translated; custom tags are themselves. */
    fun label(tag: String): String {
        BUILT_IN.forEach { (key, pick) -> if (key.equals(tag, ignoreCase = true)) return pick(S) }
        return tag
    }

    /** Split a stored `tags` column into canonical values. */
    fun parse(stored: String): List<String> =
        stored.split(",").map { canonical(it) }.filter { it.isNotBlank() }.distinct()

    /** Render a stored column for display, translated and separated for a card subtitle. */
    fun displayLine(stored: String, separator: String = " \u00b7 "): String =
        parse(stored).joinToString(separator) { label(it) }
}
