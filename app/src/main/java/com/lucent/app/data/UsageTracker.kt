package com.lucent.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import kotlin.math.pow

private val Context.usageDataStore by preferencesDataStore(name = "lucent_usage")

object UsageTracker {

    enum class Kind(val storeKey: String) { NOTE("notes_usage"), TASK("tasks_usage") }

    private fun keyFor(kind: Kind) = stringPreferencesKey(kind.storeKey)

    private const val RECENCY_HALF_LIFE_DAYS = 5.0
    private const val DAY_MILLIS = 24.0 * 60 * 60 * 1000

    private data class Entry(val count: Int, val lastOpened: Long)

    private fun parse(json: String?): Map<Long, Entry> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val obj = JSONObject(json)
            buildMap {
                obj.keys().forEach { k ->
                    val id = k.toLongOrNull() ?: return@forEach
                    val e = obj.optJSONObject(k) ?: return@forEach
                    put(id, Entry(e.optInt("c", 0), e.optLong("t", 0L)))
                }
            }
        } catch (t: Throwable) {
            emptyMap()
        }
    }

    private fun serialize(map: Map<Long, Entry>): String {
        val obj = JSONObject()
        map.forEach { (id, e) ->
            obj.put(id.toString(), JSONObject().put("c", e.count).put("t", e.lastOpened))
        }
        return obj.toString()
    }

    suspend fun clearAll(context: Context) {
        context.usageDataStore.edit { it.clear() }
    }

    suspend fun recordOpen(context: Context, kind: Kind, id: Long) {
        val now = System.currentTimeMillis()
        context.usageDataStore.edit { prefs ->
            val map = parse(prefs[keyFor(kind)]).toMutableMap()
            val existing = map[id]
            map[id] = Entry((existing?.count ?: 0) + 1, now)
            prefs[keyFor(kind)] = serialize(map)
        }
    }

    fun scores(context: Context, kind: Kind): Flow<Map<Long, Double>> =
        context.usageDataStore.data.map { prefs ->
            val now = System.currentTimeMillis()
            parse(prefs[keyFor(kind)]).mapValues { (_, e) -> openScore(e.count, e.lastOpened, now) }
        }

    private fun openScore(count: Int, lastOpened: Long, now: Long): Double {
        if (count <= 0) return 0.0
        val ageDays = ((now - lastOpened).coerceAtLeast(0)).toDouble() / DAY_MILLIS
        val recency = 0.5.pow(ageDays / RECENCY_HALF_LIFE_DAYS)
        return (1.0 + count).pow(0.6) * recency
    }

    fun score(openActivity: Double, updatedAt: Long, now: Long): Double {
        val ageDays = ((now - updatedAt).coerceAtLeast(0)).toDouble() / DAY_MILLIS
        val editRecency = 0.5.pow(ageDays / RECENCY_HALF_LIFE_DAYS)
        return openActivity + editRecency
    }
}
