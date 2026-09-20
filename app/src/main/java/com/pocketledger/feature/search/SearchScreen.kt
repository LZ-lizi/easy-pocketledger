package com.pocketledger.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.pocketledger.domain.Money
import com.pocketledger.ui.components.CalendarPickerDialog
import com.pocketledger.ui.components.DestructiveOutlinedButton
import com.pocketledger.ui.components.LedgerIcon
import com.pocketledger.ui.components.LedgerIconView
import com.pocketledger.ui.theme.LedgerTheme
import com.pocketledger.ui.theme.MoneyTextStyles
import com.pocketledger.ui.util.DateLabels

/**
 * Finds entries by keyword, date, category, account, direction or amount.
 *
 * Filters rather than a single text box: "how much did I spend on 外卖 in September" is
 * three conditions, and typing them into one field would mean inventing a query syntax
 * nobody would guess.
 */
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val todayKey = remember { java.time.LocalDate.now().toString() }
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }
    var amountOpen by remember { mutableStateOf(false) }

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
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "‹",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "搜索",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        item(key = "keyword") {
            OutlinedTextField(
                value = state.filters.keyword,
                onValueChange = viewModel::setKeyword,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("关键词") },
                placeholder = { Text("交易对象或备注") },
                trailingIcon = {
                    if (state.filters.keyword.isNotEmpty()) {
                        Text(
                            text = "✕",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clickable { viewModel.clearKeyword() }
                                .padding(8.dp),
                        )
                    }
                },
            )
        }

        item(key = "type") {
            FilterRow(label = "收支") {
                FilterChip("全部", state.filters.type == null) { viewModel.setType(null) }
                FilterChip("支出", state.filters.type == TxnType.EXPENSE) {
                    viewModel.setType(TxnType.EXPENSE)
                }
                FilterChip("收入", state.filters.type == TxnType.INCOME) {
                    viewModel.setType(TxnType.INCOME)
                }
                FilterChip("转账", state.filters.type == TxnType.TRANSFER) {
                    viewModel.setType(TxnType.TRANSFER)
                }
            }
        }

        item(key = "date") {
            FilterRow(label = "日期") {
                DateChip(
                    label = state.filters.startKey?.let { DateLabels.dayLabel(it) } ?: "开始",
                    set = state.filters.startKey != null,
                    onClick = { pickingStart = true },
                )
                DateChip(
                    label = state.filters.endKey?.let { DateLabels.dayLabel(it) } ?: "结束",
                    set = state.filters.endKey != null,
                    onClick = { pickingEnd = true },
                )
                if (state.filters.startKey != null || state.filters.endKey != null) {
                    ClearChip { viewModel.setDateRange(null, null) }
                }
            }
        }

        item(key = "amount") {
            FilterRow(label = "金额") {
                DateChip(
                    label = amountLabel(state),
                    set = state.filters.minCents != null || state.filters.maxCents != null,
                    onClick = { amountOpen = true },
                )
                if (state.filters.minCents != null || state.filters.maxCents != null) {
                    ClearChip { viewModel.setAmountRange(null, null) }
                }
            }
        }

        if (state.categories.isNotEmpty()) {
            item(key = "category") {
                FilterRow(label = "类别") {
                    FilterChip("全部", state.filters.categoryId == null) {
                        viewModel.setCategory(null)
                    }
                    state.categories
                        .filter { it.kind == com.pocketledger.data.entity.CategoryKind.EXPENSE }
                        .filter { it.parentId == null }
                        .forEach { category ->
                            FilterChip(category.name, state.filters.categoryId == category.id) {
                                viewModel.setCategory(
                                    if (state.filters.categoryId == category.id) null else category.id
                                )
                            }
                        }
                }
            }
        }

        if (state.accounts.isNotEmpty()) {
            item(key = "account") {
                FilterRow(label = "账户") {
                    FilterChip("全部", state.filters.accountId == null) {
                        viewModel.setAccount(null)
                    }
                    state.accounts.forEach { account ->
                        FilterChip(account.name, state.filters.accountId == account.id) {
                            viewModel.setAccount(
                                if (state.filters.accountId == account.id) null else account.id
                            )
                        }
                    }
                }
            }
        }

        item(key = "summary") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (state.hasFilters) {
                        "筛选出 ${state.resultCount} 条"
                    } else {
                        "全部 ${state.resultCount} 条"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.resultCount > 0) {
                    Text(
                        text = " · 合计 ${Money.formatWithSymbol(state.totalCents)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (state.hasFilters) {
                    TextButton(onClick = viewModel::clearAll) { Text("清空条件") }
                }
            }
        }

        if (state.loaded && state.results.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = if (state.hasFilters) "没有符合条件的记录" else "此账本暂无记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }

        items(
            count = state.results.size,
            key = { "result-${state.results[it].id}" },
        ) { index ->
            val row = state.results[index]
            ResultRow(row = row, onClick = { onOpenTransaction(row.id) })
        }
    }

    if (pickingStart) {
        CalendarPickerDialog(
            initialDateKey = state.filters.startKey ?: todayKey,
            onPick = {
                viewModel.setDateRange(it, state.filters.endKey)
                pickingStart = false
            },
            onDismiss = { pickingStart = false },
        )
    }
    if (pickingEnd) {
        CalendarPickerDialog(
            initialDateKey = state.filters.endKey ?: todayKey,
            onPick = {
                viewModel.setDateRange(state.filters.startKey, it)
                pickingEnd = false
            },
            onDismiss = { pickingEnd = false },
        )
    }
    if (amountOpen) {
        AmountRangeDialog(
            minCents = state.filters.minCents,
            maxCents = state.filters.maxCents,
            onConfirm = { min, max ->
                viewModel.setAmountRange(min, max)
                amountOpen = false
            },
            onDismiss = { amountOpen = false },
        )
    }
}

private fun amountLabel(state: SearchUiState): String {
    val min = state.filters.minCents
    val max = state.filters.maxCents
    return when {
        min == null && max == null -> "不限"
        min != null && max != null -> "${Money.formatCompact(min)} ~ ${Money.formatCompact(max)}"
        min != null -> "≥ ${Money.formatCompact(min)}"
        else -> "≤ ${Money.formatCompact(max!!)}"
    }
}

@Composable
private fun FilterRow(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainer
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
        )
    }
}

/** A date or amount chip: outlined while unset so "off" is visible at a glance. */
@Composable
private fun DateChip(label: String, set: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (set) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainer
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (set) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
        )
    }
}

@Composable
private fun ClearChip(onClick: () -> Unit) {
    DestructiveOutlinedButton(
        label = "清除",
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp),
    )
}

@Composable
private fun ResultRow(row: TxnRow, onClick: () -> Unit) {
    val ledger = LedgerTheme.colors
    val type = runCatching { TxnType.valueOf(row.type) }.getOrDefault(TxnType.EXPENSE)
    val accent = when (type) {
        TxnType.INCOME -> ledger.income
        TxnType.TRANSFER -> ledger.transfer
        TxnType.EXPENSE -> row.categoryColorArgb?.let { Color(it) } ?: ledger.expense
    }
    val title = when (type) {
        TxnType.TRANSFER -> row.note?.takeIf { it.isNotBlank() }
            ?: "${row.accountName} → ${row.toAccountName ?: "?"}"
        else -> row.categoryName ?: "未分类"
    }
    val subtitle = listOfNotNull(
        row.merchant?.takeIf { it.isNotBlank() },
        row.note?.takeIf { it.isNotBlank() && type != TxnType.TRANSFER },
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
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                LedgerIconView(
                    icon = LedgerIcon.forKey(row.categoryIconKey),
                    tint = accent,
                    size = 20.dp,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = DateLabels.dayLabel(row.localDateKey) +
                        if (subtitle.isNotEmpty()) " · $subtitle" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = when (type) {
                    TxnType.INCOME -> Money.formatSigned(row.amountCents, negative = false)
                    TxnType.EXPENSE -> Money.formatSigned(row.amountCents, negative = true)
                    TxnType.TRANSFER -> Money.formatWithSymbol(row.amountCents)
                },
                style = MoneyTextStyles.Medium,
                color = when (type) {
                    TxnType.INCOME -> ledger.income
                    TxnType.EXPENSE -> MaterialTheme.colorScheme.onSurface
                    TxnType.TRANSFER -> ledger.transfer
                },
            )
        }
    }
}

/** Two bounds, each optional; blank on either side leaves that end open. */
@Composable
private fun AmountRangeDialog(
    minCents: Long?,
    maxCents: Long?,
    onConfirm: (Long?, Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    var minText by remember {
        mutableStateOf(minCents?.let { Money.formatCompact(it) } ?: "")
    }
    var maxText by remember {
        mutableStateOf(maxCents?.let { Money.formatCompact(it) } ?: "")
    }
    val min = minText.trim().takeIf { it.isNotEmpty() }?.let { Money.parseYuanToCents(it) }
    val max = maxText.trim().takeIf { it.isNotEmpty() }?.let { Money.parseYuanToCents(it) }
    val valid = (minText.isBlank() || min != null) && (maxText.isBlank() || max != null)

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("金额区间") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = minText,
                    onValueChange = { minText = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    prefix = { Text("¥") },
                    label = { Text("不低于") },
                    isError = minText.isNotBlank() && min == null,
                )
                OutlinedTextField(
                    value = maxText,
                    onValueChange = { maxText = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    prefix = { Text("¥") },
                    label = { Text("不高于") },
                    isError = maxText.isNotBlank() && max == null,
                )
                Text(
                    text = "留空即不限制上限或下限",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(min, max) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
