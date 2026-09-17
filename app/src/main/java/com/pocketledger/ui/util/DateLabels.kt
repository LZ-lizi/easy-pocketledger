package com.pocketledger.ui.util

import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Chinese-language date labels. Kept in the UI layer; nothing here affects storage. */
object DateLabels {

    private val WEEKDAYS = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    /** `2026-09-16` -> `9月16日 周三`. */
    fun dayLabel(dateKey: String): String {
        val date = parseOrNull(dateKey) ?: return dateKey
        return "${date.monthValue}月${date.dayOfMonth}日 ${weekday(date)}"
    }

    /** Adds `今天` / `昨天` / `明天` in front of a nearby date. */
    fun dayLabelWithRelative(dateKey: String, today: LocalDate = LocalDate.now()): String {
        val date = parseOrNull(dateKey) ?: return dateKey
        val base = "${date.monthValue}月${date.dayOfMonth}日 ${weekday(date)}"
        val delta = date.toEpochDay() - today.toEpochDay()
        val prefix = when (delta) {
            0L -> "今天 · "
            -1L -> "昨天 · "
            1L -> "明天 · "
            else -> ""
        }
        return prefix + base
    }

    fun weekday(date: LocalDate): String = WEEKDAYS[date.dayOfWeek.value - 1]

    fun isToday(dateKey: String, today: LocalDate = LocalDate.now()): Boolean = dateKey == today.toString()

    private fun parseOrNull(dateKey: String): LocalDate? = try {
        LocalDate.parse(dateKey)
    } catch (_: DateTimeParseException) {
        null
    }
}
