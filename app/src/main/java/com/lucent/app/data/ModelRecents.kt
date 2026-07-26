package com.lucent.app.data

import org.json.JSONArray

/**
 * The short list of models the user has recently switched the assistant to (B-group task 5).
 *
 * The quick switcher on the Assistant screen needs somewhere to switch *to*. Fetching the provider's
 * catalogue works but needs the network, takes a moment, and returns dozens of ids for a user who in
 * practice alternates between two or three. So every switch is remembered here, most recent first,
 * and the switcher offers this list instantly with the fetched catalogue as a second, opt-in step.
 *
 * Stored as a plain JSON array of strings. Model ids are not secrets — they are public product names
 * — so unlike the API key and base URL this is deliberately NOT encrypted at rest, matching how the
 * theme and language keys are handled.
 */
object ModelRecents {

    /** How many to keep. Long enough to cover real alternation, short enough to stay a menu. */
    const val MAX = 8

    /** Parse the stored JSON into a list, newest first. Never throws; unreadable data reads empty. */
    fun parse(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length())
                .mapNotNull { arr.optString(it, "").takeIf { s -> s.isNotBlank() } }
                .distinct()
                .take(MAX)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Return the stored JSON with [model] moved to the front, de-duplicated and capped at [MAX].
     * Re-selecting a model that is already in the list promotes it rather than adding a second copy,
     * so the menu stays ordered by genuine recency.
     */
    fun add(json: String?, model: String): String {
        val trimmed = model.trim()
        if (trimmed.isBlank()) return json ?: "[]"
        val next = (listOf(trimmed) + parse(json).filter { it != trimmed }).take(MAX)
        val arr = JSONArray()
        next.forEach { arr.put(it) }
        return arr.toString()
    }

    fun serialize(models: List<String>): String {
        val arr = JSONArray()
        models.distinct().take(MAX).forEach { arr.put(it) }
        return arr.toString()
    }
}
