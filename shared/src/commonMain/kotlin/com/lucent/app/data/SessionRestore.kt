package com.lucent.app.data

import com.lucent.app.platform.PlatformContext
import com.lucent.app.data.createSettingsRepository

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

object SessionRestore {

    const val KIND_NOTE = "note"
    const val KIND_TASK = "task"

    @Serializable
    data class Snapshot(
        val kind: String,
        val itemId: Long?,
        val title: String,
        val payload: String,
        val savedAt: Long = 0L
    )

    @Volatile
    var pending: Snapshot? = null
        private set

    @Volatile
    var asked: Boolean = false

    var restoring: Snapshot? by mutableStateOf(null)
        private set

    fun hydrate(stored: String?) {
        pending = stored?.takeIf { it.isNotBlank() }?.let { parse(it) }
    }

    fun beginRestore(snapshot: Snapshot) {
        pending = null
        restoring = snapshot
    }

    fun decline() {
        pending = null
    }

    fun consumeRestore(kind: String): Snapshot? {
        val current = restoring ?: return null
        if (current.kind != kind) return null
        restoring = null
        return current
    }


    suspend fun save(context: PlatformContext, snapshot: Snapshot) {
        runCatching {
            createSettingsRepository(context)
                .setSessionSnapshot(serialize(snapshot.copy(savedAt = System.currentTimeMillis())))
        }
    }

    suspend fun clear(context: PlatformContext) {
        runCatching { createSettingsRepository(context).setSessionSnapshot("") }
    }


    fun serialize(s: Snapshot): String = Json.encodeToString(s)

    fun parse(json: String): Snapshot? = try {
        val s = Json.decodeFromString<Snapshot>(json)
        if (s.kind != KIND_NOTE && s.kind != KIND_TASK) null else s
    } catch (_: Throwable) {
        null
    }


    fun put(vararg pairs: Pair<String, Any?>): String {
        return buildJsonObject {
            pairs.forEach { (k, v) ->
                val element = when (v) {
                    null -> JsonNull
                    is String -> JsonPrimitive(v)
                    is Number -> JsonPrimitive(v)
                    is Boolean -> JsonPrimitive(v)
                    else -> JsonPrimitive(v.toString())
                }
                put(k, element)
            }
        }.toString()
    }

    fun read(payload: String): JsonObject = try {
        Json.parseToJsonElement(payload) as? JsonObject ?: buildJsonObject {}
    } catch (_: Throwable) {
        buildJsonObject {}
    }
}
