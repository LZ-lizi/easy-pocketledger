package com.pocketledger.domain

import java.time.LocalDate
import java.time.YearMonth

/** One instalment that has fallen due and still needs a transaction. */
data class PendingPeriod(
    /** 1-based. */
    val periodIndex: Int,
    val dueDateKey: String,
    val amountCents: Long,
)

/**
 * The due-date engine behind 月付 / 白条.
 *
 * Pure and separate from the database on purpose: "did this charge already happen"
 * is the one question that must never be answered wrongly, so the rule is testable
 * without a device or a database.
 *
 * The schedule anchors on [repayDay] rather than on the start date. Paying on the
 * 10th means the 10th, and a plan started on the 25th simply begins next month --
 * clamping to the start date would silently shift every later payment.
 */
object InstallmentSchedule {

    /** Normalises a stored day-of-month to something a calendar can actually use. */
    fun clampDay(day: Int): Int = day.coerceIn(1, 31)

    /**
     * First payment date: the first occurrence of [repayDay] on or after [startDateKey].
     *
     * A month too short for the requested day uses its last day, so repayDay 31 in
     * February falls on the 28th (or 29th) rather than being skipped.
     */
    fun firstDueDate(startDateKey: String, repayDay: Int): LocalDate {
        val start = runCatching { LocalDate.parse(startDateKey) }.getOrElse { LocalDate.now() }
        val day = clampDay(repayDay)
        val thisMonth = start.withDayOfMonth(minOf(day, start.lengthOfMonth()))
        if (!thisMonth.isBefore(start)) return thisMonth
        val next = start.plusMonths(1)
        return next.withDayOfMonth(minOf(day, next.lengthOfMonth()))
    }

    /** Due date of one instalment, 1-based. */
    fun dueDate(startDateKey: String, repayDay: Int, periodIndex: Int): LocalDate =
        firstDueDate(startDateKey, repayDay).plusMonths((periodIndex - 1).toLong())

    /**
     * Instalments due on or before [today] that have no transaction yet.
     *
     * [alreadyGenerated] is the set of period indices already backed by a
     * transaction; passing it in (rather than querying here) keeps this function
     * pure and lets the caller use whatever index it already has.
     *
     * Amounts come from [amountForPeriod], so the final instalment absorbs the
     * rounding remainder and the periods always add up to [totalCents].
     */
    fun pendingPeriods(
        startDateKey: String,
        repayDay: Int,
        periodCount: Int,
        totalCents: Long,
        today: LocalDate,
        alreadyGenerated: Set<Int>,
    ): List<PendingPeriod> =
        (1..periodCount)
            .filter { it !in alreadyGenerated }
            .mapNotNull { index ->
                val due = dueDate(startDateKey, repayDay, index)
                if (due.isAfter(today)) {
                    null
                } else {
                    PendingPeriod(index, due.toString(), amountForPeriod(totalCents, periodCount, index))
                }
            }

    /** What one instalment costs, with the remainder landing on the last one. */
    fun amountForPeriod(totalCents: Long, periodCount: Int, periodIndex: Int): Long =
        splitEvenly(totalCents, periodCount).getOrNull(periodIndex - 1) ?: 0L

    /** How many instalments have come due by [today], regardless of generation. */
    fun elapsedPeriods(startDateKey: String, repayDay: Int, periodCount: Int, today: LocalDate): Int =
        (1..periodCount).count { !dueDate(startDateKey, repayDay, it).isAfter(today) }

    /** The date the last instalment falls on; null when the plan has no periods. */
    fun finalDueDate(startDateKey: String, repayDay: Int, periodCount: Int): LocalDate? =
        if (periodCount <= 0) null else dueDate(startDateKey, repayDay, periodCount)

    /**
     * Splits a total into equal instalments with the remainder on the **last** one.
     *
     * Rounding each period independently would leave the parts not adding up to the
     * whole, which is the kind of discrepancy that makes someone stop trusting the
     * app. A 1000.00 total over 3 gives 333.33, 333.33, 333.34.
     */
    fun splitEvenly(totalCents: Long, periodCount: Int): List<Long> {
        if (periodCount <= 0) return emptyList()
        val base = totalCents / periodCount
        val remainder = totalCents - base * periodCount
        return List(periodCount) { index ->
            if (index == periodCount - 1) base + remainder else base
        }
    }

    /** `YYYY-MM` of a due date, for grouping. */
    fun monthKeyOf(date: LocalDate): String = YearMonth.from(date).toString()
}
