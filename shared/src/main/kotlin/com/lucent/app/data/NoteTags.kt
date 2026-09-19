package com.lucent.app.data

import com.lucent.app.i18n.En
import com.lucent.app.i18n.Ja
import com.lucent.app.i18n.Ko
import com.lucent.app.i18n.S
import com.lucent.app.i18n.Tr
import com.lucent.app.i18n.Zh

object NoteTags {

    private val TABLES: List<Tr> = listOf(En, Zh, Ja, Ko)

    private val BUILT_IN: List<Pair<String, (Tr) -> String>> = listOf(
        En.tagStudy to { t: Tr -> t.tagStudy },
        En.tagWork to { t: Tr -> t.tagWork },
        En.tagGame to { t: Tr -> t.tagGame },
        En.tagSports to { t: Tr -> t.tagSports },
        En.tagOther to { t: Tr -> t.tagOther }
    )

    val DEFAULTS: List<String> = BUILT_IN.map { it.first }

    private val CANONICAL_BY_LABEL: Map<String, String> = HashMap<String, String>().apply {
        BUILT_IN.forEach { (key, pick) -> TABLES.forEach { put(pick(it).lowercase(), key) } }
    }

    fun canonical(tag: String): String {
        val t = tag.trim()
        if (t.isEmpty()) return t
        return CANONICAL_BY_LABEL[t.lowercase()] ?: t
    }

    fun label(tag: String): String {
        BUILT_IN.forEach { (key, pick) -> if (key.equals(tag, ignoreCase = true)) return pick(S) }
        return tag
    }

    fun parse(stored: String): List<String> =
        stored.split(",").map { canonical(it) }.filter { it.isNotBlank() }.distinct()

    fun displayLine(stored: String, separator: String = " \u00b7 "): String =
        parse(stored).joinToString(separator) { label(it) }
}
