package com.pocketledger.domain

import java.time.LocalDate

/**
 * Everything the home card shows about this month's living allowance.
 *
 * The figure the whole app is built around is [remainingCents]: the user opens the
 * app to answer one question, "can I afford this?".
 */
data class AllowanceSnapshot(
    val periodKey: String,
    /** The configured allowance, or 0 when the month has none yet. */
    val budgetCents: Long,
    val spentCents: Long,
    val remainingCents: Long,
    val daysRemaining: Int,
    val dailyAvailableCents: Long,
    val hasBudget: Boolean,
) {
    val isOverBudget: Boolean get() = hasBudget && remainingCents < 0

    /** 0f..1f, clamped; 0 when there is nothing to measure against. */
    val progress: Float
        get() = if (!hasBudget || budgetCents <= 0L) {
            0f
        } else {
            (spentCents.toDouble() / budgetCents.toDouble()).coerceIn(0.0, 1.0).toFloat()
        }
}

/**
 * The allowance rule, in one place.
 *
 * ```
 * 剩余 = 额度 − 全部支出
 * 日均 = 剩余 ÷ 剩余天数（含今天）
 * ```
 *
 * Counting *all* spending rather than only essentials was a deliberate choice: the
 * alternative lets the card show plenty left while the month is actually blown.
 */
object AllowanceCalculator {

    fun compute(
        periodKey: String,
        budgetCents: Long?,
        spentCents: Long,
        today: LocalDate,
    ): AllowanceSnapshot {
        val hasBudget = budgetCents != null && budgetCents > 0L
        val budget = budgetCents ?: 0L
        val remaining = budget - spentCents
        val daysRemaining = DateKeys.daysRemainingInMonth(periodKey, today)
        val dailyAvailable = when {
            !hasBudget -> 0L
            remaining <= 0L -> 0L
            daysRemaining <= 0 -> 0L
            else -> remaining / daysRemaining
        }
        return AllowanceSnapshot(
            periodKey = periodKey,
            budgetCents = budget,
            spentCents = spentCents,
            remainingCents = remaining,
            daysRemaining = daysRemaining,
            dailyAvailableCents = dailyAvailable,
            hasBudget = hasBudget,
        )
    }

    /**
     * Projects the month-end total from the current pace, assuming today is
     * partially through. Used for the "at this rate you'll overspend" hint.
     */
    fun projectedMonthSpend(spentCents: Long, periodKey: String, today: LocalDate): Long {
        val total = DateKeys.daysInMonth(periodKey)
        val elapsed = total - DateKeys.daysRemainingInMonth(periodKey, today) + 1
        if (elapsed <= 0) return spentCents
        return spentCents * total / elapsed
    }
}
