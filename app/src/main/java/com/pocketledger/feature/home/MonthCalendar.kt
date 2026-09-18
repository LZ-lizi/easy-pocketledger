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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketledger.data.dao.DayTotal
import com.pocketledger.domain.Money
import com.pocketledger.ui.theme.LedgerTheme
import java.time.LocalDate

private val WEEKDAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

/**
 * Month grid with each day's income and spending written into the cell.
 *
 * The cell used to carry a coloured intensity bar, which showed *how much* only in
 * relation to the busiest day of the month and never said which way the money went.
 * Showing the two figures directly -- red for spending, green for income, in the empty
 * space under the date -- answers "what happened on the 14th" without a tap. Weeks
 * start on Monday, matching Chinese habit.
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
                            total = dayTotals[dateKey],
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
    total: DayTotal?,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ledger = LedgerTheme.colors
    val expense = total?.expenseCents ?: 0L
    val income = total?.incomeCents ?: 0L

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
                .padding(top = 3.dp),
        )

        // The amounts fill the space under the date. On a selected cell the coloured
        // text would fight the primary background, so it is drawn in the on-primary
        // colour there instead -- the position still says which figure is which.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (expense > 0L) {
                DayAmount(
                    text = "-${Money.formatTiny(expense)}",
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else ledger.expense,
                )
            }
            if (income > 0L) {
                DayAmount(
                    text = "+${Money.formatTiny(income)}",
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else ledger.income,
                )
            }
        }
    }
}

/**
 * One amount inside a day cell.
 *
 * `softWrap = false` plus the tiny style keeps a four-figure amount on the single line
 * the cell can afford; [Money.formatTiny] has already capped the length.
 */
@Composable
private fun DayAmount(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 9.sp,
        lineHeight = 11.sp,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
    )
}
