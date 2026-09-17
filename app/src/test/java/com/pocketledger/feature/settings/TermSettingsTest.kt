package com.pocketledger.feature.settings

import com.pocketledger.data.entity.TermEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TermSettingsTest {

    private fun term(start: String, end: String) = TermEntity(
        id = 1,
        name = "test",
        startDateKey = start,
        endDateKey = end,
    )

    /** Inclusive counting: a term that starts and ends the same day lasts one day. */
    @Test
    fun `a single day term counts as one day`() {
        assertEquals(1L, TermSettingsViewModel.dayCount(term("2026-09-01", "2026-09-01")))
    }

    @Test
    fun `day count includes both endpoints`() {
        assertEquals(2L, TermSettingsViewModel.dayCount(term("2026-09-01", "2026-09-02")))
        // September has 30 days, so 1 Sep to 30 Sep is 30 days, not 29.
        assertEquals(30L, TermSettingsViewModel.dayCount(term("2026-09-01", "2026-09-30")))
    }

    @Test
    fun `an unparseable term reports zero days instead of throwing`() {
        assertEquals(0L, TermSettingsViewModel.dayCount(term("nonsense", "2026-09-30")))
    }

    @Test
    fun `a valid range requires an ordered pair of parseable dates`() {
        assertTrue(TermSettingsViewModel.isValidRange("2026-09-01", "2027-01-15"))
        assertTrue(TermSettingsViewModel.isValidRange("2026-09-01", "2026-09-01"))
        assertFalse(TermSettingsViewModel.isValidRange("2027-01-15", "2026-09-01"))
        assertFalse(TermSettingsViewModel.isValidRange("oops", "2026-09-01"))
        assertFalse(TermSettingsViewModel.isValidRange("2026-09-01", ""))
    }

    @Test
    fun `term name suggestion follows the academic half of the year`() {
        assertEquals("2026 秋季学期", TermSettingsViewModel.suggestedTermName(LocalDate.of(2026, 9, 16)))
        assertEquals("2026 秋季学期", TermSettingsViewModel.suggestedTermName(LocalDate.of(2026, 7, 1)))
        assertEquals("2026 春季学期", TermSettingsViewModel.suggestedTermName(LocalDate.of(2026, 6, 30)))
        assertEquals("2026 春季学期", TermSettingsViewModel.suggestedTermName(LocalDate.of(2026, 1, 5)))
    }

    @Test
    fun `default end date is a valid range roughly one semester long`() {
        val today = LocalDate.of(2026, 9, 1)
        val end = TermSettingsViewModel.defaultEndDate(today)
        assertTrue(TermSettingsViewModel.isValidRange(today.toString(), end))
        assertTrue("expected a term of at least 100 days, got $end", TermSettingsViewModel.dayCount(
            term(today.toString(), end)
        ) >= 100L)
    }
}
