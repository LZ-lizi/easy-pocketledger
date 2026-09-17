package com.pocketledger.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketledger.data.dao.TxnRow
import com.pocketledger.data.entity.TxnType
import com.pocketledger.domain.AllowanceSnapshot
import com.pocketledger.domain.Money
import com.pocketledger.ui.theme.LedgerTheme
import com.pocketledger.ui.theme.MoneyTextStyles
import com.pocketledger.ui.util.DateLabels

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    contentPadding: PaddingValues,
    onOpenTransaction: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    HomeContent(
        state = state,
        contentPadding = contentPadding,
        onPreviousMonth = viewModel::previousMonth,
        onNextMonth = viewModel::nextMonth,
        onTapAllowance = viewModel::openAllowanceDialog,
        onToggleView = viewModel::toggleViewMode,
        onSelectDay = viewModel::selectDay,
        onOpenTransaction = onOpenTransaction,
        modifier = modifier,
    )

    if (state.allowanceDialogVisible) {
        AllowanceDialog(
            monthLabel = state.monthLabel,
            currentCents = state.allowance?.budgetCents ?: 0L,
            onDismiss = viewModel::dismissAllowanceDialog,
            onConfirm = viewModel::saveAllowance,
        )
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    contentPadding: PaddingValues,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onTapAllowance: () -> Unit,
    onToggleView: () -> Unit,
    onSelectDay: (String?) -> Unit,
    onOpenTransaction: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 4.dp,
            bottom = contentPadding.calculateBottomPadding() + 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "switcher") {
            MonthSwitcher(
                label = state.monthLabel,
                isCurrentMonth = state.isCurrentMonth,
                viewMode = state.viewMode,
                onPrevious = onPreviousMonth,
                onNext = onNextMonth,
                onToggleView = onToggleView,
            )
        }

        item(key = "allowance") {
            AllowanceCard(
                allowance = state.allowance,
                monthLabel = state.monthLabel,
                onClick = onTapAllowance,
            )
        }

        if (state.viewMode == HomeViewMode.CALENDAR) {
            item(key = "calendar") {
                MonthCalendar(
                    monthKey = state.monthKey,
                    dayTotals = state.dayTotals,
                    selectedDateKey = state.selectedDateKey,
                    onSelectDay = onSelectDay,
                )
            }
        }

        state.selectedDateKey?.let { dayKey ->
            item(key = "day-filter") {
                SelectedDayBar(
                    dateKey = dayKey,
                    onClear = { onSelectDay(null) },
                )
            }
        }

        if (state.isEmpty) {
            item(key = "empty") { EmptyLedgerHint(hasDayFilter = state.selectedDateKey != null) }
        }

        state.visibleDayGroups.forEach { group ->
            item(key = "day-${group.dateKey}") { DayHeader(group) }
            items(group.rows, key = { "txn-${it.id}" }) { row ->
                TransactionRowItem(row = row, onClick = { onOpenTransaction(row.id) })
            }
        }
    }
}

@Composable
private fun SelectedDayBar(dateKey: String, onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "只看 ${DateLabels.dayLabel(dateKey)}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "显示全月",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .clickable(onClick = onClear)
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun MonthSwitcher(
    label: String,
    isCurrentMonth: Boolean,
    viewMode: HomeViewMode,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleView: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton(symbol = "‹", onClick = onPrevious)
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        if (!isCurrentMonth) {
            Text(
                text = "回到本月",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(onClick = onNext)
                    .padding(horizontal = 4.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        StepButton(symbol = "›", onClick = onNext)
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable(onClick = onToggleView)
                .padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            Text(
                text = if (viewMode == HomeViewMode.CALENDAR) "列表" else "日历",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun StepButton(symbol: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The one number the whole app exists to answer: how much is left this month.
 *
 * Every yuan spent counts against the allowance -- daily *and* leisure -- so the
 * card can never look comfortable while the month is actually blown.
 */
@Composable
private fun AllowanceCard(
    allowance: AllowanceSnapshot?,
    monthLabel: String,
    onClick: () -> Unit,
) {
    val ledger = LedgerTheme.colors
    val scheme = MaterialTheme.colorScheme

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp)) {
            if (allowance == null) {
                Text(
                    text = "还没有设置生活费",
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "点这里填写每月生活费，首页就会显示「本月还能花」和日均可用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                return@Column
            }

            Text(
                text = if (allowance.hasBudget) "本月还能花" else "本月已花",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))

            val heroColor = when {
                !allowance.hasBudget -> scheme.onSurface
                allowance.isOverBudget -> ledger.expense
                else -> scheme.onSurface
            }
            Text(
                text = if (allowance.hasBudget) {
                    Money.formatWithSymbol(allowance.remainingCents)
                } else {
                    Money.formatWithSymbol(allowance.spentCents)
                },
                style = MoneyTextStyles.Hero,
                color = heroColor,
            )

            Spacer(Modifier.height(6.dp))
            Text(
                text = secondaryLine(allowance),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))
            // A plain fill against the allowance: the old two-colour split compared
            // 日常 against 娱乐, a distinction the category tree no longer carries.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                if (allowance.progress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(allowance.progress)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(
                                if (allowance.isOverBudget) ledger.expense
                                else MaterialTheme.colorScheme.primary
                            )
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "已花 ${Money.format(allowance.spentCents)}",
                    style = MoneyTextStyles.Small,
                    color = scheme.onSurfaceVariant,
                )
                if (allowance.hasBudget) {
                    Text(
                        text = " / 额度 ${Money.format(allowance.budgetCents)}",
                        style = MoneyTextStyles.Small,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun secondaryLine(allowance: AllowanceSnapshot): String = when {
    !allowance.hasBudget -> "已花 ${Money.formatWithSymbol(allowance.spentCents)}"
    allowance.isOverBudget -> "已超出 ${Money.formatWithSymbol(-allowance.remainingCents)}"
    allowance.daysRemaining <= 0 -> "本月已结束"
    else -> "日均 ${Money.formatWithSymbol(allowance.dailyAvailableCents)} · 剩 ${allowance.daysRemaining} 天"
}

@Composable
private fun DayHeader(group: DayGroup) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = DateLabels.dayLabelWithRelative(group.dateKey),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        if (group.expenseCents > 0L) {
            Text(
                text = "支出 ${Money.format(group.expenseCents)}",
                style = MoneyTextStyles.Small,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TransactionRowItem(row: TxnRow, onClick: () -> Unit) {
    val ledger = LedgerTheme.colors
    val type = runCatching { TxnType.valueOf(row.type) }.getOrDefault(TxnType.EXPENSE)
    val accent = when (type) {
        TxnType.INCOME -> ledger.income
        TxnType.EXPENSE -> if (row.mainCategoryId != null && row.categoryColorArgb != null) {
            // Category colour keeps the list scannable by habit rather than by reading.
            Color(row.categoryColorArgb)
        } else {
            ledger.expense
        }

        TxnType.TRANSFER -> ledger.transfer
    }
    val title = when (type) {
        TxnType.TRANSFER -> row.note?.takeIf { it.isNotBlank() }
            ?: "${row.accountName} → ${row.toAccountName ?: "?"}"
        else -> row.categoryName ?: "未分类"
    }
    val subtitle = listOfNotNull(
        row.merchant?.takeIf { it.isNotBlank() },
        row.note?.takeIf { it.isNotBlank() && type != TxnType.TRANSFER },
        row.accountName,
    ).joinToString(" · ")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryBadge(
                label = title,
                color = accent,
                muted = row.isExcludedFromStats,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                val signed = when (type) {
                    TxnType.INCOME -> Money.formatSigned(row.amountCents, negative = false)
                    TxnType.EXPENSE -> Money.formatSigned(row.amountCents, negative = true)
                    TxnType.TRANSFER -> Money.formatWithSymbol(row.amountCents)
                }
                Text(
                    text = signed,
                    style = MoneyTextStyles.Medium,
                    color = when (type) {
                        TxnType.INCOME -> ledger.income
                        TxnType.EXPENSE -> MaterialTheme.colorScheme.onSurface
                        TxnType.TRANSFER -> ledger.transfer
                    },
                )
                if (row.isExcludedFromStats) {
                    Text(
                        text = "不计收支",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * A coloured circle carrying the category's first character.
 *
 * Chosen over a hand-drawn icon per category: 26 bespoke vectors drawn blind would
 * be far more likely to look broken than a consistent typographic badge, and the
 * category colour already makes rows scannable by habit.
 */
@Composable
private fun CategoryBadge(label: String, color: Color, muted: Boolean) {
    val fill = if (muted) MaterialTheme.colorScheme.surfaceContainerHigh else color.copy(alpha = 0.16f)
    val content = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else color
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(MaterialTheme.shapes.small)
            .background(fill),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.take(1),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = content,
        )
    }
}

@Composable
private fun EmptyLedgerHint(hasDayFilter: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (hasDayFilter) "这天没有记录" else "这个月还没有记录",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (hasDayFilter) "换一天看看，或点右下角记一笔" else "点右下角的按钮开始记一笔",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AllowanceDialog(
    monthLabel: String,
    currentCents: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    var text by remember {
        mutableStateOf(if (currentCents > 0L) Money.formatCompact(currentCents) else "")
    }
    val parsed = Money.parseYuanToCents(text)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$monthLabel 生活费") },
        text = {
            Column {
                Text(
                    text = "填每月到手的钱。之后没设置的月份会自动沿用这一次的额度。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    prefix = { Text("¥") },
                    label = { Text("金额") },
                    isError = text.isNotEmpty() && parsed == null,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let(onConfirm) },
                enabled = parsed != null && parsed > 0L,
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
