package com.pocketledger.feature.budget

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketledger.data.entity.CategoryEntity
import com.pocketledger.domain.BudgetAlerts
import com.pocketledger.domain.Money
import com.pocketledger.ui.components.LedgerIcon
import com.pocketledger.ui.components.LedgerIconView
import com.pocketledger.ui.theme.LedgerTheme
import com.pocketledger.ui.theme.MoneyTextStyles

/**
 * Budgets at three levels: the month overall, each 大类, and any 小类 worth watching.
 *
 * Every 大类 is listed whether or not it has a cap, so the screen doubles as a
 * picture of where the money went this month -- an empty list would answer neither
 * "what is my budget" nor "how am I doing".
 */
@Composable
fun BudgetSettingsScreen(
    viewModel: BudgetViewModel,
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
                    text = "预算",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                StepChip("‹", viewModel::previousMonth)
                Text(
                    text = state.monthLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                StepChip("›", viewModel::nextMonth)
            }
        }

        state.total?.let { total ->
            item(key = "total") {
                TotalBudgetCard(row = total, onClick = { viewModel.edit(total) })
            }
        }

        item(key = "main-title") { SectionLabel("各大类") }

        items(state.mainRows, key = { "main-${it.categoryId}" }) { row ->
            BudgetRowCard(row = row, onClick = { viewModel.edit(row) })
        }

        item(key = "leaf-title") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionLabel("单独设限的类别")
                Spacer(Modifier.weight(1f))
                if (state.leafOptions.isNotEmpty()) {
                    AddLeafButton(options = state.leafOptions, onPick = viewModel::addLeafBudget)
                }
            }
        }

        if (state.leafRows.isEmpty()) {
            item(key = "leaf-empty") {
                Text(
                    text = "没有单独设限的类别。大类预算已覆盖的，不必再设一层。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(state.leafRows, key = { "leaf-${it.categoryId}" }) { row ->
            BudgetRowCard(row = row, onClick = { viewModel.edit(row) })
        }
    }

    // Captured locally: `state` is a delegated property, so Kotlin cannot smart-cast
    // its nullable field at the call site.
    val editorTarget = state.editorTarget
    if (state.editorVisible && editorTarget != null) {
        BudgetEditorDialog(
            row = editorTarget,
            onDismiss = viewModel::dismissEditor,
            onSave = viewModel::save,
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
private fun StepChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
    )
}

@Composable
private fun AddLeafButton(options: List<CategoryEntity>, onPick: (CategoryEntity) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Text(
            text = "添加类别预算",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(CircleShape)
                .clickable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
        if (expanded) {
            AlertDialog(
                onDismissRequest = { expanded = false },
                title = { Text("选择类别") },
                text = {
                    Column(
                        modifier = Modifier
                            .height(360.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        options.forEach { option ->
                            Text(
                                text = option.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expanded = false
                                        onPick(option)
                                    }
                                    .padding(vertical = 12.dp),
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { expanded = false }) { Text("取消") }
                },
            )
        }
    }
}

@Composable
private fun TotalBudgetCard(row: BudgetRow, onClick: () -> Unit) {
    val progress = row.progress
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = "本月总预算",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (row.isSet) {
                    Money.formatWithSymbol(row.spentCents) + " / " + Money.format(row.limitCents)
                } else {
                    "已花 " + Money.formatWithSymbol(row.spentCents)
                },
                style = MoneyTextStyles.Large,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = when {
                    !row.isSet -> "点这里设一个月度上限（与明细页生活费同步）"
                    progress.isOver -> "已超 " + Money.formatWithSymbol(-progress.remainingCents)
                    else -> "还剩 " + Money.formatWithSymbol(progress.remainingCents)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (progress.isOver) {
                    LedgerTheme.colors.expense
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (row.isSet) {
                Spacer(Modifier.height(12.dp))
                ProgressBar(progress.ratio, progress.isOver, MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun BudgetRowCard(row: BudgetRow, onClick: () -> Unit) {
    val accent = Color(row.colorArgb)
    val progress = row.progress
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LedgerIconView(
                    icon = LedgerIcon.forKey(row.iconKey),
                    tint = accent,
                    size = 20.dp,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = row.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (row.isSet && progress.level >= BudgetAlerts.WARNING_LEVEL) {
                    Text(
                        text = if (progress.isOver) "超支" else "接近上限",
                        style = MaterialTheme.typography.labelSmall,
                        color = LedgerTheme.colors.expense,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = if (row.isSet) {
                        Money.format(row.spentCents) + " / " + Money.format(row.limitCents)
                    } else {
                        Money.format(row.spentCents)
                    },
                    style = MoneyTextStyles.Small,
                    color = if (row.isSet && progress.isOver) {
                        LedgerTheme.colors.expense
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (row.isSet) {
                Spacer(Modifier.height(8.dp))
                ProgressBar(progress.ratio, progress.isOver, accent)
            }
        }
    }
}

@Composable
private fun ProgressBar(ratio: Float, isOver: Boolean, accent: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(7.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        if (ratio > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(ratio)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(if (isOver) LedgerTheme.colors.expense else accent)
            )
        }
    }
}

@Composable
private fun BudgetEditorDialog(
    row: BudgetRow,
    onDismiss: () -> Unit,
    onSave: (Long, Long) -> Unit,
) {
    var text by remember {
        mutableStateOf(row.limitCents.takeIf { it > 0L }?.let { Money.formatCompact(it) }.orEmpty())
    }
    val parsed = if (text.isBlank()) 0L else Money.parseYuanToCents(text)
    val valid = parsed != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (row.categoryId == 0L) "本月总预算" else "${row.name} 预算") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "本月已花 " + Money.formatWithSymbol(row.spentCents),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    prefix = { Text("¥") },
                    label = { Text("上限") },
                    isError = !valid,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(500L, 1000L, 2000L).forEach { yuan ->
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .clickable {
                                    val current = Money.parseYuanToCents(text) ?: 0L
                                    text = Money.formatCompact(current + yuan * 100L)
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = "+$yuan",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                Text(
                    text = if (row.categoryId == 0L) {
                        "留空或填 0 表示取消。这个数字和明细页生活费卡片上的额度是同一个，" +
                            "在任意一边改都会同步。"
                    } else {
                        "留空或填 0 表示取消这个预算。"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onSave(row.categoryId, parsed ?: 0L) },
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
