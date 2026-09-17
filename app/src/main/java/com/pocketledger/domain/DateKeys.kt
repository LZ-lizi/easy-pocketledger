package com.pocketledger.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * Date keys.
 *
 * Every transaction stores both an epoch-millis instant and a `localDateKey`
 * (`YYYY-MM-DD`). Grouping, month totals and range filters then compare plain
 * strings, which is index-friendly and immune to the day shifting under a
 * timezone change. [monthKey] follows the same idea with a `YYYY-MM` shape.
 *
 * `java.time` is used directly rather than a multiplatform date library: minSdk is
 * 31, so the full API is present and no dependency is needed.
 */
object DateKeys {

    fun dateKey(date: LocalDate): String = date.toString()

    fun monthKey(date: LocalDate): String = YearMonth.from(date).toString()

    fun parseDateKey(key: String): LocalDate = LocalDate.parse(key)

    fun parseMonthKey(key: String): YearMonth = YearMonth.parse(key)

    fun monthKeyOf(dateKey: String): String = dateKey.take(7)

    fun monthStart(monthKey: String): LocalDate = YearMonth.parse(monthKey).atDay(1)

    fun monthEnd(monthKey: String): LocalDate = YearMonth.parse(monthKey).atEndOfMonth()

    /** Inclusive `first..last` date keys covering the whole month. */
    fun monthRange(monthKey: String): Pair<String, String> {
        val month = YearMonth.parse(monthKey)
        return month.atDay(1).toString() to month.atEndOfMonth().toString()
    }

    /** Inclusive `first..last` date keys covering the whole year. */
    fun yearRange(year: Int): Pair<String, String> =
        LocalDate.of(year, 1, 1).toString() to LocalDate.of(year, 12, 31).toString()

    /** Inclusive range between two date keys, ordered defensively. */
    fun range(from: String, to: String): Pair<String, String> =
        if (from <= to) from to to else to to from

    /**
     * Days left in the month **including today**, which is the denominator behind
     * 「日均可用」. Returns 0 once the month is over so callers never divide by zero.
     */
    fun daysRemainingInMonth(monthKey: String, today: LocalDate): Int {
        val end = monthEnd(monthKey)
        if (today > end) return 0
        return (end.toEpochDay() - today.toEpochDay()).toInt() + 1
    }

    fun daysInMonth(monthKey: String): Int = YearMonth.parse(monthKey).lengthOfMonth()

    /** `2026-09` -> `2026年9月`, used by the month switcher. */
    fun monthLabel(monthKey: String): String {
        val month = YearMonth.parse(monthKey)
        return "${month.year}年${month.monthValue}月"
    }

    /**
     * Moves a timestamp onto a different calendar day while keeping its wall-clock
     * time.
     *
     * Editing only the date must not reshuffle the ledger: if two entries on the
     * same day kept their relative order before the edit, they keep it after.
     * A midnight timestamp is treated as "no meaningful time" and given the current
     * time so the edited row does not jump to the top or bottom of its day.
     */
    fun withTimeOfDay(dateKey: String, originalMillis: Long): Long {
        val date = runCatching { LocalDate.parse(dateKey) }.getOrElse { LocalDate.now() }
        val originalTime = Instant.ofEpochMilli(originalMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
        val time = if (originalTime == LocalTime.MIDNIGHT) LocalTime.now() else originalTime
        return date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}
