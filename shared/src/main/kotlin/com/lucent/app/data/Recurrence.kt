package com.lucent.app.data

import java.util.Calendar

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
        val cal = Calendar.getInstance().apply { timeInMillis = fromMillis }
        when (rule) {
            RepeatRule.DAILY -> cal.add(Calendar.DAY_OF_MONTH, 1)
            RepeatRule.WEEKLY -> cal.add(Calendar.DAY_OF_MONTH, 7)
            RepeatRule.MONTHLY -> cal.add(Calendar.MONTH, 1)
            RepeatRule.YEARLY -> cal.add(Calendar.YEAR, 1)
            RepeatRule.NONE -> Unit
        }
        return cal.timeInMillis
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
