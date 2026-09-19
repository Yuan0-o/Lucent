package com.lucent.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object DueParsing {

    private val LOCAL_PATTERNS = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd HH:mm"
    )

    private const val DEFAULT_HOUR = 9

    private val OUT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US)

    fun parse(input: String?): Long? {
        val s = input?.trim().orEmpty()
        if (s.isEmpty()) return null

        try {
            return Instant.parse(s).toEpochMilli()
        } catch (t: Throwable) {
        }

        for (pattern in LOCAL_PATTERNS) {
            try {
                val formatter = DateTimeFormatter.ofPattern(pattern, Locale.US)
                return LocalDateTime.parse(s, formatter)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            } catch (t: Throwable) {
            }
        }

        return try {
            LocalDate.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US))
                .atTime(DEFAULT_HOUR, 0)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        } catch (t: Throwable) {
            null
        }
    }

    fun isClearRequest(input: String?): Boolean {
        val s = input?.trim()?.lowercase() ?: return false
        return s in setOf("", "none", "clear", "remove", "no", "unset", "null")
    }

    fun format(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(OUT_FORMAT)
}
