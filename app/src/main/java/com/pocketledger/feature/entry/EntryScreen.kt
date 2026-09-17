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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketledger.data.entity.CategoryEntity
import com.pocketledger.data.entity.TxnType
import com.pocketledger.domain.Money
import com.pocketledger.ui.theme.LedgerTheme
import com.pocketledger.ui.theme.MoneyTextStyles
import com.pocketledger.ui.util.DateLabels

private val KEYPAD_ROWS = listOf(
    listOf("1", "2", "3"),
    listOf("4", "5", "6"),
    listOf("7", "8", "9"),
    listOf(".", "0", "⌫"),
)

/**
 * Keypad-first entry.
 *
 * The custom keypad replaces the system IME on purpose: it keeps the amount field,
 * the category grid and the account picker all visible at once, so a routine
 * entry never scrolls or fights a keyboard.
 */
@Composable
fun EntryScreen(
    viewModel: EntryViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        EntryHeader(
            type = state.type,
            justSaved = state.justSaved,
            dateKey = state.dateKey,
            onTypeChange = viewModel::setType,
            onClose = onClose,
        )

        AmountDisplay(amountInput = state.amountInput, type = state.type)

        if (state.type == TxnType.EXPENSE && state.mainCategories.size > 1) {
            MainCategoryTabs(
                categories = state.mainCategories,
                selectedId = state.selectedMainCategoryId,
                onSelect = viewModel::selectMainCategory,
            )
        }

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

        NoteField(
            note = state.note,
            merchant = state.merchant,
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
}

@Composable
private fun EntryHeader(
    type: TxnType,
    justSaved: Boolean,
    dateKey: String,
    onTypeChange: (TxnType) -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
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
        TypeSwitch(type = type, onTypeChange = onTypeChange)
        Spacer(Modifier.weight(1f))

        if (justSaved) {
            Text(
                text = "已记一笔",
                style = MaterialTheme.typography.labelMedium,
                color = LedgerTheme.colors.income,
            )
            Spacer(Modifier.width(8.dp))
        }

        Text(
            text = DateLabels.dayLabel(dateKey),
            style = MaterialTheme.typography.labelMedium,
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
        TypePill(
            label = "支出",
            selected = type == TxnType.EXPENSE,
            accent = LedgerTheme.colors.expense,
            onClick = { onTypeChange(TxnType.EXPENSE) },
        )
        TypePill(
            label = "收入",
            selected = type == TxnType.INCOME,
            accent = LedgerTheme.colors.income,
            onClick = { onTypeChange(TxnType.INCOME) },
        )
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
            .padding(horizontal = 18.dp, vertical = 7.dp),
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

@Composable
private fun MainCategoryTabs(
    categories: List<CategoryEntity>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        categories.forEach { category ->
            val selected = category.id == selectedId
            val accent = Color(category.colorArgb)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(
                        if (selected) accent.copy(alpha = 0.16f)
                        else MaterialTheme.colorScheme.surfaceContainer
                    )
                    .clickable { onSelect(category.id) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

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
            Text(
                text = category.name.take(1),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = if (selected) Color.White else accent,
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
    accounts: List<com.pocketledger.data.entity.AccountEntity>,
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
private fun NoteField(
    note: String,
    merchant: String,
    onNoteChange: (String) -> Unit,
    onMerchantChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = merchant,
            onValueChange = onMerchantChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text("商家", style = MaterialTheme.typography.bodySmall) },
            textStyle = MaterialTheme.typography.bodySmall,
        )
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
    val accent = if (type == TxnType.INCOME) LedgerTheme.colors.income else MaterialTheme.colorScheme.primary
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
                "保存 ${Money.formatWithSymbol(amountCents)}"
            } else {
                "选择分类和账户"
            },
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
