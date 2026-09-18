package com.pocketledger.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Threshold logic for overspending alerts.
 *
 * Getting this wrong means either silence when a budget is blown, or a notification
 * every time the app opens -- both of which train the user to ignore it.
 */
class BudgetAlertsTest {

    private fun progress(
        categoryId: Long = 1L,
        name: String = "餐饮",
        limit: Long = 100000L,
        spent: Long,
    ) = BudgetProgress(categoryId, name, limit, spent)

    @Test
    fun `levels sit at the two thresholds`() {
        assertEquals(0, BudgetAlerts.levelFor(0, 100000))
        assertEquals(0, BudgetAlerts.levelFor(79999, 100000))
        assertEquals(80, BudgetAlerts.levelFor(80000, 100000))
        assertEquals(80, BudgetAlerts.levelFor(100000, 100000))
        assertEquals(100, BudgetAlerts.levelFor(100001, 100000))
    }

    /** Spending exactly the cap is "reached", not "over"; the ratio agrees. */
    @Test
    fun `exactly at the cap is not over`() {
        val item = progress(spent = 100000)
        assertEquals(BudgetAlerts.WARNING_LEVEL, item.level)
        assertFalse(item.isOver)
        assertEquals(0L, item.remainingCents)
    }

    @Test
    fun `a budget with no limit never alerts`() {
        val item = progress(limit = 0, spent = 50000)
        assertEquals(0, item.level)
        assertEquals(0f, item.ratio, 0.001f)
    }

    @Test
    fun `no alerts below the warning threshold`() {
        val pending = BudgetAlerts.pending("2026-09", listOf(progress(spent = 50000)), emptySet())
        assertTrue(pending.isEmpty())
    }

    @Test
    fun `crossing the warning reports once`() {
        val pending = BudgetAlerts.pending("2026-09", listOf(progress(spent = 85000)), emptySet())
        assertEquals(listOf(80), pending.map { it.level })
        assertEquals("2026-09:1:80", pending.first().key)
    }

    /** The property that keeps the app from nagging: a fired crossing stays fired. */
    @Test
    fun `an already reported crossing is not reported again`() {
        val item = progress(spent = 85000)
        val first = BudgetAlerts.pending("2026-09", listOf(item), emptySet())
        assertEquals(1, first.size)
        val second = BudgetAlerts.pending("2026-09", listOf(item), first.map { it.key }.toSet())
        assertTrue(second.isEmpty())
    }

    @Test
    fun `going over reports both thresholds if neither fired yet`() {
        val pending = BudgetAlerts.pending("2026-09", listOf(progress(spent = 120000)), emptySet())
        assertEquals(listOf(80, 100), pending.map { it.level })
    }

    @Test
    fun `only the unreported threshold fires when the warning already went out`() {
        val item = progress(spent = 120000)
        val fired = setOf(item.alertKey("2026-09", 80))
        val pending = BudgetAlerts.pending("2026-09", listOf(item), fired)
        assertEquals(listOf(100), pending.map { it.level })
    }

    /** A new month must alert again; the period is part of the key. */
    @Test
    fun `a new period resets the alerts`() {
        val item = progress(spent = 85000)
        val fired = setOf(item.alertKey("2026-09", 80))
        val pending = BudgetAlerts.pending("2026-10", listOf(item), fired)
        assertEquals(1, pending.size)
        assertEquals("2026-10:1:80", pending.first().key)
    }

    @Test
    fun `several budgets each report their own crossing`() {
        val pending = BudgetAlerts.pending(
            periodKey = "2026-09",
            progress = listOf(
                progress(categoryId = 1L, name = "餐饮", spent = 90000),
                progress(categoryId = 2L, name = "交通", spent = 5000),
                progress(categoryId = 0L, name = "总预算", limit = 500000, spent = 520000),
            ),
            alreadyFired = emptySet(),
        )
        assertEquals(
            listOf("2026-09:1:80", "2026-09:0:80", "2026-09:0:100"),
            pending.map { it.key },
        )
    }

    @Test
    fun `ratio clamps at one so a progress bar cannot overflow`() {
        assertEquals(1f, progress(spent = 300000).ratio, 0.001f)
    }
}
