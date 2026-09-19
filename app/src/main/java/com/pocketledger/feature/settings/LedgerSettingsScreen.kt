package com.pocketledger.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketledger.data.entity.LedgerEntity
import com.pocketledger.data.entity.LedgerType
import com.pocketledger.ui.components.ConfirmDeleteDialog
import com.pocketledger.ui.theme.LedgerTheme

/**
 * Ledger list: switch, rename, archive, delete, create.
 *
 * Everything else in the app is scoped to whichever ledger is selected here, so this
 * is the one screen where that choice is made.
 */
@Composable
fun LedgerSettingsScreen(
    viewModel: LedgerSettingsViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenBudget: (Long) -> Unit,
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
                    text = "账本管理",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable { viewModel.createLedger() }
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
                text = "在这里新建、删除、更改账本设置",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        items(state.ledgers, key = { it.id }) { ledger ->
            LedgerCard(
                ledger = ledger,
                selected = ledger.id == state.selectedId,
                onEdit = { viewModel.edit(ledger) },
                onOpenBudget = onOpenBudget,
            )
        }
    }

    if (state.editorVisible) {
        LedgerEditorDialog(
            existing = state.editorTarget,
            canDelete = state.ledgers.size > 1,
            onDismiss = viewModel::dismissEditor,
            onSave = viewModel::save,
            onArchive = viewModel::toggleArchive,
            onDelete = viewModel::deleteLedger,
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

/**
 * One ledger.
 *
 * Tapping opens the editor rather than switching to it: managing books and moving the
 * app's focus are different intentions, and the earlier version conflated them so a
 * stray tap silently changed every other screen.
 */
@Composable
private fun LedgerCard(
    ledger: LedgerEntity,
    selected: Boolean,
    onEdit: () -> Unit,
    onOpenBudget: (Long) -> Unit,
) {
    val accent = Color(ledger.colorArgb)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (ledger.isArchived) 0.55f else 1f)
            .clickable(onClick = onEdit),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                accent.copy(alpha = 0.14f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) accent else MaterialTheme.colorScheme.outlineVariant
                    )
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = ledger.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(ledgerTypeLabel(ledger.type))
                        if (ledger.isArchived) append(" · 已归档")
                        if (selected) append(" · 当前")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // A 累计模式 ledger has no budgets by design, so it is not offered one.
            if (ledger.type == LedgerType.BUDGET) {
                Text(
                    text = "预算",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { onOpenBudget(ledger.id) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Text(
                text = "编辑",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable(onClick = onEdit)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun LedgerEditorDialog(
    existing: LedgerEntity?,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onSave: (LedgerEntity) -> Unit,
    onArchive: (LedgerEntity) -> Unit,
    onDelete: (LedgerEntity) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var type by remember { mutableStateOf(existing?.type ?: LedgerType.BUDGET) }
    var confirmingDelete by remember { mutableStateOf(false) }
    // The mode decides whether accounts and budgets exist at all, so changing it on a
    // ledger that already holds data would silently strand that data. Locked after creation.
    val typeLocked = existing != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "新建账本" else "编辑账本") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
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

                Text(
                    text = "记账方式",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TypeChoice("预算模式", type == LedgerType.BUDGET, !typeLocked) {
                        type = LedgerType.BUDGET
                    }
                    TypeChoice("累计模式", type == LedgerType.ACCUMULATE, !typeLocked) {
                        type = LedgerType.ACCUMULATE
                    }
                }
                if (typeLocked) {
                    Text(
                        text = "账本创建后不能再改记账方式，否则已有数据会失去归属。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (existing != null) {
                    // Archiving and deleting side by side, deliberately the same shape:
                    // one puts the ledger away and one destroys it, and the pair only
                    // reads correctly when neither is hidden behind a menu.
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { onArchive(existing) },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, LedgerTheme.colors.expense.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = LedgerTheme.colors.expense,
                            ),
                        ) {
                            Text(
                                text = if (existing.isArchived) "取消归档" else "归档",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                        OutlinedButton(
                            onClick = { confirmingDelete = true },
                            modifier = Modifier.weight(1f),
                            enabled = canDelete,
                            border = BorderStroke(1.dp, LedgerTheme.colors.expense.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = LedgerTheme.colors.expense,
                            ),
                        ) {
                            Text(text = "删除", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    if (!canDelete) {
                        Text(
                            text = "至少要保留一个账本。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        LedgerEntity(
                            id = existing?.id ?: 0L,
                            name = name.trim(),
                            type = type,
                            iconKey = existing?.iconKey ?: "book",
                            colorArgb = existing?.colorArgb ?: 0xFF2F6BFF.toInt(),
                            sortOrder = existing?.sortOrder ?: 0,
                            isArchived = existing?.isArchived ?: false,
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

    if (confirmingDelete && existing != null) {
        ConfirmDeleteDialog(
            title = "删除账本",
            target = "「${existing.name}」和它下面的所有记账条目都会被删除。",
            onConfirm = { onDelete(existing) },
            onDismiss = { confirmingDelete = false },
        )
    }
}

@Composable
private fun TypeChoice(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .alpha(if (enabled) 1f else 0.5f)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainer
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
