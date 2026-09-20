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

    /**
     * The defaults describe the term you are in, not the day you opened the editor.
     *
     * A semester starts on 9月1日 or 3月1日, so the editor offers the boundary that has most
     * recently passed: in September, that is the autumn term already under way; in January,
     * still the previous September's, because that is the term in progress.
     */
    @Test
    fun `default start is the most recent term boundary`() {
        fun start(month: Int, day: Int) =
            TermSettingsViewModel.defaultStartDate(LocalDate.of(2027, month, day))

        assertEquals("2026-09-01", start(1, 5))     // 秋季学期 still in progress
        assertEquals("2026-09-01", start(2, 28))
        assertEquals("2027-03-01", start(3, 1))     // 春季学期 begins
        assertEquals("2027-03-01", start(6, 30))
        assertEquals("2027-03-01", start(8, 31))
        assertEquals("2027-09-01", start(9, 1))     // 秋季学期 begins
        assertEquals("2027-09-01", start(12, 31))
    }

    @Test
    fun `the suggested name matches the suggested start date`() {
        // The two must never describe different terms: the name is derived from the same
        // boundary, not from the month the user happens to be looking at.
        assertEquals("2027 春季学期", TermSettingsViewModel.suggestedTermName(LocalDate.of(2027, 5, 1)))
        assertEquals("2027 春季学期", TermSettingsViewModel.suggestedTermName(LocalDate.of(2027, 3, 1)))
        assertEquals("2026 秋季学期", TermSettingsViewModel.suggestedTermName(LocalDate.of(2027, 2, 28)))
        assertEquals("2027 秋季学期", TermSettingsViewModel.suggestedTermName(LocalDate.of(2027, 9, 1)))
    }

    @Test
    fun `default end date runs one semester from the start, both ends inclusive`() {
        // 9月1日 -> 12月31日 and 3月1日 -> 6月30日: the two dates a term actually ends on.
        assertEquals("2026-12-31", TermSettingsViewModel.defaultEndDate("2026-09-01"))
        assertEquals("2027-06-30", TermSettingsViewModel.defaultEndDate("2027-03-01"))
        val end = TermSettingsViewModel.defaultEndDate("2026-09-01")
        assertTrue(TermSettingsViewModel.isValidRange("2026-09-01", end))
        assertTrue(
            "expected a term of at least 100 days, got $end",
            TermSettingsViewModel.dayCount(term("2026-09-01", end)) >= 100L,
        )
    }
}
