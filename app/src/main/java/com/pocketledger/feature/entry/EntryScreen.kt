package com.pocketledger.feature.entry

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
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
import com.pocketledger.data.entity.AccountEntity
import com.pocketledger.data.entity.CategoryEntity
import com.pocketledger.data.entity.TxnType
import com.pocketledger.domain.Money
import com.pocketledger.ui.components.LedgerIcon
import com.pocketledger.ui.components.LedgerIconView
import com.pocketledger.ui.theme.LedgerTheme
import com.pocketledger.ui.theme.MoneyTextStyles
import com.pocketledger.ui.util.DateLabels
import java.time.LocalTime

private val KEYPAD_ROWS = listOf(
    listOf("1", "2", "3"),
    listOf("4", "5", "6"),
    listOf("7", "8", "9"),
    listOf(".", "0", "⌫"),
)

/**
 * Keypad-first entry for all three movements.
 *
 * The custom keypad replaces the system IME on purpose: it keeps the amount field,
 * the category grid and the account picker visible at once, so a routine entry
 * never scrolls and never fights a keyboard.
 *
 * The category grid shows leaf items directly. Grouping into 大类 exists for
 * statistics, not for data entry -- making someone pick a group first spends a tap
 * on a decision they do not care about.
 */
@Composable
fun EntryScreen(
    viewModel: EntryViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var dateTimeDialogVisible by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        EntryHeader(
            type = state.type,
            justSaved = state.justSaved,
            dateKey = state.dateKey,
            customTime = state.customTime,
            onTypeChange = viewModel::setType,
            onTapDate = { dateTimeDialogVisible = true },
            onClose = onClose,
        )

        AmountDisplay(amountInput = state.amountInput, type = state.type)

        if (state.isTransfer) {
            TransferPanel(
                accounts = state.accounts,
                fromId = state.selectedAccountId,
                toId = state.selectedToAccountId,
                feeInput = state.feeInput,
                onSelectFrom = viewModel::selectAccount,
                onSelectTo = viewModel::selectToAccount,
                onFeeChange = viewModel::setFee,
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(state.orderedCategories, key = { it.id }) { category ->
                    CategoryCell(
                        category = category,
                        selected = category.id == state.selectedCategoryId,
                        onClick = { viewModel.selectCategory(category.id) },
                    )
                }
            }

            AccountPicker(
                accounts = state.accounts,
                selectedId = state.selectedAccountId,
                onSelect = viewModel::selectAccount,
            )
        }

        NoteField(
            note = state.note,
            merchant = state.merchant,
            showMerchant = !state.isTransfer,
            onNoteChange = viewModel::setNote,
            onMerchantChange = viewModel::setMerchant,
        )

        Keypad(
            onKey = viewModel::pressKey,
            onBackspace = viewModel::backspace,
        )

        SaveButton(
            enabled = state.canSave,
            amountCents = state.amountCents,
            type = state.type,
            onSave = viewModel::save,
        )
    }

    if (dateTimeDialogVisible) {
        DateTimeDialog(
            dateKey = state.dateKey,
            time = state.customTime,
            onShiftDate = viewModel::shiftDate,
            onSetTime = viewModel::setTime,
            onUseNow = viewModel::useCurrentTime,
            onDismiss = { dateTimeDialogVisible = false },
        )
    }
}

@Composable
private fun EntryHeader(
    type: TxnType,
    justSaved: Boolean,
    dateKey: String,
    customTime: LocalTime?,
    onTypeChange: (TxnType) -> Unit,
    onTapDate: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
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

        Spacer(Modifier.width(6.dp))
        TypeSwitch(type = type, onTypeChange = onTypeChange)
        Spacer(Modifier.weight(1f))

        if (justSaved) {
            Text(
                text = "已记一笔",
                style = MaterialTheme.typography.labelMedium,
                color = LedgerTheme.colors.income,
            )
            Spacer(Modifier.width(6.dp))
        }

        // The time only appears once it has been changed; otherwise the entry simply
        // records "now" and the header stays quiet.
        val label = buildString {
            append(DateLabels.dayLabel(dateKey))
            if (customTime != null) {
                append(" ")
                append("%02d:%02d".format(customTime.hour, customTime.minute))
            }
        }
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(
                    if (customTime != null) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        Color.Transparent
                    }
                )
                .clickable(onClick = onTapDate)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (customTime != null) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/**
 * Date and time together.
 *
 * Steppers rather than a calendar and a clock dial: an entry is nearly always today
 * or yesterday and within an hour of now, so the corrections needed are one or two
 * steps, and a full picker would be more chrome than the job requires.
 */
@Composable
private fun DateTimeDialog(
    dateKey: String,
    time: LocalTime?,
    onShiftDate: (Long) -> Unit,
    onSetTime: (Int, Int) -> Unit,
    onUseNow: () -> Unit,
    onDismiss: () -> Unit,
) {
    val shown = time ?: LocalTime.now()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("记账时间") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "日期",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(48.dp),
                    )
                    StepChip("‹") { onShiftDate(-1) }
                    Text(
                        text = DateLabels.dayLabel(dateKey),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    StepChip("›") { onShiftDate(1) }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "时间",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(48.dp),
                    )
                    StepChip("‹") { onSetTime(shown.hour - 1, shown.minute) }
                    Text(
                        text = "%02d : %02d".format(shown.hour, shown.minute),
                        style = MoneyTextStyles.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    StepChip("›") { onSetTime(shown.hour + 1, shown.minute) }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    StepChip("-5 分") { onSetTime(shown.hour, shown.minute - 5) }
                    Spacer(Modifier.width(10.dp))
                    StepChip("+5 分") { onSetTime(shown.hour, shown.minute + 5) }
                }

                TextButton(onClick = { onUseNow(); onDismiss() }) {
                    Text("用当前时间")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        },
    )
}

@Composable
private fun StepChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
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
private fun TypeSwitch(type: TxnType, onTypeChange: (TxnType) -> Unit) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        TypePill("支出", type == TxnType.EXPENSE, LedgerTheme.colors.expense) {
            onTypeChange(TxnType.EXPENSE)
        }
        TypePill("收入", type == TxnType.INCOME, LedgerTheme.colors.income) {
            onTypeChange(TxnType.INCOME)
        }
        TypePill("转账", type == TxnType.TRANSFER, LedgerTheme.colors.transfer) {
            onTypeChange(TxnType.TRANSFER)
        }
    }
}

@Composable
private fun TypePill(
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
            .padding(horizontal = 13.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AmountDisplay(amountInput: String, type: TxnType) {
    val ledger = LedgerTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            text = if (amountInput.isEmpty()) "0.00" else amountInput,
            style = MoneyTextStyles.Hero,
            color = when {
                amountInput.isEmpty() -> MaterialTheme.colorScheme.onSurfaceVariant
                type == TxnType.INCOME -> ledger.income
                else -> MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * From / to account pickers plus an optional fee.
 *
 * The fee lives here rather than in the category grid because a transfer fee is a
 * real cost that leaves the source account -- it counts as an expense in reports
 * even though the transferred principal does not.
 */
@Composable
private fun TransferPanel(
    accounts: List<AccountEntity>,
    fromId: Long?,
    toId: Long?,
    feeInput: String,
    onSelectFrom: (Long) -> Unit,
    onSelectTo: (Long) -> Unit,
    onFeeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AccountLine("从", accounts, fromId, onSelectFrom)
        AccountLine(
            label = "到",
            accounts = accounts.filter { it.id != fromId },
            selectedId = toId,
            onSelect = onSelectTo,
            emptyHint = "需要至少两个账户才能转账",
        )
        OutlinedTextField(
            value = feeInput,
            onValueChange = onFeeChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("手续费（可留空）", style = MaterialTheme.typography.bodySmall) },
            prefix = { Text("¥") },
            textStyle = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "转账不计入收支，只有手续费算支出。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AccountLine(
    label: String,
    accounts: List<AccountEntity>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    emptyHint: String? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(22.dp),
        )
        Spacer(Modifier.width(8.dp))
        if (accounts.isEmpty()) {
            Text(
                text = emptyHint ?: "没有可用账户",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                accounts.forEach { account ->
                    AccountChip(account, account.id == selectedId) { onSelect(account.id) }
                }
            }
        }
    }
}

@Composable
private fun AccountChip(account: AccountEntity, selected: Boolean, onClick: () -> Unit) {
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
            text = account.name,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/**
 * A categorised cell with its vector icon.
 *
 * The icon replaces the earlier first-character badge: 早/午/晚 and 水/电/燃 all
 * begin with visually similar glyphs, so the badge made the grid harder to scan
 * rather than easier.
 */
@Composable
private fun CategoryCell(
    category: CategoryEntity,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = Color(category.colorArgb)
    Column(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(if (selected) accent else accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            LedgerIconView(
                icon = LedgerIcon.forKey(category.iconKey),
                tint = if (selected) Color.White else accent,
                size = 22.dp,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = category.name,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun AccountPicker(
    accounts: List<AccountEntity>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
) {
    if (accounts.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        accounts.forEach { account ->
            AccountChip(account, account.id == selectedId) { onSelect(account.id) }
        }
    }
}

@Composable
private fun NoteField(
    note: String,
    merchant: String,
    showMerchant: Boolean,
    onNoteChange: (String) -> Unit,
    onMerchantChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showMerchant) {
            OutlinedTextField(
                value = merchant,
                onValueChange = onMerchantChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("商家", style = MaterialTheme.typography.bodySmall) },
                textStyle = MaterialTheme.typography.bodySmall,
            )
        }
        OutlinedTextField(
            value = note,
            onValueChange = onNoteChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text("备注", style = MaterialTheme.typography.bodySmall) },
            textStyle = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun Keypad(onKey: (String) -> Unit, onBackspace: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        KEYPAD_ROWS.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { key ->
                    KeypadButton(
                        label = key,
                        modifier = Modifier.weight(1f),
                        onClick = { if (key == "⌫") onBackspace() else onKey(key) },
                    )
                }
            }
        }
    }
}

@Composable
private fun KeypadButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(50.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SaveButton(
    enabled: Boolean,
    amountCents: Long,
    type: TxnType,
    onSave: () -> Unit,
) {
    val ledger = LedgerTheme.colors
    val accent = when (type) {
        TxnType.INCOME -> ledger.income
        TxnType.TRANSFER -> ledger.transfer
        TxnType.EXPENSE -> MaterialTheme.colorScheme.primary
    }
    val hint = when (type) {
        TxnType.TRANSFER -> "选择转出和转入账户"
        else -> "选择分类和账户"
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)
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
                val verb = if (type == TxnType.TRANSFER) "转账" else "保存"
                "$verb ${Money.formatWithSymbol(amountCents)}"
            } else {
                hint
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
