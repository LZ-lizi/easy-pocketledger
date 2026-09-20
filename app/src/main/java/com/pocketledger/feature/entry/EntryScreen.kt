package com.pocketledger.feature.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketledger.data.entity.AccountEntity
import com.pocketledger.data.entity.CategoryEntity
import com.pocketledger.domain.Money
import com.pocketledger.ui.components.CalendarPickerDialog
import com.pocketledger.ui.components.LedgerIcon
import com.pocketledger.ui.components.LedgerIconView
import com.pocketledger.ui.components.WheelTimePickerDialog
import com.pocketledger.ui.theme.LedgerTheme
import com.pocketledger.ui.theme.MoneyTextStyles
import com.pocketledger.ui.util.DateLabels
import java.time.LocalTime
import kotlin.math.floor

private val KEYPAD_ROWS = listOf(
    listOf("1", "2", "3"),
    listOf("4", "5", "6"),
    listOf("7", "8", "9"),
    listOf(".", "0", "⌫"),
)

/**
 * Keypad-first entry for expenses, income, transfers and 月付 plans.
 *
 * The category grid shows **leaf items only** and starts collapsed to the pinned
 * set, so the common case needs no scrolling. Grouping into 大类 exists for
 * statistics, not for data entry.
 *
 * Saving closes the screen. Previously it cleared the amount and left a "已记一笔"
 * hint, which left it ambiguous whether the entry had actually been recorded.
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
            mode = state.mode,
            dateKey = state.dateKey,
            customTime = state.customTime,
            onModeChange = viewModel::setMode,
            onTapDate = { dateTimeDialogVisible = true },
            onClose = onClose,
        )

        AmountDisplay(
            amountInput = state.amountInput,
            mode = state.mode,
        )

        when {
            state.isTransfer -> TransferPanel(
                accounts = state.accounts,
                fromId = state.selectedAccountId,
                toId = state.selectedToAccountId,
                feeInput = state.feeInput,
                onSelectFrom = viewModel::selectAccount,
                onSelectTo = viewModel::selectToAccount,
                onFeeChange = viewModel::setFee,
                modifier = Modifier.weight(1f),
            )

            state.isMonthly -> MonthlyPanel(
                state = state,
                onNameChange = viewModel::setPlanName,
                onPeriodsChange = viewModel::setPlanPeriods,
                onRepayDayChange = viewModel::setPlanRepayDay,
                onFeeChange = viewModel::setPlanFee,
                onSelectCategory = viewModel::selectCategory,
                onSelectAccount = viewModel::selectAccount,
                modifier = Modifier.weight(1f),
            )

            else -> {
                CategoryGrid(
                    state = state,
                    onSelect = viewModel::selectCategory,
                    modifier = Modifier.weight(1f),
                )
                AccountPicker(
                    accounts = state.accounts,
                    selectedId = state.selectedAccountId,
                    onSelect = viewModel::selectAccount,
                    recordsToHiddenAccount = state.accountIsImplicit,
                )
            }
        }

        if (!state.isMonthly) {
            NoteField(
                note = state.note,
                merchant = state.merchant,
                showMerchant = !state.isTransfer,
                onNoteChange = viewModel::setNote,
                onMerchantChange = viewModel::setMerchant,
            )
        } else {
            NoteField(
                note = state.note,
                merchant = "",
                showMerchant = false,
                onNoteChange = viewModel::setNote,
                onMerchantChange = {},
            )
        }

        Keypad(onKey = viewModel::pressKey, onBackspace = viewModel::backspace)

        SaveButton(
            enabled = state.canSave,
            amountCents = state.amountCents,
            mode = state.mode,
            onSave = { viewModel.save(onClose) },
        )
    }

    if (dateTimeDialogVisible) {
        DateTimeDialog(
            dateKey = state.dateKey,
            time = state.customTime,
            onPickDate = viewModel::setDate,
            onSetTime = viewModel::setTime,
            onUseNow = viewModel::useCurrentTime,
            onDismiss = { dateTimeDialogVisible = false },
        )
    }
}

@Composable
private fun EntryHeader(
    mode: EntryMode,
    dateKey: String,
    customTime: LocalTime?,
    onModeChange: (EntryMode) -> Unit,
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
        ModeSwitch(mode = mode, onModeChange = onModeChange)
        Spacer(Modifier.weight(1f))

        // Bordered so it reads as tappable; unpinned-looking chips get missed.
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
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape,
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

@Composable
private fun ModeSwitch(mode: EntryMode, onModeChange: (EntryMode) -> Unit) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        EntryMode.entries.forEach { candidate ->
            val selected = candidate == mode
            val accent = when (candidate) {
                EntryMode.EXPENSE -> LedgerTheme.colors.expense
                EntryMode.INCOME -> LedgerTheme.colors.income
                EntryMode.TRANSFER -> LedgerTheme.colors.transfer
                EntryMode.MONTHLY -> MaterialTheme.colorScheme.tertiary
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (selected) accent else Color.Transparent)
                    .clickable { onModeChange(candidate) }
                    .padding(horizontal = 11.dp, vertical = 7.dp),
            ) {
                Text(
                    text = candidate.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) Color.White
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AmountDisplay(amountInput: String, mode: EntryMode) {
    val ledger = LedgerTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.End,
    ) {
        if (mode == EntryMode.MONTHLY) {
            Text(
                text = "总金额",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = if (amountInput.isEmpty()) "0.00" else amountInput,
            style = MoneyTextStyles.Hero,
            color = when {
                amountInput.isEmpty() -> MaterialTheme.colorScheme.onSurfaceVariant
                mode == EntryMode.INCOME -> ledger.income
                else -> MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The everyday grid: the most-used categories plus a 「更多」 cell.
 *
 * 「更多」 is a grid cell rather than a pill under the grid, because it is one more
 * choice in the same list, not a different kind of thing. It carries the three-dot
 * glyph and the same shape as every category around it.
 */
/**
 * Nominal height of one category cell: 4dp padding + 38dp icon + 3dp gap + ~16dp label
 * + 4dp padding.
 *
 * The grid is sized in whole multiples of this rather than being given the full
 * remaining height. `weight(1f)` handed it 223dp on a 1080x2400 phone while a row cost
 * 78dp, so the third row was always sliced in half -- measured on a real device, the
 * 「公共交通」 label came out 11px tall and collided with the account chips below. A
 * half-row is not a scroll hint, it is a rendering defect, so the grid now occupies
 * exactly the rows that fit and the remainder of the slot stays empty.
 */
private val CategoryCellHeight = 65.dp
private val CategoryRowSpacing = 2.dp
private val CategoryGridPadding = 6.dp

@Composable
private fun CategoryGrid(
    state: EntryUiState,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var moreOpen by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            val pitch = CategoryCellHeight + CategoryRowSpacing
            val usable = (maxHeight - CategoryGridPadding * 2).coerceAtLeast(CategoryCellHeight)
            // Whole rows only, and never more than the list actually needs -- an
            // income ledger has six leaves and should not reserve three empty rows.
            val fits = floor(usable.value / pitch.value).toInt().coerceAtLeast(1)
            val needed = (state.visibleCategories.size + (if (state.hasHiddenCategories) 1 else 0) + 3) / 4
            val rows = minOf(fits, maxOf(needed, 1))

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CategoryCellHeight * rows + CategoryRowSpacing * (rows - 1) + CategoryGridPadding * 2),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = CategoryGridPadding),
                verticalArrangement = Arrangement.spacedBy(CategoryRowSpacing),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(state.visibleCategories, key = { it.id }) { category ->
                    CategoryCell(
                        category = category,
                        selected = category.id == state.selectedCategoryId,
                        onClick = { onSelect(category.id) },
                    )
                }
                if (state.hasHiddenCategories) {
                    item(key = "more") {
                        MoreCell(
                            label = "更多",
                            selected = false,
                            onClick = { moreOpen = true },
                        )
                    }
                }
            }
        }
    }

    if (moreOpen) {
        CategoryMoreDialog(
            groups = state.categoryGroups,
            selectedId = state.selectedCategoryId,
            onSelect = onSelect,
            onDismiss = { moreOpen = false },
        )
    }
}

/**
 * The full category list, floating above the keypad and grouped by 大类.
 *
 * A dialog rather than expanding the grid in place: expanding pushed the keypad and the
 * amount out of view exactly when the user was mid-entry, and it could not say which
 * 大类 an item belonged to.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryMoreDialog(
    groups: List<CategoryGroup>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 440.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "全部分类",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "✕",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    groups.forEach { group ->
                        item(key = group.key) {
                            CategoryGroupBlock(
                                group = group,
                                selectedId = selectedId,
                                onSelect = { id ->
                                    onSelect(id)
                                    onDismiss()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryGroupBlock(
    group: CategoryGroup,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
) {
    val accent = Color(group.colorArgb)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LedgerIconView(
                icon = LedgerIcon.forKey(group.iconKey),
                tint = accent,
                size = 16.dp,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = group.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            group.items.forEach { category ->
                CategoryChip(
                    category = category,
                    selected = category.id == selectedId,
                    onClick = { onSelect(category.id) },
                )
            }
        }
    }
}

/**
 * The 月付 form.
 *
 * The keypad amount is the plan's total, so only the schedule details are asked for
 * here. Everything the plan needs to start generating charges is collected in one
 * place, and the per-instalment figure updates live.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthlyPanel(
    state: EntryUiState,
    onNameChange: (String) -> Unit,
    onPeriodsChange: (String) -> Unit,
    onRepayDayChange: (String) -> Unit,
    onFeeChange: (String) -> Unit,
    onSelectCategory: (Long) -> Unit,
    onSelectAccount: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var moreOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Two rows of fields instead of three. The panel sits in a fixed-height slot
        // between the amount and the keypad, so every row of inputs it spends is a row
        // the category chips below lose -- and the chips were the part being cut off.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.planName,
                onValueChange = onNameChange,
                modifier = Modifier.weight(1.8f),
                singleLine = true,
                label = { Text("名称", style = MaterialTheme.typography.bodySmall) },
                textStyle = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = state.planPeriods,
                onValueChange = onPeriodsChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("期数", style = MaterialTheme.typography.bodySmall) },
                textStyle = MaterialTheme.typography.bodyMedium,
                isError = state.planPeriodsValue == null,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.planRepayDay,
                onValueChange = onRepayDayChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("还款日", style = MaterialTheme.typography.bodySmall) },
                textStyle = MaterialTheme.typography.bodyMedium,
                isError = state.planRepayDayValue == null,
            )
            OutlinedTextField(
                value = state.planFeeInput,
                onValueChange = onFeeChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                prefix = { Text("¥") },
                label = { Text("手续费", style = MaterialTheme.typography.bodySmall) },
                textStyle = MaterialTheme.typography.bodyMedium,
            )
        }

        if (state.planPerPeriodCents > 0L) {
            Text(
                text = "每期约 ${Money.formatWithSymbol(state.planPerPeriodCents)}，到期自动生成扣款",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.visibleCategories.isNotEmpty()) {
            Text(
                text = "关联类别",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Wrapped rather than a horizontally scrolling row: a scroll strip shows two
            // and a half chips, which reads as the list being broken off rather than as
            // something to swipe.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.visibleCategories.forEach { category ->
                    CategoryChip(
                        category = category,
                        selected = category.id == state.selectedCategoryId,
                        onClick = { onSelectCategory(category.id) },
                    )
                }
                // The twelve quick categories are what the grid shows, not what a monthly
                // plan is limited to: rent, tuition and insurance are recurring precisely
                // because they are not everyday spending, so they are exactly the ones the
                // quick set leaves out. The full tree is one tap away, same dialog as the
                // expense grid's 「更多」.
                if (state.categoryGroups.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { moreOpen = true }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    ) {
                        Text(
                            text = "更多",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        if (state.accounts.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.accounts.forEach { account ->
                    AccountChip(account, account.id == state.selectedAccountId) {
                        onSelectAccount(account.id)
                    }
                }
            }
        }
    }

    if (moreOpen) {
        CategoryMoreDialog(
            groups = state.categoryGroups,
            selectedId = state.selectedCategoryId,
            onSelect = onSelectCategory,
            onDismiss = { moreOpen = false },
        )
    }
}

@Composable
private fun CategoryChip(category: CategoryEntity, selected: Boolean, onClick: () -> Unit) {
    val accent = Color(category.colorArgb)
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) accent else accent.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = category.name,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Color.White else accent,
        )
    }
}

/**
 * Date and time for this entry, each opened as its own picker.
 *
 * Every stepper that used to live here is gone. A calendar cannot produce an invalid
 * date and shows the weekday, which is usually why the date is being changed at all;
 * a wheel reaches any minute in one gesture, which the ±1 hour and ±5 minute chips
 * never could. Both pickers float above this dialog, so opening one costs nothing.
 */
@Composable
private fun DateTimeDialog(
    dateKey: String,
    time: LocalTime?,
    onPickDate: (String) -> Unit,
    onSetTime: (Int, Int) -> Unit,
    onUseNow: () -> Unit,
    onDismiss: () -> Unit,
) {
    val shown = time ?: LocalTime.now()
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("记账时间") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PickerField(
                    label = "日期",
                    value = DateLabels.dayLabel(dateKey),
                    onClick = { pickingDate = true },
                )
                PickerField(
                    label = "时间",
                    value = if (time == null) {
                        "当前时间"
                    } else {
                        "%02d:%02d".format(shown.hour, shown.minute)
                    },
                    // An untouched time reads as a default, not as a decision.
                    muted = time == null,
                    onClick = { pickingTime = true },
                )
                if (time != null) {
                    TextButton(onClick = onUseNow) { Text("改回当前时间") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        },
    )

    if (pickingDate) {
        CalendarPickerDialog(
            initialDateKey = dateKey,
            onDismiss = { pickingDate = false },
            onPick = onPickDate,
        )
    }

    if (pickingTime) {
        WheelTimePickerDialog(
            initialTime = shown,
            onDismiss = { pickingTime = false },
            onPick = { hour, minute -> onSetTime(hour, minute) },
        )
    }
}

/** A label plus a tappable value, for opening a picker. */
@Composable
private fun PickerField(
    label: String,
    value: String,
    onClick: () -> Unit,
    muted: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(52.dp),
        )
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
 * The 「更多」 cell.
 *
 * Deliberately the same shape and size as a category cell: it is one more choice in the
 * same grid, and a differently styled control would read as navigation rather than as
 * the way to reach the rest of the list.
 */
@Composable
private fun MoreCell(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            LedgerIconView(
                icon = LedgerIcon.ELLIPSIS,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                size = 20.dp,
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (selected) accent else accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            LedgerIconView(
                icon = LedgerIcon.forKey(category.iconKey),
                tint = if (selected) Color.White else accent,
                size = 20.dp,
            )
        }
        Spacer(Modifier.height(3.dp))
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
    recordsToHiddenAccount: Boolean = false,
) {
    if (accounts.isEmpty()) {
        // A 累计模式 ledger records to an account it deliberately never shows, so there
        // is nothing to explain. A 预算模式 ledger whose accounts were all deleted has
        // nowhere to put the entry at all, and saying so beats a Save button that
        // silently refuses.
        if (!recordsToHiddenAccount) {
            Text(
                text = "当前账本还没有账户，保存前先到「账户」页添加一个。",
                style = MaterialTheme.typography.labelSmall,
                color = LedgerTheme.colors.expense,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        return
    }
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
                placeholder = { Text("交易对象", style = MaterialTheme.typography.bodySmall) },
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
    mode: EntryMode,
    onSave: () -> Unit,
) {
    val ledger = LedgerTheme.colors
    val accent = when (mode) {
        EntryMode.INCOME -> ledger.income
        EntryMode.TRANSFER -> ledger.transfer
        EntryMode.MONTHLY -> MaterialTheme.colorScheme.tertiary
        EntryMode.EXPENSE -> MaterialTheme.colorScheme.primary
    }
    val hint = when (mode) {
        EntryMode.TRANSFER -> "选择转出和转入账户"
        EntryMode.MONTHLY -> "填写名称、期数和还款日"
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
                val verb = when (mode) {
                    EntryMode.TRANSFER -> "转账"
                    EntryMode.MONTHLY -> "创建月付"
                    else -> "保存"
                }
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
