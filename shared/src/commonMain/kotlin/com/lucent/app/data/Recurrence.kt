package com.lucent.app.data

import kotlinx.datetime.*

enum class RepeatRule(val key: String, val label: String) {
    NONE("NONE", "Does not repeat"),
    DAILY("DAILY", "Daily"),
    WEEKLY("WEEKLY", "Weekly"),
    MONTHLY("MONTHLY", "Monthly"),
    YEARLY("YEARLY", "Yearly");

    companion object {
        fun fromKey(key: String?): RepeatRule {
            val k = key?.trim()?.lowercase() ?: return NONE
            entries.firstOrNull { it.key.equals(k, ignoreCase = true) }?.let { return it }
            return when (k) {
                "day", "daily", "everyday", "every day" -> DAILY
                "week", "every week" -> WEEKLY
                "month", "every month" -> MONTHLY
                "year", "annual", "annually", "every year" -> YEARLY
                else -> NONE
            }
        }
    }
}

object Recurrence {

    fun advance(fromMillis: Long, rule: RepeatRule): Long {
        if (rule == RepeatRule.NONE) return fromMillis
        val instant = Instant.fromEpochMilliseconds(fromMillis)
        val tz = TimeZone.currentSystemDefault()
        val nextInstant = when (rule) {
            RepeatRule.DAILY -> instant.plus(DateTimePeriod(days = 1), tz)
            RepeatRule.WEEKLY -> instant.plus(DateTimePeriod(days = 7), tz)
            RepeatRule.MONTHLY -> instant.plus(DateTimePeriod(months = 1), tz)
            RepeatRule.YEARLY -> instant.plus(DateTimePeriod(years = 1), tz)
            RepeatRule.NONE -> instant
        }
        return nextInstant.toEpochMilliseconds()
    }

    fun nextOccurrence(base: Long?, rule: RepeatRule, now: Long = System.currentTimeMillis()): Long? {
        if (rule == RepeatRule.NONE) return null
        var t = advance(base ?: now, rule)
        var guard = 0
        while (t <= now && guard < 4000) {
            t = advance(t, rule)
            guard++
        }
        return t
    }

    fun nextOccurrence(task: Task): Task? {
        val rule = RepeatRule.fromKey(task.repeatRule)
        if (rule == RepeatRule.NONE) return null
        val base = task.dueAt ?: return null
        val nextDue = nextOccurrence(base, rule) ?: return null
        return task.copy(
            id = 0,
            isDone = false,
            createdAt = System.currentTimeMillis(),
            attachments = "[]",
            dueAt = nextDue,
            completedAt = null,
            subtasks = Checklist.resetDone(task.subtasks),
            trashedAt = null
        )
    }
}
