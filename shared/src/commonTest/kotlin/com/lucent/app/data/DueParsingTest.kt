package com.lucent.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DueParsingTest {

    private val zone = ZoneId.systemDefault()

    private fun expectMillis(pattern: String, input: String): Long {
        val dt = LocalDateTime.parse(input, DateTimeFormatter.ofPattern(pattern, Locale.US))
        return dt.atZone(zone).toInstant().toEpochMilli()
    }

    @Test
    fun parsesAbsoluteDateWithDefaultNineAm() {
        val expected = LocalDate.parse("2026-02-01")
            .atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected, DueParsing.parse("2026-02-01"))
    }

    @Test
    fun parsesDateAndTimeForms() {
        assertEquals(expectMillis("yyyy-MM-dd HH:mm", "2026-02-01 10:30"), DueParsing.parse("2026-02-01 10:30"))
        assertEquals(expectMillis("yyyy-MM-dd'T'HH:mm", "2026-02-01T10:30"), DueParsing.parse("2026-02-01T10:30"))
        assertEquals(expectMillis("yyyy-MM-dd HH:mm:ss", "2026-02-01 10:30:15"), DueParsing.parse("2026-02-01 10:30:15"))
    }

    @Test
    fun parsesZonedInstant() {
        val expected = Instant.parse("2026-02-01T10:30:00Z").toEpochMilli()
        assertEquals(expected, DueParsing.parse("2026-02-01T10:30:00Z"))
    }

    @Test
    fun trimsAndRejectsGarbage() {
        assertNull(DueParsing.parse(""))
        assertNull(DueParsing.parse("   "))
        assertNull(DueParsing.parse("next tuesday please"))
        assertNull(DueParsing.parse("2026-13-45"))
    }

    @Test
    fun formatRoundTripsParsedValue() {
        val parsed = DueParsing.parse("2026-03-15 14:45")!!
        val formatted = DueParsing.format(parsed)
        assertEquals(parsed, DueParsing.parse(formatted))
        assertTrue(formatted.startsWith("2026-03-15 14:45"), "unexpected format: $formatted")
    }

    @Test
    fun recognisesClearRequests() {
        for (s in listOf("none", "clear", "remove", "no", "unset", "null", "  none  ", "")) {
            assertTrue(DueParsing.isClearRequest(s), "'$s' should mean clear")
        }
        assertTrue(!DueParsing.isClearRequest("2026-02-01"))
        assertTrue(!DueParsing.isClearRequest("tomorrow"))
    }
}
