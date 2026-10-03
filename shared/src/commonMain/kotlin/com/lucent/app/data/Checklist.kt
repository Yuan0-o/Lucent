package com.lucent.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ChecklistItem(
    val id: String,
    val text: String,
    val done: Boolean = false
)

object Checklist {

    fun parse(json: String?): List<ChecklistItem> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                ChecklistItem(
                    id = o.optString("id", "").ifBlank { UUID.randomUUID().toString() },
                    text = o.optString("text", ""),
                    done = o.optBoolean("done", false)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun serialize(list: List<ChecklistItem>): String {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("text", it.text)
                    .put("done", it.done)
            )
        }
        return arr.toString()
    }

    fun newItem(text: String): ChecklistItem =
        ChecklistItem(id = UUID.randomUUID().toString(), text = text.trim())

    fun add(json: String, text: String): String {
        if (text.isBlank()) return json
        return serialize(parse(json) + newItem(text))
    }

    fun addAll(json: String, raw: String): String {
        val texts = raw.split('\n', ';').map { it.trim() }.filter { it.isNotEmpty() }
        if (texts.isEmpty()) return json
        return serialize(parse(json) + texts.map { newItem(it) })
    }

    fun toggle(json: String, id: String): String =
        serialize(parse(json).map { if (it.id == id) it.copy(done = !it.done) else it })

    fun setDone(json: String, id: String, done: Boolean): String =
        serialize(parse(json).map { if (it.id == id) it.copy(done = done) else it })

    fun updateText(json: String, id: String, newText: String): String =
        serialize(parse(json).map { if (it.id == id) it.copy(text = newText) else it })

    fun remove(json: String, id: String): String =
        serialize(parse(json).filterNot { it.id == id })

    fun progress(json: String?): Pair<Int, Int>? {
        val list = parse(json)
        if (list.isEmpty()) return null
        return list.count { it.done } to list.size
    }

    fun resetDone(json: String?): String = serialize(parse(json).map { it.copy(done = false) })

    fun completeAll(json: String?): String = serialize(parse(json).map { it.copy(done = true) })

    fun findByText(json: String?, query: String): ChecklistItem? {
        val q = query.trim()
        if (q.isEmpty()) return null
        val list = parse(json)
        list.firstOrNull { it.text.equals(q, ignoreCase = true) }?.let { return it }
        val partial = list.filter { it.text.contains(q, ignoreCase = true) }
        return if (partial.size == 1) partial.first() else null
    }

    fun toMarkdown(json: String?): String =
        parse(json).joinToString("\n") { "- [${if (it.done) "x" else " "}] ${it.text}" }
}
