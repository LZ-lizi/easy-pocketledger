package com.pocketledger.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The allowance rule is the single most load-bearing calculation in the app: the
 * home card's big number comes straight from it. September 2026 has 30 days, so
 * from the 16th there are 15 days left including today.
 */
class AllowanceCalculatorTest {

    private val today = LocalDate.of(2026, 9, 16)
    private val month = "2026-09"

    private fun compute(
        budget: Long?,
        spent: Long,
        daily: Long = spent,
        leisure: Long = 0,
        on: LocalDate = today,
    ) = AllowanceCalculator.compute(month, budget, spent, daily, leisure, on)

    @Test
    fun `remaining is the allowance minus every kind of spending`() {
        val snapshot = compute(budget = 250000, spent = 125950, daily = 90000, leisure = 35950)
        assertEquals(124050L, snapshot.remainingCents)
        assertEquals(125950L, snapshot.spentCents)
        assertTrue(snapshot.hasBudget)
        assertFalse(snapshot.isOverBudget)
    }

    @Test
    fun `daily available divides by remaining days including today`() {
        val snapshot = compute(budget = 250000, spent = 100000)
        assertEquals(15, snapshot.daysRemaining)
        assertEquals(10000L, snapshot.dailyAvailableCents)   // 150000 / 15
    }

    @Test
    fun `integer division floors rather than rounding up`() {
        // 100000 / 15 = 6666.67 -> 6666, never 6667: the card must not overpromise.
        val snapshot = compute(budget = 200000, spent = 100000)
        assertEquals(6666L, snapshot.dailyAvailableCents)
    }

    @Test
    fun `overspending reports a negative remainder and no daily allowance`() {
        val snapshot = compute(budget = 100000, spent = 130000)
        assertEquals(-30000L, snapshot.remainingCents)
        assertTrue(snapshot.isOverBudget)
        assertEquals(0L, snapshot.dailyAvailableCents)
    }

    @Test
    fun `a month without a budget still tracks spending`() {
        val snapshot = compute(budget = null, spent = 5000)
        assertFalse(snapshot.hasBudget)
        assertEquals(0L, snapshot.dailyAvailableCents)
        assertEquals(-5000L, snapshot.remainingCents)
        assertFalse(snapshot.isOverBudget)   // nothing to be over yet
    }

    @Test
    fun `a zero budget is treated as unset, not as instantly overspent`() {
        val snapshot = compute(budget = 0, spent = 5000)
        assertFalse(snapshot.hasBudget)
        assertEquals(0L, snapshot.dailyAvailableCents)
    }

    @Test
    fun `the last day of the month has exactly one day left`() {
        val snapshot = compute(budget = 300000, spent = 0, on = LocalDate.of(2026, 9, 30))
        assertEquals(1, snapshot.daysRemaining)
        assertEquals(300000L, snapshot.dailyAvailableCents)
    }

    @Test
    fun `once the month is over there is no daily figure`() {
        val snapshot = compute(budget = 300000, spent = 0, on = LocalDate.of(2026, 10, 5))
        assertEquals(0, snapshot.daysRemaining)
        assertEquals(0L, snapshot.dailyAvailableCents)
    }

    @Test
    fun `leisure ratio reflects the discretionary share`() {
        val snapshot = compute(budget = 250000, spent = 100000, daily = 72000, leisure = 28000)
        assertEquals(0.28f, snapshot.leisureRatio, 0.001f)
    }

    @Test
    fun `leisure ratio is zero rather than NaN with no spending`() {
        val snapshot = compute(budget = 250000, spent = 0)
        assertEquals(0f, snapshot.leisureRatio, 0.001f)
    }

    @Test
    fun `progress clamps to one when over budget`() {
        val snapshot = compute(budget = 100000, spent = 250000)
        assertEquals(1.0f, snapshot.progress, 0.001f)
    }

    @Test
    fun `progress is zero without a budget`() {
        val snapshot = compute(budget = null, spent = 250000)
        assertEquals(0.0f, snapshot.progress, 0.001f)
    }

    @Test
    fun `projection extrapolates the current pace to month end`() {
        // 15 of 30 days left -> 16 days elapsed. 160000 spent over 16 days -> 300000.
        val projected = AllowanceCalculator.projectedMonthSpend(160000, month, today)
        assertEquals(300000L, projected)
    }
}
