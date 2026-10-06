package com.lucent.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

object CustomTemplates {

    const val MAX = 24

    @Serializable
    data class Template(
        @SerialName("id") val id: String,
        @SerialName("name") val name: String,
        @SerialName("title") val title: String = "",
        @SerialName("body") val body: String = "",
        @SerialName("tags") val tags: List<String> = emptyList(),
        @SerialName("colorKey") val colorKey: String? = null,
        @SerialName("pinned") val pinned: Boolean = false,
        @SerialName("isChecklist") val isChecklist: Boolean = false,
        @SerialName("checklistTexts") val checklistTexts: List<String> = emptyList()
    )

    @Serializable
    data class Draft(
        @SerialName("title") val title: String = "",
        @SerialName("body") val body: String = "",
        @SerialName("tags") val tags: List<String> = emptyList(),
        @SerialName("colorKey") val colorKey: String? = null,
        @SerialName("pinned") val pinned: Boolean = false,
        @SerialName("isChecklist") val isChecklist: Boolean = false,
        @SerialName("checklistTexts") val checklistTexts: List<String> = emptyList()
    )

    private val jsonConfig = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    fun parse(json: String?): List<Template> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = jsonConfig.decodeFromString<JsonArray>(json)
            arr.indices.mapNotNull { i ->
                val o = arr[i] as? JsonObject ?: return@mapNotNull null
                val id = (o["id"] as? JsonPrimitive)?.content ?: ""
                if (id.isBlank()) null else Template(
                    id = id,
                    name = (o["name"] as? JsonPrimitive)?.content ?: "",
                    title = (o["title"] as? JsonPrimitive)?.content ?: "",
                    body = (o["body"] as? JsonPrimitive)?.content ?: "",
                    tags = o["tags"].toStringList(),
                    colorKey = if (o["colorKey"] == null || o["colorKey"] is kotlinx.serialization.json.JsonNull) null else (o["colorKey"] as? JsonPrimitive)?.content?.ifBlank { null },
                    pinned = (o["pinned"] as? JsonPrimitive)?.booleanOrNull ?: false,
                    isChecklist = (o["isChecklist"] as? JsonPrimitive)?.booleanOrNull ?: false,
                    checklistTexts = o["checklistTexts"].toStringList()
                )
            }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    fun serialize(list: List<Template>): String {
        return jsonConfig.encodeToString(list)
    }

    private fun kotlinx.serialization.json.JsonElement?.toStringList(): List<String> {
        val a = this as? JsonArray ?: return emptyList()
        return a.indices.mapNotNull { i -> (a[i] as? JsonPrimitive)?.content?.ifBlank { null } }
    }

    fun upsert(json: String?, t: Template): String {
        val without = parse(json).filterNot { it.id == t.id }
        return serialize((without + t).takeLast(MAX))
    }

    fun remove(json: String?, id: String): String =
        serialize(parse(json).filterNot { it.id == id })

    fun draftToJson(d: Draft): String = jsonConfig.encodeToString(d)

    fun parseDraft(json: String?): Draft {
        if (json.isNullOrBlank()) return Draft()
        return try {
            val o = jsonConfig.decodeFromString<JsonObject>(json)
            Draft(
                title = (o["title"] as? JsonPrimitive)?.content ?: "",
                body = (o["body"] as? JsonPrimitive)?.content ?: "",
                tags = o["tags"].toStringList(),
                colorKey = if (o["colorKey"] == null || o["colorKey"] is kotlinx.serialization.json.JsonNull) null else (o["colorKey"] as? JsonPrimitive)?.content?.ifBlank { null },
                pinned = (o["pinned"] as? JsonPrimitive)?.booleanOrNull ?: false,
                isChecklist = (o["isChecklist"] as? JsonPrimitive)?.booleanOrNull ?: false,
                checklistTexts = o["checklistTexts"].toStringList()
            )
        } catch (t: Throwable) {
            Draft()
        }
    }

    fun draftEmpty(d: Draft): Boolean =
        d.title.isBlank() && d.body.isBlank() && d.tags.isEmpty() &&
            !d.pinned && d.colorKey == null && !d.isChecklist && d.checklistTexts.isEmpty()
}
