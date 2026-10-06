package com.lucent.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object AutoBackup {

    const val MIN_INTERVAL_HOURS = 6

    val INTERVAL_CHOICES = listOf(6, 12, 24, 24 * 7)

    const val DEFAULT_INTERVAL_HOURS = 12

    const val DEFAULT_KEEP = 5
    val KEEP_RANGE = 1..20

    const val FILE_PREFIX = "lucent-auto-"
    const val FILE_SUFFIX = ".lcb"

    val MODULES: Set<BackupModule>
        get() = DEFAULT_BACKUP_MODULES

    @Serializable
    data class State(
        val enabled: Boolean = false,
        val folderUri: String = "",
        val intervalHours: Int = DEFAULT_INTERVAL_HOURS,
        val keep: Int = DEFAULT_KEEP,
        val lastRunAt: Long = 0L,
        val lastError: String = ""
    ) {
        val runnable: Boolean get() = enabled && folderUri.isNotBlank()

        fun toJson(): String = Json.encodeToString(this)

        companion object {
            val EMPTY = State()

            fun fromJson(json: String): State {
                if (json.isBlank()) return EMPTY
                return try {
                    val decoded = Json.decodeFromString<State>(json)
                    decoded.copy(
                        intervalHours = decoded.intervalHours.coerceAtLeast(MIN_INTERVAL_HOURS),
                        keep = decoded.keep.coerceIn(KEEP_RANGE)
                    )
                } catch (_: Throwable) {
                    EMPTY
                }
            }
        }
    }

    fun nextDueAt(state: State): Long =
        if (state.lastRunAt <= 0L) 0L
        else state.lastRunAt + state.intervalHours.coerceAtLeast(MIN_INTERVAL_HOURS) * 3_600_000L

    fun isDue(state: State, now: Long = System.currentTimeMillis()): Boolean =
        state.runnable && now >= nextDueAt(state)

    fun fileNameFor(timestamp: Long): String {
        val fmt = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
        return "$FILE_PREFIX${fmt.format(java.util.Date(timestamp))}$FILE_SUFFIX"
    }

    fun isOurs(name: String): Boolean = name.startsWith(FILE_PREFIX) && name.endsWith(FILE_SUFFIX)

    fun filesToDelete(existingNames: List<String>, keep: Int): List<String> {
        val ours = existingNames.filter { isOurs(it) }.sorted()
        val surplus = ours.size - keep.coerceIn(KEEP_RANGE)
        return if (surplus <= 0) emptyList() else ours.take(surplus)
    }
}
