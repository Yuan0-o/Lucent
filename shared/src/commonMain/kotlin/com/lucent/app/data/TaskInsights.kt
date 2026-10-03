package com.lucent.app.data

import java.time.Instant
import java.time.ZoneId

object TaskInsights {

    data class DayValue(val label: String, val value: Int)
    data class DayPair(val label: String, val first: Int, val second: Int)
    data class StatusDistribution(val done: Int, val active: Int, val overdue: Int) {
        val total: Int get() = done + active + overdue
    }

    fun completedPerDay(tasks: List<Task>, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): List<DayValue> {
        val today = now.atZone(zone).toLocalDate()
        return (13 downTo 0).map { offset ->
            val day = today.minusDays(offset.toLong())
            DayValue(day.dayOfMonth.toString(), tasks.count {
                it.trashedAt == null && it.completedAt?.let { time -> Instant.ofEpochMilli(time).atZone(zone).toLocalDate() == day } == true
            })
        }
    }

    fun createdAndCompletedPerDay(tasks: List<Task>, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): List<DayPair> {
        val today = now.atZone(zone).toLocalDate()
        return (6 downTo 0).map { offset ->
            val day = today.minusDays(offset.toLong())
            val label = day.dayOfMonth.toString()
            DayPair(label,
                tasks.count { it.trashedAt == null && Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() == day },
                tasks.count { it.trashedAt == null && it.completedAt?.let { time -> Instant.ofEpochMilli(time).atZone(zone).toLocalDate() == day } == true })
        }
    }

    fun statusDistribution(tasks: List<Task>, now: Instant = Instant.now()): StatusDistribution {
        val live = tasks.filter { it.trashedAt == null }
        val overdue = live.count { !it.isDone && it.dueAt?.let { due -> due < now.toEpochMilli() } == true }
        return StatusDistribution(live.count { it.isDone }, live.count { !it.isDone } - overdue, overdue)
    }

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
