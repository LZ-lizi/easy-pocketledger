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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketledger.data.dao.PeriodTotals
import com.pocketledger.data.dao.TxnRow
import com.pocketledger.data.entity.LedgerType
import com.pocketledger.data.entity.TxnType
import com.pocketledger.domain.AllowanceSnapshot
import com.pocketledger.domain.Money
import com.pocketledger.ui.components.LedgerIcon
import com.pocketledger.ui.components.LedgerIconView
import com.pocketledger.ui.components.LedgerSwitcherDialog
import com.pocketledger.ui.theme.LedgerTheme
import com.pocketledger.ui.theme.MoneyTextStyles
import com.pocketledger.ui.util.DateLabels

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    contentPadding: PaddingValues,
    onOpenTransaction: (Long) -> Unit,
    onOpenSearch: () -> Unit,
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
        onTapLedger = viewModel::openLedgerSwitcher,
        onOpenSearch = onOpenSearch,
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

    if (state.ledgerSwitcherVisible) {
        LedgerSwitcherDialog(
            ledgers = state.ledgers,
            selectedId = state.selectedLedgerId,
            onSelect = viewModel::selectLedger,
            onDismiss = viewModel::dismissLedgerSwitcher,
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
    onTapLedger: () -> Unit,
    onOpenSearch: () -> Unit,
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
                ledgerName = state.ledgerName,
                onPrevious = onPreviousMonth,
                onNext = onNextMonth,
                onToggleView = onToggleView,
                onTapLedger = onTapLedger,
                onOpenSearch = onOpenSearch,
            )
        }

        item(key = "allowance") {
            // A 累计模式 ledger has no accounts and no allowance, so it gets a running
            // total instead. Showing an allowance card there would invite the user to
            // configure something the ledger type cannot use.
            if (state.ledgerType == LedgerType.ACCUMULATE) {
                RunningTotalCard(
                    totals = state.totals,
                    monthLabel = state.monthLabel,
                )
            } else {
                AllowanceCard(
                    allowance = state.allowance,
                    monthLabel = state.monthLabel,
                    onClick = onTapAllowance,
                )
            }
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
    ledgerName: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleView: () -> Unit,
    onTapLedger: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The calendar toggle sits on the left so the date stepper below owns the
        // centre, where the eye already is.
        ViewToggle(viewMode = viewMode, onToggle = onToggleView)

        Spacer(Modifier.weight(1f))
        StepButton(symbol = "‹", onClick = onPrevious)
        // Fixed width and centred: the two arrows stay equidistant from the date
        // regardless of how long the label is.
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.width(MONTH_LABEL_WIDTH),
        )
        StepButton(symbol = "›", onClick = onNext)
        Spacer(Modifier.weight(1f))

        // Search sits left of the ledger chip: it filters the ledger the chip names, so
        // reading order runs "search this ledger", and the chip stays on the edge where
        // it has always been.
        RoundIconButton(
            icon = LedgerIcon.SEARCH,
            onClick = onOpenSearch,
        )
        Spacer(Modifier.width(8.dp))
        LedgerChip(name = ledgerName, onClick = onTapLedger)
    }
}

/** A round icon button in the header row: same size and shape as the view toggle. */
@Composable
private fun RoundIconButton(icon: LedgerIcon, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        LedgerIconView(
            icon = icon,
            tint = MaterialTheme.colorScheme.primary,
            size = 18.dp,
        )
    }
}

private val MONTH_LABEL_WIDTH = 104.dp

@Composable
private fun ViewToggle(viewMode: HomeViewMode, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        LedgerIconView(
            icon = if (viewMode == HomeViewMode.CALENDAR) LedgerIcon.LIST else LedgerIcon.CALENDAR,
            tint = MaterialTheme.colorScheme.primary,
            size = 18.dp,
        )
    }
}

@Composable
private fun LedgerChip(name: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = name.ifBlank { "账本" },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 84.dp),
        )
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
                    text = "点这里填写每月生活费，首页就会显示「预算剩余」和剩余日均可用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                return@Column
            }

            Text(
                text = if (allowance.hasBudget) "预算剩余" else "本月已花",
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
            // Two daily figures, facing each other: what is left to spend per remaining
            // day on the left, what is actually being spent per elapsed day on the
            // right. Reading them together is the "am I on pace" check, and neither
            // number alone answers it.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = secondaryLine(allowance),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "已记账日均 ${Money.formatWithSymbol(allowance.dailySpentCents)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }

            // The progress bar and the 已花/额度 row only say something when there is an
            // allowance to measure against. Without one they repeated the hero number a
            // third time -- the card read 本月已花 / ¥1,007.16 / 已花 ¥1,007.16 /
            // 已花 1,007.16 -- and drew an empty bar that carried no information.
            if (!allowance.hasBudget) return@Column

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
                Text(
                    text = " / 额度 ${Money.format(allowance.budgetCents)}",
                    style = MoneyTextStyles.Small,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun secondaryLine(allowance: AllowanceSnapshot): String = when {
    !allowance.hasBudget -> "点这里设置每月生活费，就能看到「预算剩余」和剩余日均可用"
    allowance.isOverBudget -> "已超出 ${Money.formatWithSymbol(-allowance.remainingCents)}"
    allowance.daysRemaining <= 0 -> "本月已结束"
    else -> "剩余日均可用 ${Money.formatWithSymbol(allowance.dailyAvailableCents)} · 剩 ${allowance.daysRemaining} 天"
}

/**
 * The 累计模式 home card: what came in, what went out, what is left over.
 *
 * Deliberately not a budget view. A ledger tracking a running total has no monthly
 * allowance by design, and inventing one here would contradict the mode the user
 * chose when creating it.
 */
@Composable
private fun RunningTotalCard(totals: PeriodTotals, monthLabel: String) {
    val ledger = LedgerTheme.colors
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = "$monthLabel 累计",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = Money.formatWithSymbol(totals.netCents),
                style = MoneyTextStyles.Hero,
                color = if (totals.netCents < 0L) ledger.expense else scheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "结余",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column {
                    Text(
                        text = "收入",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                    Text(
                        text = Money.formatWithSymbol(totals.incomeCents),
                        style = MoneyTextStyles.Medium,
                        color = ledger.income,
                    )
                }
                Column {
                    Text(
                        text = "支出",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                    Text(
                        text = Money.formatWithSymbol(totals.expenseCents),
                        style = MoneyTextStyles.Medium,
                        color = ledger.expense,
                    )
                }
            }
        }
    }
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
        // Both of the day's totals, in the same shape: a day that took in 200 and spent
        // 30 was reading as a 30 元 day, which is half the story.
        if (group.expenseCents > 0L) {
            Text(
                text = "支出 ${Money.format(group.expenseCents)}",
                style = MoneyTextStyles.Small,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (group.incomeCents > 0L) {
            if (group.expenseCents > 0L) Spacer(Modifier.width(10.dp))
            Text(
                text = "收入 ${Money.format(group.incomeCents)}",
                style = MoneyTextStyles.Small,
                color = LedgerTheme.colors.income,
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
                iconKey = row.categoryIconKey,
                fallback = title,
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
 * The category's vector icon on a tinted tile.
 *
 * A first-character badge was the earlier placeholder and read poorly: 早/午/晚 and
 * 水/电/燃 begin with visually similar glyphs, so the list was harder to scan than no
 * badge at all. Transfers carry no category, so they keep a text fallback.
 */
@Composable
private fun CategoryBadge(
    iconKey: String?,
    fallback: String,
    color: Color,
    muted: Boolean,
) {
    val fill = if (muted) MaterialTheme.colorScheme.surfaceContainerHigh else color.copy(alpha = 0.16f)
    val content = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else color
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(MaterialTheme.shapes.small)
            .background(fill),
        contentAlignment = Alignment.Center,
    ) {
        if (iconKey == null) {
            Text(
                text = fallback.take(1),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = content,
            )
        } else {
            LedgerIconView(
                icon = LedgerIcon.forKey(iconKey),
                tint = content,
                size = 20.dp,
            )
        }
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
