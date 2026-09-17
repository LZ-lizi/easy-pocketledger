package com.pocketledger.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pocketledger.data.dao.DayTotal
import com.pocketledger.ui.theme.LedgerTheme
import java.time.LocalDate

private val WEEKDAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

/**
 * Month grid where each day carries a spend-intensity bar.
 *
 * The bar is relative to the busiest day *of that month*, so the shape of the
 * month is readable at a glance without any axis or legend -- the whole point of a
 * calendar view next to the list. Weeks start on Monday, matching Chinese habit.
 */
@Composable
fun MonthCalendar(
    monthKey: String,
    dayTotals: Map<String, DayTotal>,
    selectedDateKey: String?,
    onSelectDay: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = remember { LocalDate.now() }
    val firstOfMonth = remember(monthKey) { LocalDate.parse("$monthKey-01") }
    val daysInMonth = remember(monthKey) { firstOfMonth.lengthOfMonth() }
    val leadingBlanks = firstOfMonth.dayOfWeek.value - 1        // Monday == 1
    val peakExpense = remember(dayTotals) {
        dayTotals.values.maxOfOrNull { it.expenseCents }?.coerceAtLeast(1L) ?: 1L
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            WEEKDAY_LABELS.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }

        val cells = leadingBlanks + daysInMonth
        val weeks = (cells + 6) / 7
        var dayCursor = 1 - leadingBlanks

        repeat(weeks) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                repeat(7) {
                    val dayNumber = dayCursor
                    dayCursor++
                    if (dayNumber in 1..daysInMonth) {
                        val date = firstOfMonth.withDayOfMonth(dayNumber)
                        val dateKey = date.toString()
                        DayCell(
                            dayNumber = dayNumber,
                            dateKey = dateKey,
                            total = dayTotals[dateKey],
                            peakExpense = peakExpense,
                            isToday = date == today,
                            isSelected = dateKey == selectedDateKey,
                            onClick = { onSelectDay(if (dateKey == selectedDateKey) null else dateKey) },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Box(Modifier.weight(1f).aspectRatio(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    dayNumber: Int,
    dateKey: String,
    total: DayTotal?,
    peakExpense: Long,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ledger = LedgerTheme.colors
    val hasIncome = (total?.incomeCents ?: 0L) > 0L
    val expense = total?.expenseCents ?: 0L
    val intensity = if (expense <= 0L) 0f else (expense.toFloat() / peakExpense).coerceIn(0.18f, 1f)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.small)
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    isToday -> MaterialTheme.colorScheme.surfaceContainerHigh
                    else -> MaterialTheme.colorScheme.surface
                }
            )
            .then(
                if (isToday && !isSelected) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
    ) {
        Text(
            text = dayNumber.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
            color = when {
                isSelected -> MaterialTheme.colorScheme.onPrimary
                isToday -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 4.dp),
        )

        if (expense > 0L) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 5.dp)
                    .fillMaxWidth(0.52f)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = intensity)
                        } else {
                            ledger.expense.copy(alpha = intensity)
                        }
                    )
            )
        }

        if (hasIncome) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 5.dp, bottom = 5.dp)
                    .height(5.dp)
                    .fillMaxWidth(0.12f)
                    .clip(CircleShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else ledger.income)
            )
        }
    }
}
