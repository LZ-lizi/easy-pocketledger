package com.pocketledger.feature.installments

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketledger.data.entity.AccountEntity
import com.pocketledger.data.entity.CategoryEntity
import com.pocketledger.data.entity.InstallmentKind
import com.pocketledger.data.entity.InstallmentPlanEntity
import com.pocketledger.domain.DateKeys
import com.pocketledger.domain.InstallmentSchedule
import com.pocketledger.domain.Money
import com.pocketledger.ui.theme.LedgerTheme
import com.pocketledger.ui.theme.MoneyTextStyles
import java.time.LocalDate

/**
 * 月付 / 白条 plans: what is owed, how far along, and when the next charge lands.
 *
 * Progress is read from the transactions actually posted, so a plan that has not
 * generated a charge yet shows it rather than agreeing with the calendar.
 */
@Composable
fun InstallmentSettingsScreen(
    viewModel: InstallmentSettingsViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackChip(onBack)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "月付/白条",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable { viewModel.create() }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = "新建",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        item(key = "hint") {
            Text(
                text = "到期当天会自动在本账本生成一笔扣款，同一期只生成一次。" +
                    "打开 App 时补生成，所以停用几天再打开也不会漏。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.plans.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = "还没有计划，点右上角「新建」加一个",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }

        items(state.plans, key = { it.plan.id }) { row ->
            PlanCard(row = row, onClick = { viewModel.edit(row.plan) })
        }
    }

    if (state.editorVisible) {
        InstallmentEditorDialog(
            existing = state.editorTarget,
            accounts = state.accounts,
            categories = state.categories,
            onDismiss = viewModel::dismissEditor,
            onSave = viewModel::save,
            onSetActive = viewModel::setActive,
        )
    }
}

@Composable
private fun BackChip(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "‹",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlanCard(row: InstallmentPlanRow, onClick: () -> Unit) {
    val plan = row.plan
    val accent = if (plan.isActive) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = plan.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = buildString {
                        append(installmentKindLabel(plan.kind))
                        if (!plan.isActive) append(" · 已终止")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = Money.formatWithSymbol(row.remainingCents),
                    style = MoneyTextStyles.Large,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "未还",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "共 ${Money.formatCompact(plan.totalAmountCents)}",
                    style = MoneyTextStyles.Small,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                if (row.progress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(row.progress)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(accent)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row {
                Text(
                    text = "已还 ${row.paidPeriods}/${plan.periodCount} 期",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = row.nextDueDateKey?.let { "下次 ${DateKeys.parseDateKey(it)}" }
                        ?: row.finalDueDateKey?.let { "已还完" }
                        ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (plan.isActive) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "每期 ${Money.formatWithSymbol(InstallmentSchedule.amountForPeriod(plan.totalAmountCents, plan.periodCount, 1))}" +
                        " · 每月 ${plan.repayDay} 日",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InstallmentEditorDialog(
    existing: InstallmentPlanEntity?,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (InstallmentPlanEntity) -> Unit,
    onSetActive: (InstallmentPlanEntity, Boolean) -> Unit,
) {
    val today = remember { LocalDate.now() }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var kind by remember { mutableStateOf(existing?.kind ?: InstallmentKind.MONTHLY) }
    var totalInput by remember {
        mutableStateOf(
            existing?.totalAmountCents?.let { Money.formatCompact(it) }.orEmpty()
        )
    }
    var periodCountInput by remember {
        mutableStateOf(existing?.periodCount?.toString() ?: "1")
    }
    var repayDayInput by remember {
        mutableStateOf(existing?.repayDay?.toString() ?: today.dayOfMonth.toString())
    }
    var startDateInput by remember {
        mutableStateOf(existing?.startDateKey ?: today.toString())
    }
    var accountId by remember { mutableStateOf(existing?.accountId) }
    var categoryId by remember { mutableStateOf(existing?.categoryId) }

    val totalCents = Money.parseYuanToCents(totalInput)
    val periodCount = periodCountInput.toIntOrNull()
    val repayDay = repayDayInput.toIntOrNull()
    val startValid = runCatching { LocalDate.parse(startDateInput) }.isSuccess

    val valid = name.isNotBlank() &&
        totalCents != null && totalCents > 0L &&
        periodCount != null && periodCount in 1..120 &&
        repayDay != null && repayDay in 1..31 &&
        startValid

    // Shown live so the per-period figure is never a surprise after saving.
    val perPeriod = if (totalCents != null && periodCount != null && periodCount > 0) {
        InstallmentSchedule.amountForPeriod(totalCents, periodCount, 1)
    } else {
        0L
    }
    val finalDue = if (periodCount != null && repayDay != null && startValid) {
        InstallmentSchedule.finalDueDate(startDateInput, repayDay, periodCount)?.toString()
    } else {
        null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "新建计划" else "编辑计划") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("名称") },
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KindChip("月付", kind == InstallmentKind.MONTHLY) {
                        kind = InstallmentKind.MONTHLY
                    }
                    KindChip("白条", kind == InstallmentKind.CREDIT) {
                        kind = InstallmentKind.CREDIT
                    }
                }

                OutlinedTextField(
                    value = totalInput,
                    onValueChange = { totalInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    prefix = { Text("¥") },
                    label = { Text("总金额") },
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = periodCountInput,
                        onValueChange = { periodCountInput = it.filter(Char::isDigit).take(3) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("期数") },
                        isError = periodCount == null || periodCount !in 1..120,
                    )
                    OutlinedTextField(
                        value = repayDayInput,
                        onValueChange = { repayDayInput = it.filter(Char::isDigit).take(2) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("每月几号") },
                        isError = repayDay == null || repayDay !in 1..31,
                    )
                }

                if (perPeriod > 0L) {
                    Text(
                        text = "每期约 ${Money.formatWithSymbol(perPeriod)}" +
                            (finalDue?.let { "，最后一期在 $it" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                OutlinedTextField(
                    value = startDateInput,
                    onValueChange = { startDateInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("起始日期") },
                    placeholder = { Text("2026-09-01") },
                    isError = !startValid,
                    supportingText = {
                        Text("格式 YYYY-MM-DD", style = MaterialTheme.typography.labelSmall)
                    },
                )

                if (accounts.isNotEmpty()) {
                    Text(
                        text = "关联账户",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        accounts.forEach { account ->
                            SelectChip(account.name, accountId == account.id) {
                                accountId = account.id
                            }
                        }
                    }
                }

                if (categories.isNotEmpty()) {
                    Text(
                        text = "关联类别",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SelectChip("不指定", categoryId == null) { categoryId = null }
                        categories.forEach { category ->
                            SelectChip(category.name, categoryId == category.id) {
                                categoryId = category.id
                            }
                        }
                    }
                }

                if (existing != null) {
                    TextButton(
                        onClick = { onSetActive(existing, !existing.isActive) },
                    ) {
                        Text(
                            text = if (existing.isActive) "终止这个计划" else "恢复这个计划",
                            color = LedgerTheme.colors.expense,
                        )
                    }
                    Text(
                        text = "终止只影响之后的扣款，已经记下的账目不动。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        InstallmentPlanEntity(
                            id = existing?.id ?: 0L,
                            ledgerId = existing?.ledgerId ?: 0L,
                            name = name.trim(),
                            kind = kind,
                            totalAmountCents = totalCents ?: 0L,
                            periodCount = periodCount ?: 1,
                            perPeriodCents = perPeriod,
                            repayDay = repayDay ?: 1,
                            startDateKey = startDateInput.trim(),
                            accountId = accountId,
                            categoryId = categoryId,
                            isActive = existing?.isActive ?: true,
                            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                        )
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun KindChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainer
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit) {
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
        )
    }
}
