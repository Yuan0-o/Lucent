package com.lucent.app.data

import java.time.Instant
import java.time.ZoneId

object TaskInsights {

    data class Summary(
        val active: Int,
        val overdue: Int,
        val dueToday: Int,
        val dueThisWeek: Int,
        val completed: Int,
        val withReminders: Int
    ) {
        val needsAttention: Int get() = overdue + dueToday

        val isEmpty: Boolean get() = active == 0 && completed == 0
    }

    fun summarize(
        tasks: List<Task>,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Summary {
        val live = tasks.filter { it.trashedAt == null }
        val today = now.atZone(zone).toLocalDate()
        val nowMs = now.toEpochMilli()

        var active = 0
        var overdue = 0
        var dueToday = 0
        var dueThisWeek = 0
        var completed = 0
        var withReminders = 0

        for (task in live) {
            if (task.isDone) {
                completed++
                continue
            }
            active++
            if (task.reminderEnabled && task.dueAt != null) withReminders++
            val due = task.dueAt ?: continue
            when {
                due < nowMs -> overdue++
                else -> {
                    val dueDate = Instant.ofEpochMilli(due).atZone(zone).toLocalDate()
                    if (dueDate == today) dueToday++
                    if (!dueDate.isBefore(today) && dueDate.isBefore(today.plusDays(7))) dueThisWeek++
                }
            }
        }

        return Summary(
            active = active,
            overdue = overdue,
            dueToday = dueToday,
            dueThisWeek = dueThisWeek,
            completed = completed,
            withReminders = withReminders
        )
    }

    fun headline(summary: Summary): String? {
        if (summary.active == 0) return null
        val parts = buildList {
            if (summary.overdue > 0) add("${summary.overdue} overdue")
            if (summary.dueToday > 0) add("${summary.dueToday} due today")
            if (summary.overdue == 0 && summary.dueToday == 0 && summary.dueThisWeek > 0) {
                add("${summary.dueThisWeek} due this week")
            }
        }
        return if (parts.isEmpty()) "All clear" else parts.joinToString(" · ")
    }
}
