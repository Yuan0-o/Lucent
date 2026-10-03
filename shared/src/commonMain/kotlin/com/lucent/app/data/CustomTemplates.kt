package com.lucent.app.data

import org.json.JSONArray
import org.json.JSONObject

object CustomTemplates {

    const val MAX = 24

    data class Template(
        val id: String,
        val name: String,
        val title: String = "",
        val body: String = "",
        val tags: List<String> = emptyList(),
        val colorKey: String? = null,
        val pinned: Boolean = false,
        val isChecklist: Boolean = false,
        val checklistTexts: List<String> = emptyList()
    )

    data class Draft(
        val title: String = "",
        val body: String = "",
        val tags: List<String> = emptyList(),
        val colorKey: String? = null,
        val pinned: Boolean = false,
        val isChecklist: Boolean = false,
        val checklistTexts: List<String> = emptyList()
    )

    fun parse(json: String?): List<Template> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id", "")
                if (id.isBlank()) null else Template(
                    id = id,
                    name = o.optString("name", ""),
                    title = o.optString("title", ""),
                    body = o.optString("body", ""),
                    tags = o.optJSONArray("tags").toStringList(),
                    colorKey = if (o.isNull("colorKey")) null else o.optString("colorKey", "").ifBlank { null },
                    pinned = o.optBoolean("pinned", false),
                    isChecklist = o.optBoolean("isChecklist", false),
                    checklistTexts = o.optJSONArray("checklistTexts").toStringList()
                )
            }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    fun serialize(list: List<Template>): String {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        return arr.toString()
    }

    private fun JSONObject.putList(key: String, values: List<String>): JSONObject {
        val a = JSONArray()
        values.forEach { a.put(it) }
        return put(key, a)
    }

    private fun toJson(t: Template): JSONObject = JSONObject()
        .put("id", t.id)
        .put("name", t.name)
        .put("title", t.title)
        .put("body", t.body)
        .putList("tags", t.tags)
        .put("colorKey", t.colorKey ?: JSONObject.NULL)
        .put("pinned", t.pinned)
        .put("isChecklist", t.isChecklist)
        .putList("checklistTexts", t.checklistTexts)

    private fun org.json.JSONArray?.toStringList(): List<String> {
        val a = this ?: return emptyList()
        return (0 until a.length()).mapNotNull { i -> a.optString(i, "").ifBlank { null } }
    }

    fun upsert(json: String?, t: Template): String {
        val without = parse(json).filterNot { it.id == t.id }
        return serialize((without + t).takeLast(MAX))
    }

    fun remove(json: String?, id: String): String =
        serialize(parse(json).filterNot { it.id == id })


    fun draftToJson(d: Draft): String = JSONObject()
        .put("title", d.title)
        .put("body", d.body)
        .putList("tags", d.tags)
        .put("colorKey", d.colorKey ?: JSONObject.NULL)
        .put("pinned", d.pinned)
        .put("isChecklist", d.isChecklist)
        .putList("checklistTexts", d.checklistTexts)
        .toString()

    fun parseDraft(json: String?): Draft {
        if (json.isNullOrBlank()) return Draft()
        return try {
            val o = JSONObject(json)
            Draft(
                title = o.optString("title", ""),
                body = o.optString("body", ""),
                tags = o.optJSONArray("tags").toStringList(),
                colorKey = if (o.isNull("colorKey")) null else o.optString("colorKey", "").ifBlank { null },
                pinned = o.optBoolean("pinned", false),
                isChecklist = o.optBoolean("isChecklist", false),
                checklistTexts = o.optJSONArray("checklistTexts").toStringList()
            )
        } catch (t: Throwable) {
            Draft()
        }
    }

    fun draftEmpty(d: Draft): Boolean =
        d.title.isBlank() && d.body.isBlank() && d.tags.isEmpty() &&
            !d.pinned && d.colorKey == null && !d.isChecklist && d.checklistTexts.isEmpty()
}
