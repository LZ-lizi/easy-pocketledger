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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
            Text(
                text = "统计",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        item(key = "mode") {
            ModeChips(mode = state.mode, onSelect = viewModel::setMode)
        }

        if (state.filterOptions.isNotEmpty()) {
            item(key = "filter") {
                CategoryFilterRow(
                    options = state.filterOptions,
                    selectedId = state.filterMainCategoryId,
                    onSelect = viewModel::setFilter,
                )
            }
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

        if (state.months.isNotEmpty()) {
            item(key = "trend") { TrendCard(state) }
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
                    text = "这段时间还没有支出记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 32.dp),
                )
            }
        }
    }
}

/**
 * Narrows the whole page to one 大类.
 *
 * A leaf inherits its parent in the query, so picking 餐饮 also counts 外卖 and 早餐
 * without the user having to think about the hierarchy.
 */
@Composable
private fun CategoryFilterRow(
    options: List<CategoryEntity>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip("全部", selectedId == null) { onSelect(null) }
        options.forEach { option ->
            FilterChip(option.name, selectedId == option.id) { onSelect(option.id) }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainer
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
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
                    text = "还没有设置学期",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "去「我的 → 学期设置」添加一个日期区间（比如 2026 秋季学期：9月1日 到 1月15日），" +
                        "这里就能按学期看总账了。",
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
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                LabelledAmount("收入", state.totals.incomeCents, LedgerTheme.colors.income)
                LabelledAmount("支出", state.totals.expenseCents, LedgerTheme.colors.expense)
            }
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
private fun TrendCard(state: StatsUiState) {
    val ledger = LedgerTheme.colors
    val peak = state.months.maxOfOrNull { maxOf(it.expenseCents, it.incomeCents) }
        ?.coerceAtLeast(1L) ?: 1L

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "近半年趋势",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                LegendDot("收入", ledger.income)
                Spacer(Modifier.width(10.dp))
                LegendDot("支出", ledger.expense)
            }
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                state.months.forEach { month ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Bar(month.expenseCents, peak, ledger.expense, Modifier.weight(1f))
                            Bar(month.incomeCents, peak, ledger.income, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = month.monthKey.takeLast(2).trimStart('0') + "月",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Bar(cents: Long, peak: Long, accent: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxHeight(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight((cents.toFloat() / peak).coerceIn(0f, 1f))
                .clip(MaterialTheme.shapes.extraSmall)
                .background(accent)
        )
    }
}

@Composable
private fun LegendDot(label: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
