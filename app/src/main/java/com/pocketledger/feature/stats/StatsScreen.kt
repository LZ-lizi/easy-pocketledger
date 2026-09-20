package com.pocketledger.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
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
import com.pocketledger.data.entity.CategoryEntity
import com.pocketledger.domain.Money
import com.pocketledger.ui.components.ChartSlice
import com.pocketledger.ui.components.DonutChart
import com.pocketledger.ui.theme.LedgerTheme
import com.pocketledger.ui.theme.MoneyTextStyles

@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var filterOpen by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "统计",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                // One button instead of a chip strip: the filter is an occasional,
                // deliberate act, and a row of thirteen 大类 chips spent a whole line of
                // every visit on something almost never touched.
                if (state.filterOptions.isNotEmpty()) {
                    FilterButton(active = state.filterActive) { filterOpen = true }
                }
            }
        }

        item(key = "mode") {
            ModeChips(mode = state.mode, onSelect = viewModel::setMode)
        }

        when (state.mode) {
            StatsRangeMode.MONTH -> item(key = "month") {
                MonthStepper(
                    label = state.rangeLabel,
                    onPrevious = viewModel::previousMonth,
                    onNext = viewModel::nextMonth,
                )
            }

            StatsRangeMode.TERM -> item(key = "terms") {
                TermPicker(state = state, onSelect = viewModel::selectTerm)
            }

            else -> Unit
        }

        item(key = "totals") { TotalsCard(state) }

        if (state.hasExpense && state.donutSlices.isNotEmpty()) {
            item(key = "donut") { DonutCard(state = state, onPieLevel = viewModel::setPieLevel) }
        }

        if (state.topCategories.isNotEmpty()) {
            item(key = "ranking-title") {
                Text(
                    text = "支出排行",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            items(state.topCategories, key = { it.id }) { rank -> CategoryRankRow(rank) }
        } else {
            item(key = "empty") {
                Text(
                    text = "这段时间暂无支出记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 32.dp),
                )
            }
        }
    }

    if (filterOpen) {
        CategoryFilterDialog(
            options = state.filterOptions,
            selectedIds = state.filterCategoryIds,
            onApply = viewModel::setFilter,
            onDismiss = { filterOpen = false },
        )
    }
}

/**
 * The entry point to the category filter.
 *
 * Colour carries its state, because the button sits on a page where the filter's effect is
 * invisible until you look at the numbers: muted while every category is counted, accent
 * once the page has actually been narrowed. Opening the dialog shows the detail.
 */
@Composable
private fun FilterButton(active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainer
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = "筛选",
            style = MaterialTheme.typography.labelLarge,
            color = if (active) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/**
 * Picks which 大类 the whole page counts.
 *
 * Everything starts ticked, because "show me all my spending" is the question the page is
 * opened to answer; narrowing is the exception. Ticking a 大类 includes its items, so 餐饮
 * counts 外卖 and 早餐 without the hierarchy being a separate decision.
 */
@Composable
private fun CategoryFilterDialog(
    options: List<CategoryEntity>,
    selectedIds: Set<Long>,
    onApply: (Set<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(options) { mutableStateOf(selectedIds) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("筛选类别") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { draft = options.map { it.id }.toSet() }) {
                        Text("全选")
                    }
                    TextButton(onClick = { draft = emptySet() }) { Text("全不选") }
                }
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                draft = if (option.id in draft) {
                                    draft - option.id
                                } else {
                                    draft + option.id
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = option.id in draft,
                            onCheckedChange = { checked ->
                                draft = if (checked) draft + option.id else draft - option.id
                            },
                        )
                        Text(
                            text = option.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                if (draft.isEmpty()) {
                    Text(
                        text = "未选择任何类别",
                        style = MaterialTheme.typography.labelSmall,
                        color = LedgerTheme.colors.expense,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onApply(draft)
                    onDismiss()
                },
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun ModeChips(mode: StatsRangeMode, onSelect: (StatsRangeMode) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatsRangeMode.entries.forEach { candidate ->
            val selected = candidate == mode
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainer
                    )
                    .clickable { onSelect(candidate) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = candidate.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun TermPicker(state: StatsUiState, onSelect: (Long) -> Unit) {
    if (state.needsTermSetup) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = "暂未设置学期",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "在「我的 → 学期设置」添加一个日期区间以查看学期总账",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.terms.forEach { term ->
            val selected = term.id == state.selectedTermId
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainer
                    )
                    .clickable { onSelect(term.id) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = term.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun MonthStepper(label: String, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepChip("‹", onPrevious)
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        StepChip("›", onNext)
    }
}

@Composable
private fun StepChip(symbol: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TotalsCard(state: StatsUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = "结余",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = Money.formatWithSymbol(state.totals.netCents),
                style = MoneyTextStyles.Hero,
                color = if (state.totals.netCents < 0L) {
                    LedgerTheme.colors.expense
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Spacer(Modifier.height(14.dp))
            // 收入 left, 支出 right, above the bar that splits between them, so each label
            // sits over its own half.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                LabelledAmount("收入", state.totals.incomeCents, LedgerTheme.colors.income)
                LabelledAmount("支出", state.totals.expenseCents, LedgerTheme.colors.expense)
            }
            Spacer(Modifier.height(10.dp))
            IncomeExpenseBar(state.totals.incomeCents, state.totals.expenseCents)
        }
    }
}

/**
 * One bar split between what came in and what went out.
 *
 * This replaced a six-month bar chart below the card. The chart answered "how has this year
 * gone", which the totals above it could not -- but it cost a whole card of vertical space
 * for a shape most visits never looked at, while the *proportion* of income to expense is
 * the thing the card is actually about. The two amounts were already printed side by side;
 * a bar under them says the same thing without a second card.
 *
 * A month with income but no spending shows a bar that is entirely green, and vice versa.
 * A period with neither draws an empty track rather than nothing, so the card does not
 * change height between a quiet month and a busy one.
 */
@Composable
private fun IncomeExpenseBar(incomeCents: Long, expenseCents: Long) {
    val total = incomeCents + expenseCents
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        if (total > 0L && incomeCents > 0L) {
            Box(
                modifier = Modifier
                    .weight(incomeCents.toFloat())
                    .fillMaxHeight()
                    .background(LedgerTheme.colors.income),
            )
        }
        if (total > 0L && expenseCents > 0L) {
            Box(
                modifier = Modifier
                    .weight(expenseCents.toFloat())
                    .fillMaxHeight()
                    .background(LedgerTheme.colors.expense),
            )
        }
    }
}

@Composable
private fun LabelledAmount(label: String, cents: Long, accent: Color) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = Money.formatWithSymbol(cents),
            style = MoneyTextStyles.Medium,
            color = accent,
        )
    }
}

/**
 * Category donut with its legend.
 *
 * Wedge colours come from each category's own stored colour, so a category keeps
 * the same identity here, in the ledger list and on the entry keypad.
 */
@Composable
private fun DonutCard(state: StatsUiState, onPieLevel: (PieLevel) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "支出构成",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                // Only the pie offers this: the ranking below stays on leaves either way.
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    PieLevel.entries.forEach { level ->
                        val selected = level == state.pieLevel
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else Color.Transparent
                                )
                                .clickable { onPieLevel(level) }
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                        ) {
                            Text(
                                text = level.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                DonutChart(
                    slices = state.donutSlices.map {
                        ChartSlice(label = it.name, value = it.totalCents, color = Color(it.colorArgb))
                    },
                    diameter = 176.dp,
                    thickness = 22.dp,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "支出",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = Money.formatCompact(state.totals.expenseCents),
                            style = MoneyTextStyles.Large,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            state.donutSlices.forEach { slice ->
                LegendRow(slice)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun LegendRow(slice: CategoryRank) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Color(slice.colorArgb))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = slice.name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${(slice.share * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = Money.formatWithSymbol(slice.totalCents),
            style = MoneyTextStyles.Small,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun CategoryRankRow(rank: CategoryRank) {
    val accent = Color(rank.colorArgb)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = rank.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${(rank.share * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = Money.formatWithSymbol(rank.totalCents),
                    style = MoneyTextStyles.Medium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(7.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(rank.share.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(accent)
                )
            }
        }
    }
}
