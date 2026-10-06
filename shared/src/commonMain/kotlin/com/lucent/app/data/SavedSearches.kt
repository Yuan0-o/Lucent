package com.lucent.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object SavedSearches {

    const val MAX = 12

    @Serializable
    data class Entry(
        @SerialName("n") val name: String,
        @SerialName("q") val query: String
    )

    fun parse(json: String?): List<Entry> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            Json.decodeFromString<List<Entry>>(json)
        } catch (t: Throwable) {
            emptyList()
        }
    }

    fun serialize(list: List<Entry>): String {
        return Json.encodeToString(list)
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
