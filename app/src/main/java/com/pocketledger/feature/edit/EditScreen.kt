package com.pocketledger.feature.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import com.pocketledger.domain.DateKeys
import com.pocketledger.domain.Money
import com.pocketledger.ui.components.CalendarPickerDialog
import com.pocketledger.ui.components.ConfirmDeleteDialog
import com.pocketledger.ui.components.DestructiveOutlinedButton
import com.pocketledger.ui.components.LedgerIcon
import com.pocketledger.ui.components.LedgerIconView
import com.pocketledger.ui.components.WheelTimePickerDialog
import com.pocketledger.ui.theme.LedgerTheme
import com.pocketledger.ui.util.DateLabels
import java.time.LocalTime

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
            canDelete = !state.ledgerArchived,
            onClose = onClose,
            onDelete = { confirmDelete = true },
        )

        if (state.missing) {
            Text(
                text = "这笔记录已被删除",
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
            label = { Text("交易对象") },
        )
        OutlinedTextField(
            value = state.note,
            onValueChange = viewModel::setNote,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("备注") },
        )

        DateTimeFields(
            dateKey = state.dateKey,
            time = state.time,
            storedMillis = state.storedMillis,
            onPickDate = viewModel::setDate,
            onPickTime = viewModel::setTime,
            onClearTime = viewModel::clearTime,
        )

        if (state.canExcludeFromStats) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "不计收支",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = state.isExcludedFromStats,
                    onCheckedChange = viewModel::setExcludedFromStats,
                )
            }
        }

        if (state.ledgerArchived) {
            Text(
                text = "此账本已归档，无法修改",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(14.dp),
            )
        } else {
            SaveRow(
                enabled = state.canSave,
                amountCents = state.amountCents,
                transfer = state.isTransfer,
                onSave = { viewModel.save(onClose) },
            )
        }

        Spacer(Modifier.height(24.dp))
    }

    if (confirmDelete) {
        ConfirmDeleteDialog(
            title = "删除这笔记录",
            onConfirm = {
                confirmDelete = false
                viewModel.delete(onClose)
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun EditHeader(
    loaded: Boolean,
    missing: Boolean,
    canDelete: Boolean,
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
        if (loaded && !missing && canDelete) {
            DestructiveOutlinedButton(
                label = "删除",
                onClick = onDelete,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
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

/**
 * Date and time for this entry, each opening its own picker.
 *
 * Both used to be ±-chips. A stepper is fine for "yesterday" and useless for
 * "the 3rd of last month", and the ±5 minute chips could not express 13:47 at all.
 * The pickers float above this screen, so neither costs a navigation.
 */
@Composable
private fun DateTimeFields(
    dateKey: String,
    time: LocalTime?,
    storedMillis: Long,
    onPickDate: (String) -> Unit,
    onPickTime: (hour: Int, minute: Int) -> Unit,
    onClearTime: () -> Unit,
) {
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    val effectiveTime = time ?: DateKeys.timeOf(storedMillis)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel("日期")
        PickRow(
            value = DateLabels.dayLabel(dateKey),
            onClick = { pickingDate = true },
        )

        SectionLabel("时间")
        PickRow(
            value = if (time == null) {
                "%02d:%02d（原始时间）".format(effectiveTime.hour, effectiveTime.minute)
            } else {
                "%02d:%02d".format(effectiveTime.hour, effectiveTime.minute)
            },
            muted = time == null,
            onClick = { pickingTime = true },
        )
        if (time != null) {
            TextButton(onClick = onClearTime) { Text("改回当前时间") }
        }
    }

    if (pickingDate) {
        CalendarPickerDialog(
            initialDateKey = dateKey,
            onDismiss = { pickingDate = false },
            onPick = onPickDate,
        )
    }

    if (pickingTime) {
        WheelTimePickerDialog(
            initialTime = effectiveTime,
            onDismiss = { pickingTime = false },
            onPick = onPickTime,
        )
    }
}

@Composable
private fun PickRow(value: String, onClick: () -> Unit, muted: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = if (muted) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f),
        )
        LedgerIconView(
            icon = LedgerIcon.CHEVRON_RIGHT,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            size = 16.dp,
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
                "请填写金额、分类和账户"
            },
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
