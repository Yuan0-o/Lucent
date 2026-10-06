package com.lucent.app.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

object ModelRecents {

    const val MAX = 8

    fun parse(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = Json.decodeFromString<JsonArray>(json)
            arr.mapNotNull { (it as? JsonPrimitive)?.content?.takeIf { s -> s.isNotBlank() } }
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
        val arr = JsonArray(next.map { JsonPrimitive(it) })
        return Json.encodeToString(arr)
    }

    fun serialize(models: List<String>): String {
        val arr = JsonArray(models.distinct().take(MAX).map { JsonPrimitive(it) })
        return Json.encodeToString(arr)
    }
}
