package com.lucent.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

object SessionRestore {

    const val KIND_NOTE = "note"
    const val KIND_TASK = "task"

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


    suspend fun save(context: android.content.Context, snapshot: Snapshot) {
        runCatching {
            SettingsRepository(context)
                .setSessionSnapshot(serialize(snapshot.copy(savedAt = System.currentTimeMillis())))
        }
    }

    suspend fun clear(context: android.content.Context) {
        runCatching { SettingsRepository(context).setSessionSnapshot("") }
    }


    fun serialize(s: Snapshot): String = JSONObject()
        .put("kind", s.kind)
        .put("itemId", s.itemId ?: JSONObject.NULL)
        .put("title", s.title)
        .put("payload", s.payload)
        .put("savedAt", s.savedAt)
        .toString()

    fun parse(json: String): Snapshot? = try {
        val o = JSONObject(json)
        val kind = o.optString("kind")
        if (kind != KIND_NOTE && kind != KIND_TASK) null
        else Snapshot(
            kind = kind,
            itemId = if (o.isNull("itemId")) null else o.optLong("itemId"),
            title = o.optString("title"),
            payload = o.optString("payload"),
            savedAt = o.optLong("savedAt")
        )
    } catch (_: Throwable) {
        null
    }


    fun put(vararg pairs: Pair<String, Any?>): String {
        val o = JSONObject()
        pairs.forEach { (k, v) -> o.put(k, v ?: JSONObject.NULL) }
        return o.toString()
    }

    fun read(payload: String): JSONObject = try {
        JSONObject(payload)
    } catch (_: Throwable) {
        JSONObject()
    }
}
