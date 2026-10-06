package com.lucent.app.harness

import com.lucent.app.i18n.S
import kotlinx.serialization.json.*

object HarnessDescribe {

    private val PATH_KEYS = listOf("path", "from", "to", "source", "target", "file", "output", "input", "directory")
    private val QUERY_KEYS = listOf("query", "command", "url", "pattern", "prompt", "task", "question", "name")

    fun describe(name: String, argumentsJson: String): String {
        val args = try { Json.parseToJsonElement(argumentsJson).jsonObject } catch (e: Exception) { JsonObject(emptyMap()) }
        val path = first(args, PATH_KEYS)
        val query = first(args, QUERY_KEYS)
        val head = if (query.isNotBlank()) query else path
        return when {
            head.isBlank() -> S.harnessRunTool(name)
            head.length > 120 -> S.harnessRunToolOn(name, head.take(120) + "…")
            else -> S.harnessRunToolOn(name, head)
        }
    }

    fun digest(argumentsJson: String): String =
        argumentsJson.replace(Regex("\\s+"), " ").take(300)

    fun files(argumentsJson: String): List<String> {
        val args = try { Json.parseToJsonElement(argumentsJson).jsonObject } catch (e: Exception) { return emptyList() }
        val out = mutableListOf<String>()
        PATH_KEYS.forEach { key ->
            val value = args[key]?.jsonPrimitive?.content ?: ""
            if (value.isNotBlank()) out.add(value)
        }
        val array = args["paths"]?.jsonArray
        if (array != null) {
            for (i in 0 until array.size) {
                val value = array[i].jsonPrimitive.content
                if (value.isNotBlank()) out.add(value)
            }
        }
        return out
    }

    private fun first(args: JsonObject, keys: List<String>): String {
        keys.forEach { key ->
            val value = args[key]?.jsonPrimitive?.content ?: ""
            if (value.isNotBlank()) return value
        }
        return ""
    }

    fun json(value: Any?): String = when (value) {
        null -> "null"
        is JsonObject, is JsonArray -> value.toString()
        else -> value.toString()
    }

    fun text(value: Any?): String = when (value) {
        null -> ""
        is String -> value
        else -> value.toString()
    }
}
