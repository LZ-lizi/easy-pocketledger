package com.pocketledger.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class DateKeysTest {

    @Test
    fun `month range covers the whole month including short and leap months`() {
        assertEquals("2026-09-01" to "2026-09-30", DateKeys.monthRange("2026-09"))
        assertEquals("2026-02-01" to "2026-02-28", DateKeys.monthRange("2026-02"))
        assertEquals("2028-02-01" to "2028-02-29", DateKeys.monthRange("2028-02"))
        assertEquals("2026-12-01" to "2026-12-31", DateKeys.monthRange("2026-12"))
    }

    @Test
    fun `days remaining counts today`() {
        assertEquals(30, DateKeys.daysRemainingInMonth("2026-09", LocalDate.of(2026, 9, 1)))
        assertEquals(15, DateKeys.daysRemainingInMonth("2026-09", LocalDate.of(2026, 9, 16)))
        assertEquals(1, DateKeys.daysRemainingInMonth("2026-09", LocalDate.of(2026, 9, 30)))
    }

    @Test
    fun `days elapsed counts today and is the counterpart of days remaining`() {
        assertEquals(1, DateKeys.daysElapsedInMonth("2026-09", LocalDate.of(2026, 9, 1)))
        assertEquals(16, DateKeys.daysElapsedInMonth("2026-09", LocalDate.of(2026, 9, 16)))
        assertEquals(30, DateKeys.daysElapsedInMonth("2026-09", LocalDate.of(2026, 9, 30)))
        // The two overlap on today by design: 日均可用 divides by the days left *counting
        // today*, and the recorded pace divides by the days gone *counting today*. So the
        // sum is the month length plus one, not the month length.
        for (day in 1..30) {
            val on = LocalDate.of(2026, 9, day)
            assertEquals(
                31,
                DateKeys.daysElapsedInMonth("2026-09", on) +
                    DateKeys.daysRemainingInMonth("2026-09", on),
            )
        }
    }

    @Test
    fun `a finished month counts as fully elapsed`() {
        assertEquals(30, DateKeys.daysElapsedInMonth("2026-09", LocalDate.of(2026, 10, 1)))
        assertEquals(30, DateKeys.daysElapsedInMonth("2026-09", LocalDate.of(2027, 1, 1)))
    }

    @Test
    fun `a month that has not started has nothing elapsed`() {
        // Guards the 已记账日均 divides: zero elapsed must mean "no daily figure", not a
        // negative or a huge one.
        assertEquals(0, DateKeys.daysElapsedInMonth("2026-09", LocalDate.of(2026, 8, 31)))
        assertEquals(0, DateKeys.daysElapsedInMonth("2026-09", LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun `a finished month has no days remaining so the daily figure never divides by zero`() {
        assertEquals(0, DateKeys.daysRemainingInMonth("2026-09", LocalDate.of(2026, 10, 1)))
        assertEquals(0, DateKeys.daysRemainingInMonth("2026-09", LocalDate.of(2027, 1, 1)))
    }

    @Test
    fun `range orders its endpoints defensively`() {
        assertEquals("2026-09-01" to "2026-09-30", DateKeys.range("2026-09-30", "2026-09-01"))
        assertEquals("2026-09-01" to "2026-09-30", DateKeys.range("2026-09-01", "2026-09-30"))
    }

    @Test
    fun `derives a month key from a date key`() {
        assertEquals("2026-09", DateKeys.monthKeyOf("2026-09-16"))
        assertEquals("2026-09", DateKeys.monthKey(LocalDate.of(2026, 9, 16)))
    }

    @Test
    fun `labels months in chinese`() {
        assertEquals("2026年9月", DateKeys.monthLabel("2026-09"))
        assertEquals("2026年12月", DateKeys.monthLabel("2026-12"))
    }

    @Test
    fun `date keys sort lexicographically in chronological order`() {
        // The whole storage design relies on this: ranges are string comparisons.
        val keys = listOf("2026-09-09", "2026-09-10", "2026-10-01", "2026-12-31", "2027-01-01")
        assertEquals(keys, keys.sorted())
    }
}
