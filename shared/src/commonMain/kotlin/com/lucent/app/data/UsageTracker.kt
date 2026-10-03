package com.lucent.app.data

import com.lucent.app.platform.PlatformContext
import kotlinx.coroutines.flow.Flow

enum class UsageKind(val storeKey: String) {
    NOTE("notes_usage"),
    TASK("tasks_usage")
}

expect object UsageTracker {
    suspend fun clearAll(context: PlatformContext)
    suspend fun recordOpen(context: PlatformContext, kind: UsageKind, id: Long)
    fun scores(context: PlatformContext, kind: UsageKind): Flow<Map<Long, Double>>
    fun score(openActivity: Double, updatedAt: Long, now: Long): Double
}
