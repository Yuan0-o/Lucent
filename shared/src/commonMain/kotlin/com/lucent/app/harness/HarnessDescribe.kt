package com.lucent.app.harness

import com.lucent.app.i18n.S
import org.json.JSONArray
import org.json.JSONObject

object HarnessDescribe {

    private val PATH_KEYS = listOf("path", "from", "to", "source", "target", "file", "output", "input", "directory")
    private val QUERY_KEYS = listOf("query", "command", "url", "pattern", "prompt", "task", "question", "name")

    fun describe(name: String, argumentsJson: String): String {
        val args = try { JSONObject(argumentsJson) } catch (e: Exception) { JSONObject() }
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
        val args = try { JSONObject(argumentsJson) } catch (e: Exception) { return emptyList() }
        val out = mutableListOf<String>()
        PATH_KEYS.forEach { key ->
            val value = args.optString(key, "")
            if (value.isNotBlank()) out.add(value)
        }
        val array = args.optJSONArray("paths")
        if (array != null) {
            for (i in 0 until array.length()) {
                val value = array.optString(i, "")
                if (value.isNotBlank()) out.add(value)
            }
        }
        return out
    }

    private fun first(args: JSONObject, keys: List<String>): String {
        keys.forEach { key ->
            val value = args.optString(key, "")
            if (value.isNotBlank()) return value
        }
        return ""
    }

    fun json(value: Any?): String = when (value) {
        null -> "null"
        is JSONObject, is JSONArray -> value.toString()
        else -> value.toString()
    }

    fun text(value: Any?): String = when (value) {
        null -> ""
        is String -> value
        else -> value.toString()
    }
}
