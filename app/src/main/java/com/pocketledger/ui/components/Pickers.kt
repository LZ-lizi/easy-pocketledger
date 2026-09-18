package com.pocketledger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pocketledger.ui.theme.MoneyTextStyles
import java.time.LocalDate
import java.time.LocalTime

private val WEEKDAY_HEADERS = listOf("一", "二", "三", "四", "五", "六", "日")

/**
 * A month grid for picking a date.
 *
 * Replaces every date text field in the app. Typing `2026-09-01` into a field was the
 * single most error-prone interaction in the app -- it needed a keyboard, a specific
 * format, and it rejected the eight most natural things a person might type. A
 * calendar cannot produce an invalid date at all, and it shows the day of the week,
 * which is usually what the date is being chosen for.
 *
 * Today is ringed so "the 3rd" is never ambiguous, and the month can be paged without
 * leaving the dialog.
 */
@Composable
fun CalendarPickerDialog(
    initialDateKey: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    title: String = "选择日期",
) {
    val today = remember { LocalDate.now() }
    val initial = remember(initialDateKey) {
        runCatching { LocalDate.parse(initialDateKey) }.getOrElse { today }
    }
    var selected by remember { mutableStateOf(initial) }
    var visibleMonth by remember { mutableStateOf(initial.withDayOfMonth(1)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MonthPager(
                    month = visibleMonth,
                    onPrevious = { visibleMonth = visibleMonth.minusMonths(1) },
                    onNext = { visibleMonth = visibleMonth.plusMonths(1) },
                )

                Row(Modifier.fillMaxWidth()) {
                    WEEKDAY_HEADERS.forEach { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                MonthGrid(
                    month = visibleMonth,
                    selected = selected,
                    today = today,
                    onSelect = { selected = it },
                )

                TextButton(onClick = { selected = today; visibleMonth = today.withDayOfMonth(1) }) {
                    Text("回到今天")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(selected.toString()); onDismiss() }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun MonthPager(month: LocalDate, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PagerButton("‹", onPrevious)
        Text(
            text = "%d 年 %d 月".format(month.year, month.monthValue),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        PagerButton("›", onNext)
    }
}

@Composable
private fun PagerButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The grid itself.
 *
 * Plain rows rather than a lazy grid: a month is at most 42 cells, and a scrollable
 * container nested inside the dialog's own layout would fight it for gestures.
 */
@Composable
private fun MonthGrid(
    month: LocalDate,
    selected: LocalDate,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
) {
    val firstOfMonth = month.withDayOfMonth(1)
    val daysInMonth = firstOfMonth.lengthOfMonth()
    val leadingBlanks = firstOfMonth.dayOfWeek.value - 1        // Monday == 1
    val weeks = (leadingBlanks + daysInMonth + 6) / 7
    var dayCursor = 1 - leadingBlanks

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(weeks) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(7) {
                    val dayNumber = dayCursor
                    dayCursor++
                    if (dayNumber in 1..daysInMonth) {
                        val date = firstOfMonth.withDayOfMonth(dayNumber)
                        DayPip(
                            date = date,
                            isSelected = date == selected,
                            isToday = date == today,
                            onClick = { onSelect(date) },
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
private fun DayPip(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected || isToday) FontWeight.SemiBold else FontWeight.Normal,
            color = when {
                isSelected -> MaterialTheme.colorScheme.onPrimary
                isToday -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

/**
 * Hour and minute wheels.
 *
 * Opened as a second-level dialog from whatever shows the time, and deliberately has
 * no step buttons: nudging a time by five minutes is exactly the case a wheel handles
 * better, and the ±-chips it replaces could not express "13:47" at all.
 *
 * The value updates while the wheel is still settling, so the confirm button always
 * writes what is under the highlight band.
 */
@Composable
fun WheelTimePickerDialog(
    initialTime: LocalTime,
    onDismiss: () -> Unit,
    onPick: (hour: Int, minute: Int) -> Unit,
    title: String = "选择时间",
) {
    var hour by remember { mutableStateOf(initialTime.hour) }
    var minute by remember { mutableStateOf(initialTime.minute) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        Wheel(
                            count = 24,
                            initial = hour,
                            onValueChange = { hour = it },
                        )
                    }
                    Text(
                        text = ":",
                        style = MoneyTextStyles.Large,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box(Modifier.weight(1f)) {
                        Wheel(
                            count = 60,
                            initial = minute,
                            onValueChange = { minute = it },
                        )
                    }
                }
                Text(
                    text = "当前 %02d:%02d".format(hour, minute),
                    style = MoneyTextStyles.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(hour, minute); onDismiss() }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

private val WHEEL_ITEM_HEIGHT = 36.dp
private const val WHEEL_VISIBLE_ITEMS = 5

/**
 * One snapping wheel.
 *
 * The centred item is simply the first visible one, which is what the symmetric
 * content padding makes true -- so the selected value needs no scroll-settled
 * callback and cannot disagree with what the user sees under the highlight band.
 */
@Composable
private fun Wheel(
    count: Int,
    initial: Int,
    onValueChange: (Int) -> Unit,
) {
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = initial.coerceIn(0, count - 1)
    )
    val fling = rememberSnapFlingBehavior(lazyListState = state)

    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex.coerceIn(0, count - 1) }
            .collect { index -> onValueChange(index) }
    }

    Box {
        LazyColumn(
            state = state,
            flingBehavior = fling,
            modifier = Modifier.height(WHEEL_ITEM_HEIGHT * WHEEL_VISIBLE_ITEMS),
            contentPadding = PaddingValues(
                vertical = WHEEL_ITEM_HEIGHT * (WHEEL_VISIBLE_ITEMS / 2),
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(count) { index ->
                Box(
                    modifier = Modifier
                        .height(WHEEL_ITEM_HEIGHT)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "%02d".format(index),
                        style = MoneyTextStyles.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(
                            alpha = if (state.firstVisibleItemIndex == index) 1f else 0.35f
                        ),
                    )
                }
            }
        }

        // Highlight band, drawn over the wheel so the selected row is unmistakable.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(WHEEL_ITEM_HEIGHT)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
        )
    }
}
