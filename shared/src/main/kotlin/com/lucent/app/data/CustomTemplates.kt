package com.lucent.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * v2.7.2 — user-defined note templates, persisted as settings JSON.
 *
 * The four built-in templates live in [NoteTemplate] and stay there; this module is the storage
 * arithmetic for templates the user makes from the composer, exactly the way [SavedSearches] holds
 * its list: one JSON string in the settings store, no schema change, no new table. A template is
 * the same thing [NoteTemplate.prefill] hands the composer — title, body, tags, checklist mode and
 * items — plus the two choices a composed note can carry that the built-ins never touch (pin and
 * colour), so a saved template restores the note exactly as it was when it was captured.
 *
 * Identity is a string id (UUID) rather than a name, because unlike saved searches two templates
 * may legitimately share a title; the list is capped so the chip row stays a row.
 *
 * The interrupted-authoring draft rides the same JSON shape under its own settings key (see
 * [Draft]); writing it is the UI's job — this file only defines the shape and the arithmetic.
 */
object CustomTemplates {

    /** Enough for a chips row. */
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

    /** The interrupted-authoring record: the composer fields of an unfinished template. */
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

    /** Add (or replace, by id) and cap at [MAX]. Returns the new JSON. */
    fun upsert(json: String?, t: Template): String {
        val without = parse(json).filterNot { it.id == t.id }
        return serialize((without + t).takeLast(MAX))
    }

    /** Remove by id. Returns the new JSON. */
    fun remove(json: String?, id: String): String =
        serialize(parse(json).filterNot { it.id == id })

    // ---- Draft shape ----

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

    /** Whether a draft holds anything worth continuing. */
    fun draftEmpty(d: Draft): Boolean =
        d.title.isBlank() && d.body.isBlank() && d.tags.isEmpty() &&
            !d.pinned && d.colorKey == null && !d.isChecklist && d.checklistTexts.isEmpty()
}
