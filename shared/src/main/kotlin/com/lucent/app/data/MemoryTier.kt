package com.lucent.app.data

enum class MemoryTier(val key: String) {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high");

    companion object {
        val DEFAULT = MEDIUM

        const val HIGH_CROSS_MESSAGE_BUDGET = 40

        fun fromKey(key: String?): MemoryTier =
            entries.firstOrNull { it.key.equals(key?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}
