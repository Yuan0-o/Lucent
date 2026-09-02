package com.lucent.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * PHASE 3 (review F-1) — saved searches, persisted as settings JSON.
 *
 * [SearchQuery] already parses a real filter language (`is:overdue`, `priority:high`, `due:week`,
 * `link:`, `has:` …); what was missing is a way to KEEP a query. This module is deliberately just
 * the storage arithmetic — parse, serialize, add, remove — over one JSON string that lives in the
 * settings store. No schema change, no new table: a saved search is a name and a query string, and
 * settings JSON is exactly the right weight for a dozen of those. The list is capped so the chips
 * row stays a row of shortcuts rather than becoming a second, unsearchable list to manage.
 *
 * Adding under an existing name REPLACES that entry (same rule a file save dialog uses): two chips
 * with one name would be indistinguishable on screen, so the name is the identity.
 */
object SavedSearches {

    /** Enough for a chips row; the 13th saved view is a sign the user wants folders, not chips. */
    const val MAX = 12

    data class Entry(val name: String, val query: String)

    fun parse(json: String?): List<Entry> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val name = o.optString("n", "")
                val query = o.optString("q", "")
                if (name.isBlank() || query.isBlank()) null else Entry(name, query)
            }
        } catch (t: Throwable) {
            // A corrupt value behaves like an empty list rather than a crash loop in a screen
            // that exists to find things.
            emptyList()
        }
    }

    fun serialize(list: List<Entry>): String {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("n", it.name).put("q", it.query)) }
        return arr.toString()
    }

    /** Add (or replace, by name) and cap at [MAX], newest kept. Returns the new JSON. */
    fun add(json: String?, name: String, query: String): String {
        val trimmedName = name.trim()
        val trimmedQuery = query.trim()
        if (trimmedName.isBlank() || trimmedQuery.isBlank()) return json ?: ""
        val without = parse(json).filterNot { it.name.equals(trimmedName, ignoreCase = true) }
        val next = (without + Entry(trimmedName, trimmedQuery)).takeLast(MAX)
        return serialize(next)
    }

    /** Remove by name. Returns the new JSON. */
    fun remove(json: String?, name: String): String =
        serialize(parse(json).filterNot { it.name == name })
}
