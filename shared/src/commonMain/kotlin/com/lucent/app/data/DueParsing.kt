package com.lucent.app.data

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

object DueParsing {

    private const val DEFAULT_HOUR = 9

    fun parse(input: String?): Long? {
        val s = input?.trim().orEmpty()
        if (s.isEmpty()) return null

        try {
            return Instant.parse(s).toEpochMilliseconds()
        } catch (t: Throwable) {
        }

        try {
            return LocalDateTime.parse(s.replace(" ", "T"))
                .toInstant(TimeZone.currentSystemDefault())
                .toEpochMilliseconds()
        } catch (t: Throwable) {
        }

        return try {
            val date = LocalDate.parse(s)
            LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, DEFAULT_HOUR, 0)
                .toInstant(TimeZone.currentSystemDefault())
                .toEpochMilliseconds()
        } catch (t: Throwable) {
            null
        }
    }

    fun isClearRequest(input: String?): Boolean {
        val s = input?.trim()?.lowercase() ?: return false
        return s in setOf("", "none", "clear", "remove", "no", "unset", "null")
    }

    fun format(millis: Long): String {
        val dt = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
        return "${dt.year}-${dt.monthNumber.toString().padStart(2, '0')}-${dt.dayOfMonth.toString().padStart(2, '0')} ${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"
    }
}
