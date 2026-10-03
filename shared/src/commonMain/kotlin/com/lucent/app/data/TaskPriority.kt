package com.lucent.app.data

enum class TaskPriority(val value: Int, val key: String, val label: String) {
    NONE(0, "none", "None"),
    LOW(1, "low", "Low"),
    MEDIUM(2, "medium", "Medium"),
    HIGH(3, "high", "High");

    companion object {
        fun fromValue(value: Int): TaskPriority = entries.firstOrNull { it.value == value } ?: NONE

        fun fromKey(key: String?): TaskPriority {
            val k = key?.trim()?.lowercase() ?: return NONE
            entries.firstOrNull { it.key == k }?.let { return it }
            return when (k) {
                "urgent", "highest", "critical", "hi", "h", "3" -> HIGH
                "med", "normal", "m", "2" -> MEDIUM
                "lo", "l", "1" -> LOW
                else -> NONE
            }
        }
    }
}
