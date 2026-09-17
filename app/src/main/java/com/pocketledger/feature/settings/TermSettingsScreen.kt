package com.pocketledger.feature.settings

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketledger.data.entity.TermEntity
import com.pocketledger.domain.DateKeys
import com.pocketledger.ui.theme.LedgerTheme
import java.time.LocalDate

/**
 * Named date ranges behind the statistics page's 学期 view.
 *
 * A term is a plain start/end pair: the app has no way to know when a given school
 * starts, and a wrong guess would silently mislabel a whole semester of spending.
 */
@Composable
fun TermSettingsScreen(
    viewModel: TermSettingsViewModel,
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
                    text = "学期设置",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable { viewModel.createTerm() }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = "添加",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        item(key = "explain") {
            Text(
                text = "统计页的「学期」用这里的日期区间算总账。" +
                    "比如填 2026-09-01 到 2027-01-15，就能看到整个秋季学期花了多少。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.terms.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = "还没有学期，点右上角「添加」建一个",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }

        items(state.terms, key = { it.term.id }) { row ->
            TermCard(row = row, onClick = { viewModel.editTerm(row.term) })
        }
    }

    if (state.editorVisible) {
        TermEditorDialog(
            existing = state.editorTarget,
            suggestedName = state.suggestedName,
            onDismiss = viewModel::dismissEditor,
            onSave = viewModel::saveTerm,
            onDelete = viewModel::deleteTerm,
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
private fun TermCard(row: TermRow, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = row.term.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${DateKeys.parseDateKey(row.term.startDateKey)} 起 · " +
                        "共 ${row.dayCount} 天",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = row.term.endDateKey,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TermEditorDialog(
    existing: TermEntity?,
    suggestedName: String,
    onDismiss: () -> Unit,
    onSave: (TermEntity) -> Unit,
    onDelete: (TermEntity) -> Unit,
) {
    val today = remember { LocalDate.now() }
    var name by remember { mutableStateOf(existing?.name ?: suggestedName) }
    var startKey by remember { mutableStateOf(existing?.startDateKey ?: today.toString()) }
    var endKey by remember {
        mutableStateOf(
            existing?.endDateKey ?: TermSettingsViewModel.defaultEndDate(today)
        )
    }

    val rangeValid = TermSettingsViewModel.isValidRange(startKey, endKey)
    val canSave = name.isNotBlank() && rangeValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "添加学期" else "编辑学期") },
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

                OutlinedTextField(
                    value = startKey,
                    onValueChange = { startKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("开始日期") },
                    placeholder = { Text("2026-09-01") },
                    supportingText = {
                        Text("格式 YYYY-MM-DD", style = MaterialTheme.typography.labelSmall)
                    },
                    isError = runCatching { LocalDate.parse(startKey) }.isFailure,
                )
                QuickChip("今天开始") { startKey = today.toString() }

                OutlinedTextField(
                    value = endKey,
                    onValueChange = { endKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("结束日期") },
                    placeholder = { Text("2027-01-15") },
                    isError = runCatching { LocalDate.parse(endKey) }.isFailure || !rangeValid,
                    supportingText = {
                        if (!rangeValid) {
                            Text(
                                text = "结束日期不能早于开始日期",
                                style = MaterialTheme.typography.labelSmall,
                                color = LedgerTheme.colors.expense,
                            )
                        }
                    },
                )
                QuickChip("默认 4 个月后") { endKey = TermSettingsViewModel.defaultEndDate(today) }

                if (existing != null) {
                    TextButton(onClick = { onDelete(existing) }) {
                        Text("删除这个学期", color = LedgerTheme.colors.expense)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    onSave(
                        TermEntity(
                            id = existing?.id ?: 0L,
                            name = name.trim(),
                            startDateKey = startKey.trim(),
                            endDateKey = endKey.trim(),
                            isActive = existing?.isActive ?: false,
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
private fun QuickChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
