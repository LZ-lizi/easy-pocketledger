package com.pocketledger.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The edit form takes input from the system keyboard, so this sanitiser is what
 * stands between a stray keystroke and a corrupted amount.
 */
class AmountSanitizerTest {

    @Test
    fun `keeps a well-formed amount untouched`() {
        assertEquals("12.34", KeypadInput.sanitizeAmount("12.34"))
        assertEquals("12", KeypadInput.sanitizeAmount("12"))
        assertEquals("0.5", KeypadInput.sanitizeAmount("0.5"))
    }

    @Test
    fun `strips characters that are not digits or a dot`() {
        assertEquals("12.34", KeypadInput.sanitizeAmount("12a.3b4"))
        assertEquals("1234", KeypadInput.sanitizeAmount("¥1,234"))
        assertEquals("12.34", KeypadInput.sanitizeAmount(" 12.34 "))
    }

    @Test
    fun `keeps only the first decimal point`() {
        assertEquals("1.23", KeypadInput.sanitizeAmount("1.2.3"))
        assertEquals("1.23", KeypadInput.sanitizeAmount("1..23"))
    }

    @Test
    fun `truncates beyond two decimals`() {
        assertEquals("12.34", KeypadInput.sanitizeAmount("12.3456"))
    }

    @Test
    fun `caps the integer part`() {
        assertEquals("12345678", KeypadInput.sanitizeAmount("123456789012"))
    }

    @Test
    fun `starts a decimal with an empty whole part`() {
        // ".5" is what a user gets by typing a dot first; Money parses it as 0.50.
        assertEquals(".5", KeypadInput.sanitizeAmount(".5"))
        assertEquals(50L, Money.parseYuanToCents(KeypadInput.sanitizeAmount(".5")))
    }

    @Test
    fun `whatever it returns is parseable or empty`() {
        val samples = listOf("", ".", "..", "abc", "1.2.3.4", "999999999999.999")
        for (sample in samples) {
            val clean = KeypadInput.sanitizeAmount(sample)
            // Empty and "." are the two accepted non-amounts; everything else must parse.
            if (clean.isNotEmpty() && clean != ".") {
                assertNotNull(
                    "sanitizeAmount(\"$sample\") gave \"$clean\" which does not parse",
                    Money.parseYuanToCents(clean),
                )
            }
        }
    }
}

/**
 * Changing a transaction's date must not reorder its day: keeping the wall-clock
 * time is what stops the ledger from shuffling after an edit.
 */
class WithTimeOfDayTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    private fun millisAt(date: LocalDate, hour: Int, minute: Int): Long =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun localTimeOf(millis: Long): LocalTime =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()

    @Test
    fun `keeps the time of day when moving to another date`() {
        val original = millisAt(LocalDate.of(2026, 9, 10), 14, 32)
        val moved = DateKeys.withTimeOfDay("2026-09-20", original)

        assertEquals(LocalDate.of(2026, 9, 20), Instant.ofEpochMilli(moved).atZone(zone).toLocalDate())
        assertEquals(LocalTime.of(14, 32), localTimeOf(moved).withSecond(0).withNano(0))
    }

    @Test
    fun `moving to the same date is a no-op for ordering`() {
        val original = millisAt(LocalDate.of(2026, 9, 10), 9, 15)
        val same = DateKeys.withTimeOfDay("2026-09-10", original)
        assertEquals(original, same)
    }

    @Test
    fun `a midnight timestamp gets a real time so the row does not jump`() {
        val midnight = millisAt(LocalDate.of(2026, 9, 10), 0, 0)
        val moved = DateKeys.withTimeOfDay("2026-09-11", midnight)
        val time = localTimeOf(moved).withSecond(0).withNano(0)
        // It must land somewhere in the day, not back at 00:00.
        assertTrue("expected a non-midnight time, got $time", time != LocalTime.MIDNIGHT)
        assertEquals(LocalDate.of(2026, 9, 11), Instant.ofEpochMilli(moved).atZone(zone).toLocalDate())
    }

    @Test
    fun `an unparseable date falls back to today rather than throwing`() {
        val original = millisAt(LocalDate.of(2026, 9, 10), 8, 0)
        val moved = DateKeys.withTimeOfDay("not-a-date", original)
        assertEquals(LocalDate.now(), Instant.ofEpochMilli(moved).atZone(zone).toLocalDate())
    }
}
