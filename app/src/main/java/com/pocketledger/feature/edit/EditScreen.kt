package com.pocketledger.feature.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketledger.data.entity.AccountEntity
import com.pocketledger.data.entity.CategoryEntity
import com.pocketledger.data.entity.TxnType
import com.pocketledger.domain.Money
import com.pocketledger.ui.theme.LedgerTheme
import com.pocketledger.ui.util.DateLabels

/**
 * Form-style editor for an existing transaction.
 *
 * Everything is visible at once on purpose: the keypad screen optimises for
 * entering a new row, while this one optimises for reviewing and correcting every
 * field of an existing one.
 */
@Composable
fun EditScreen(
    viewModel: EditViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EditHeader(
            loaded = state.loaded,
            missing = state.missing,
            onClose = onClose,
            onDelete = { confirmDelete = true },
        )

        if (state.missing) {
            Text(
                text = "这笔记录已经不存在了",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        TypeRow(type = state.type, onTypeChange = viewModel::setType)

        OutlinedTextField(
            value = state.amountInput,
            onValueChange = viewModel::setAmount,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            prefix = { Text("¥") },
            label = { Text("金额") },
            textStyle = MaterialTheme.typography.headlineSmall,
        )

        if (state.isTransfer) {
            AccountSection(
                title = "从",
                accounts = state.accounts,
                selectedId = state.accountId,
                onSelect = viewModel::selectAccount,
            )
            AccountSection(
                title = "到",
                accounts = state.destinationAccounts,
                selectedId = state.toAccountId,
                onSelect = viewModel::selectToAccount,
            )
            OutlinedTextField(
                value = state.feeInput,
                onValueChange = viewModel::setFee,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                prefix = { Text("¥") },
                label = { Text("手续费（可留空）") },
            )
        } else {
            if (state.type == TxnType.EXPENSE && state.mainCategories.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.mainCategories.forEach { main ->
                        val selected = main.id == state.selectedMainCategoryId
                        val accent = Color(main.colorArgb)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(CircleShape)
                                .background(
                                    if (selected) accent.copy(alpha = 0.16f)
                                    else MaterialTheme.colorScheme.surfaceContainer
                                )
                                .clickable { viewModel.selectMainCategory(main.id) }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = main.name,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) accent
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            SectionLabel("分类")
            CategoryChips(
                categories = state.visibleCategories,
                selectedId = state.selectedCategoryId,
                onSelect = viewModel::selectCategory,
            )

            AccountSection(
                title = "账户",
                accounts = state.accounts,
                selectedId = state.accountId,
                onSelect = viewModel::selectAccount,
            )
        }

        OutlinedTextField(
            value = state.merchant,
            onValueChange = viewModel::setMerchant,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("商家") },
        )
        OutlinedTextField(
            value = state.note,
            onValueChange = viewModel::setNote,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("备注") },
        )

        DateStepper(
            dateKey = state.dateKey,
            onShift = viewModel::shiftDate,
            onToday = viewModel::setToday,
        )

        if (state.canExcludeFromStats) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "不计收支",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "比如退款、账户互转，记下来但不进统计。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.isExcludedFromStats,
                    onCheckedChange = viewModel::setExcludedFromStats,
                )
            }
        }

        SaveRow(
            enabled = state.canSave,
            amountCents = state.amountCents,
            transfer = state.isTransfer,
            onSave = { viewModel.save(onClose) },
        )

        Spacer(Modifier.height(24.dp))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这笔记录？") },
            text = { Text("删除后余额和统计都会立刻跟着更新。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.delete(onClose)
                    }
                ) { Text("删除", color = LedgerTheme.colors.expense) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun EditHeader(
    loaded: Boolean,
    missing: Boolean,
    onClose: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "✕",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "编辑记录",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.weight(1f))
        if (loaded && !missing) {
            Text(
                text = "删除",
                style = MaterialTheme.typography.labelLarge,
                color = LedgerTheme.colors.expense,
                modifier = Modifier
                    .clickable(onClick = onDelete)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun TypeRow(type: TxnType, onTypeChange: (TxnType) -> Unit) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        TypeOption("支出", type == TxnType.EXPENSE, LedgerTheme.colors.expense) {
            onTypeChange(TxnType.EXPENSE)
        }
        TypeOption("收入", type == TxnType.INCOME, LedgerTheme.colors.income) {
            onTypeChange(TxnType.INCOME)
        }
        TypeOption("转账", type == TxnType.TRANSFER, LedgerTheme.colors.transfer) {
            onTypeChange(TxnType.TRANSFER)
        }
    }
}

@Composable
private fun TypeOption(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Categories as a chunked chip grid.
 *
 * Chunked rows rather than a lazy grid because this screen already scrolls; a
 * nested lazy grid inside a scrolling column needs a fixed height and fights the
 * parent for gestures.
 */
@Composable
private fun CategoryChips(
    categories: List<CategoryEntity>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        categories.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { category ->
                    val selected = category.id == selectedId
                    val accent = Color(category.colorArgb)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .background(if (selected) accent else accent.copy(alpha = 0.12f))
                            .clickable { onSelect(category.id) }
                            .padding(vertical = 9.dp, horizontal = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = category.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) Color.White else accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (categories.isEmpty()) {
            Text(
                text = "没有可选分类",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AccountSection(
    title: String,
    accounts: List<AccountEntity>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel(title)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            accounts.forEach { account ->
                val selected = account.id == selectedId
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainer
                        )
                        .clickable { onSelect(account.id) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = account.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DateStepper(dateKey: String, onShift: (Long) -> Unit, onToday: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel("日期")
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepChip("‹") { onShift(-1) }
            Text(
                text = DateLabels.dayLabel(dateKey),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
            )
            StepChip("今天", onClick = onToday, wide = true)
            Spacer(Modifier.width(8.dp))
            StepChip("›") { onShift(1) }
        }
    }
}

@Composable
private fun StepChip(label: String, wide: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = if (wide) 14.dp else 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SaveRow(
    enabled: Boolean,
    amountCents: Long,
    transfer: Boolean,
    onSave: () -> Unit,
) {
    val accent = if (transfer) LedgerTheme.colors.transfer else MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(MaterialTheme.shapes.large)
            .background(
                if (enabled) accent else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            .clickable(enabled = enabled, onClick = onSave),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (enabled) {
                "保存 ${Money.formatWithSymbol(amountCents)}"
            } else {
                "金额、分类和账户都要填"
            },
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
