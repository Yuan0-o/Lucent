package com.lucent.app.data

import org.json.JSONArray
import org.json.JSONObject

object SavedSearches {

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
            emptyList()
        }
    }

    fun serialize(list: List<Entry>): String {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("n", it.name).put("q", it.query)) }
        return arr.toString()
    }

    fun add(json: String?, name: String, query: String): String {
        val trimmedName = name.trim()
        val trimmedQuery = query.trim()
        if (trimmedName.isBlank() || trimmedQuery.isBlank()) return json ?: ""
        val without = parse(json).filterNot { it.name.equals(trimmedName, ignoreCase = true) }
        val next = (without + Entry(trimmedName, trimmedQuery)).takeLast(MAX)
        return serialize(next)
    }

    fun remove(json: String?, name: String): String =
        serialize(parse(json).filterNot { it.name == name })
}
