package com.lucent.app.data
import okio.Path.Companion.toPath
import com.lucent.app.platform.getFilesDir
import kotlinx.serialization.json.*
import kotlinx.serialization.json.jsonObject
import com.lucent.app.platform.applicationContext
import com.lucent.app.platform.filesDir

import com.lucent.app.platform.PlatformContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import kotlin.math.pow

actual object UsageTracker {

    private const val RECENCY_HALF_LIFE_DAYS = 5.0
    private const val DAY_MILLIS = 24.0 * 60 * 60 * 1000

    private data class Entry(val count: Int, val lastOpened: Long)

    private val state = MutableStateFlow<Map<String, String>>(emptyMap())
    private val mutex = Mutex()
    @Volatile private var loaded = false

    private fun file(context: PlatformContext) = (context.applicationContext.filesDir / "lucent_usage.json")

    private fun ensureLoaded(context: PlatformContext) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            state.value = try {
                val f = file(context)
                if (!okio.FileSystem.SYSTEM.exists(f.toString().toPath())) emptyMap() else {
                    val obj = kotlinx.serialization.json.Json.parseToJsonElement(f.readText()).jsonObject
                    buildMap { obj.entries.forEach { (k, v) -> put(k, v.jsonPrimitive.content) } }
                }
            } catch (t: Throwable) {
                emptyMap()
            }
            loaded = true
        }
    }

    private fun persist(context: PlatformContext, values: Map<String, String>) {
        try {
            val obj = buildJsonObject { values.forEach { (k, v) -> put(k, v) } }
            val f = file(context)
            val tmp = java.io.File(f.parentFile, f.name + ".tmp")
            tmp.writeText(Json.encodeToString(obj))
            okio.FileSystem.SYSTEM.atomicMove(tmp.toString().toPath(), f.toString().toPath())
        } catch (_: Throwable) {
        }
    }

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
        val obj = buildJsonObject { map.forEach { (id, e) -> put(id.toString(), buildJsonObject { put("c", e.count); put("t", e.lastOpened) }) } }
        return Json.encodeToString(obj)
    }

    actual suspend fun clearAll(context: PlatformContext) {
        ensureLoaded(context)
        mutex.withLock {
            state.value = emptyMap()
            persist(context, emptyMap())
        }
    }

    actual suspend fun recordOpen(context: PlatformContext, kind: UsageKind, id: Long) {
        ensureLoaded(context)
        val now = System.currentTimeMillis()
        mutex.withLock {
            val current = state.value.toMutableMap()
            val map = parse(current[kind.storeKey]).toMutableMap()
            val existing = map[id]
            map[id] = Entry((existing?.count ?: 0) + 1, now)
            current[kind.storeKey] = serialize(map)
            state.value = current
            persist(context, current)
        }
    }

    actual fun scores(context: PlatformContext, kind: UsageKind): Flow<Map<Long, Double>> {
        ensureLoaded(context)
        return state.map { values ->
            val now = System.currentTimeMillis()
            parse(values[kind.storeKey]).mapValues { (_, e) -> openScore(e.count, e.lastOpened, now) }
        }
    }

    private fun openScore(count: Int, lastOpened: Long, now: Long): Double {
        if (count <= 0) return 0.0
        val ageDays = ((now - lastOpened).coerceAtLeast(0)).toDouble() / DAY_MILLIS
        val recency = 0.5.pow(ageDays / RECENCY_HALF_LIFE_DAYS)
        return (1.0 + count).pow(0.6) * recency
    }

    actual fun score(openActivity: Double, updatedAt: Long, now: Long): Double {
        val ageDays = ((now - updatedAt).coerceAtLeast(0)).toDouble() / DAY_MILLIS
        val editRecency = 0.5.pow(ageDays / RECENCY_HALF_LIFE_DAYS)
        return openActivity + editRecency
    }
}
