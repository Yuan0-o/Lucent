package com.lucent.app.data

import org.json.JSONArray

object ModelRecents {

    const val MAX = 8

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
