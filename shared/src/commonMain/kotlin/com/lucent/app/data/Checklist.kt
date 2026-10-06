package com.lucent.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid
import kotlin.uuid.ExperimentalUuidApi

@Serializable
data class ChecklistItem(
    val id: String = "",
    val text: String = "",
    val done: Boolean = false
)

@OptIn(ExperimentalUuidApi::class)
object Checklist {

    private val jsonParser = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun parse(json: String?): List<ChecklistItem> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val list = jsonParser.decodeFromString<List<ChecklistItem>>(json)
            list.map { if (it.id.isBlank()) it.copy(id = Uuid.random().toString()) else it }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun serialize(list: List<ChecklistItem>): String {
        return jsonParser.encodeToString(list)
    }

    fun newItem(text: String): ChecklistItem =
        ChecklistItem(id = Uuid.random().toString(), text = text.trim())

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
