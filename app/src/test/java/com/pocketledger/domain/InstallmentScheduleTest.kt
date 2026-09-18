package com.pocketledger.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The due-date rule is the one thing about instalments that must never be wrong:
 * generating a charge twice takes real money, and missing one silently loses a
 * payment. These run without a device or a database on purpose.
 */
class InstallmentScheduleTest {

    @Test
    fun `first due date is the next occurrence of the repay day`() {
        // Started on the 5th, paying on the 10th -> the 10th of the same month.
        assertEquals(
            LocalDate.of(2026, 9, 10),
            InstallmentSchedule.firstDueDate("2026-09-05", 10),
        )
    }

    @Test
    fun `a start date past this month's repay day rolls to next month`() {
        // Started on the 25th, paying on the 10th -> this month's 10th has passed.
        assertEquals(
            LocalDate.of(2026, 10, 10),
            InstallmentSchedule.firstDueDate("2026-09-25", 10),
        )
    }

    @Test
    fun `starting exactly on the repay day pays that day`() {
        assertEquals(
            LocalDate.of(2026, 9, 10),
            InstallmentSchedule.firstDueDate("2026-09-10", 10),
        )
    }

    @Test
    fun `a short month clamps to its last day instead of skipping`() {
        // Day 31 in February must not vanish; it lands on the 28th (2026 is not a leap year).
        assertEquals(
            LocalDate.of(2027, 2, 28),
            InstallmentSchedule.dueDate("2026-11-30", 31, 4),
        )
        assertEquals(
            LocalDate.of(2028, 2, 29),
            InstallmentSchedule.dueDate("2027-11-30", 31, 4),
        )
    }

    @Test
    fun `periods advance one calendar month at a time`() {
        val start = "2026-09-05"
        assertEquals(LocalDate.of(2026, 9, 10), InstallmentSchedule.dueDate(start, 10, 1))
        assertEquals(LocalDate.of(2026, 10, 10), InstallmentSchedule.dueDate(start, 10, 2))
        assertEquals(LocalDate.of(2026, 11, 10), InstallmentSchedule.dueDate(start, 10, 3))
        assertEquals(LocalDate.of(2027, 1, 10), InstallmentSchedule.dueDate(start, 10, 5))
    }

    @Test
    fun `nothing is pending before the first due date`() {
        val pending = InstallmentSchedule.pendingPeriods(
            startDateKey = "2026-09-05",
            repayDay = 10,
            periodCount = 6,
            totalCents = 10000,
            today = LocalDate.of(2026, 9, 9),
            alreadyGenerated = emptySet(),
        )
        assertTrue(pending.isEmpty())
    }

    @Test
    fun `everything due so far is pending, and nothing beyond`() {
        val pending = InstallmentSchedule.pendingPeriods(
            startDateKey = "2026-09-05",
            repayDay = 10,
            periodCount = 6,
            totalCents = 10000,
            today = LocalDate.of(2026, 11, 15),
            alreadyGenerated = emptySet(),
        )
        assertEquals(listOf(1, 2, 3), pending.map { it.periodIndex })
        assertEquals(listOf("2026-09-10", "2026-10-10", "2026-11-10"), pending.map { it.dueDateKey })
    }

    /** The idempotency property: an already-generated period is never pending again. */
    @Test
    fun `already generated periods are excluded`() {
        val pending = InstallmentSchedule.pendingPeriods(
            startDateKey = "2026-09-05",
            repayDay = 10,
            periodCount = 6,
            totalCents = 10000,
            today = LocalDate.of(2026, 11, 15),
            alreadyGenerated = setOf(1, 2),
        )
        assertEquals(listOf(3), pending.map { it.periodIndex })
    }

    @Test
    fun `re-running after everything is generated yields nothing`() {
        val first = InstallmentSchedule.pendingPeriods(
            startDateKey = "2026-09-05",
            repayDay = 10,
            periodCount = 6,
            totalCents = 10000,
            today = LocalDate.of(2027, 6, 1),
            alreadyGenerated = emptySet(),
        )
        assertEquals(6, first.size)

        // Simulate the runner having written all six, then run again.
        val second = InstallmentSchedule.pendingPeriods(
            startDateKey = "2026-09-05",
            repayDay = 10,
            periodCount = 6,
            totalCents = 10000,
            today = LocalDate.of(2027, 6, 1),
            alreadyGenerated = first.map { it.periodIndex }.toSet(),
        )
        assertTrue(second.isEmpty())
    }

    @Test
    fun `a plan never generates more periods than it has`() {
        val pending = InstallmentSchedule.pendingPeriods(
            startDateKey = "2020-01-01",
            repayDay = 1,
            periodCount = 3,
            totalCents = 10000,
            today = LocalDate.of(2030, 1, 1),
            alreadyGenerated = emptySet(),
        )
        assertEquals(3, pending.size)
        assertEquals(listOf(1, 2, 3), pending.map { it.periodIndex })
    }

    @Test
    fun `final due date is the last period's date`() {
        assertEquals(
            LocalDate.of(2027, 2, 10),
            InstallmentSchedule.finalDueDate("2026-09-05", 10, 6),
        )
        assertNull(InstallmentSchedule.finalDueDate("2026-09-05", 10, 0))
    }

    /**
     * Split parts must add back up to the total. Rounding each period independently
     * would lose or invent a fen, which is the discrepancy that makes someone stop
     * trusting the numbers.
     */
    @Test
    fun `even split puts the remainder on the last instalment`() {
        assertEquals(listOf(33333L, 33333L, 33334L), InstallmentSchedule.splitEvenly(100000, 3))
        assertEquals(listOf(33333L, 33333L, 33333L), InstallmentSchedule.splitEvenly(99999, 3))
        assertEquals(listOf(100000L), InstallmentSchedule.splitEvenly(100000, 1))
        assertTrue(InstallmentSchedule.splitEvenly(100000, 0).isEmpty())
    }

    @Test
    fun `split parts always sum to the total`() {
        for (total in listOf(1L, 999L, 100000L, 123457L)) {
            for (count in 1..12) {
                val parts = InstallmentSchedule.splitEvenly(total, count)
                assertEquals("total=$total count=$count", total, parts.sum())
                assertEquals(count, parts.size)
            }
        }
    }

    @Test
    fun `an unparseable start date falls back to today rather than throwing`() {
        val due = InstallmentSchedule.firstDueDate("nonsense", 10)
        assertTrue(due >= LocalDate.now().withDayOfMonth(1))
    }
}
