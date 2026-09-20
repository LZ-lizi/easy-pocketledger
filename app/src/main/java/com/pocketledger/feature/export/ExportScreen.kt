package com.pocketledger.feature.export

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketledger.domain.AppBackup
import com.pocketledger.ui.components.LedgerIcon
import com.pocketledger.ui.components.LedgerIconView
import com.pocketledger.ui.theme.LedgerTheme
import com.pocketledger.ui.util.DateLabels
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The backup's MIME type.
 *
 * A vendor type rather than `application/json`: the file is not JSON to any other app --
 * it is this app's backup, and giving it a type of its own means the system will not offer
 * it to a text editor or a JSON viewer, and will not append a `.json` extension over the
 * name this screen chose.
 */
private const val BACKUP_MIME = "application/vnd.pocketledger.backup"

/**
 * Exports the current ledger to CSV, and the whole app to a backup file.
 *
 * Both go through the system file picker rather than a fixed path, so the user decides
 * where they land -- which is also what makes them reachable by whatever they use to keep
 * copies.
 */
@Composable
fun ExportScreen(
    viewModel: ExportViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val createDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        val pending = state.pending
        if (uri == null || pending == null) {
            // A cancelled picker is not a failure worth reporting.
            viewModel.onWriteFinished(success = uri == null)
            return@rememberLauncherForActivityResult
        }
        val written = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(pending.content.toByteArray(Charsets.UTF_8))
            } ?: error("no output stream")
        }.isSuccess
        viewModel.onWriteFinished(written)
    }

    val createBackupDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(BACKUP_MIME),
    ) { uri ->
        val pending = state.pendingBackup
        if (uri == null || pending == null) {
            viewModel.onBackupWriteFinished(success = uri == null)
            return@rememberLauncherForActivityResult
        }
        val written = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(pending.content.toByteArray(Charsets.UTF_8))
            } ?: error("no output stream")
        }.isSuccess
        viewModel.onBackupWriteFinished(written)
    }

    // `*/*` rather than the backup's own type: a file that has been through a chat app, a
    // cloud drive or an SD card comes back with whatever type *that* app guessed, and a
    // filter here would hide the very file the user is trying to restore.
    val openBackupDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.readBytes().toString(Charsets.UTF_8)
                    }
                }.getOrNull()
            }
            if (text == null) viewModel.onRestoreReadFailed() else viewModel.stageRestore(text)
        }
    }

    // The content is built before the picker opens, so this only has to hand over a
    // file name once there is something real to write.
    LaunchedEffect(state.pending) {
        state.pending?.let { createDocument.launch(it.fileName) }
    }

    LaunchedEffect(state.pendingBackup) {
        state.pendingBackup?.let { createBackupDocument.launch(it.fileName) }
    }

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
                    text = "导出与备份",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        item(key = "csv-title") {
            SectionTitle(
                title = "导出账单",
                detail = "导出所选账本为 CSV 文件，可用表格软件打开；" +
                    "选「全部账本」时会多一列「账本」。",
            )
        }

        item(key = "csv-target") {
            TargetField(
                label = state.targetLabel,
                enabled = state.pending == null,
                onClick = viewModel::openTargetPicker,
            )
        }

        if (state.loaded && !state.hasData) {
            // A note, not an early exit: an empty ledger still needs the backup section
            // below -- "I have nothing to export" is not "I have nothing to back up".
            item(key = "empty") {
                Text(
                    text = "这个选择下还没有记录可导出。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "all") {
            ExportOption(
                scope = ExportScope.ALL,
                count = state.totalCount,
                enabled = state.pending == null && state.totalCount > 0,
                onClick = { viewModel.prepare(ExportScope.ALL) },
            )
        }

        item(key = "month") {
            ExportOption(
                scope = ExportScope.THIS_MONTH,
                count = state.monthCount,
                enabled = state.pending == null && state.monthCount > 0,
                onClick = { viewModel.prepare(ExportScope.THIS_MONTH) },
            )
        }

        state.lastResult?.let { message ->
            item(key = "result") {
                ResultLine(
                    message = message,
                    success = message.startsWith("已导出"),
                    onClick = viewModel::dismissResult,
                )
            }
        }

        item(key = "backup-title") {
            SectionTitle(
                title = "应用备份",
                detail = "把记账、账本、账户、余额、类别和设置「全部」存成一个文件。" +
                    "备份不可选范围，恢复时用这个文件覆盖当前全部数据。",
            )
        }

        item(key = "backup-export") {
            ActionCard(
                title = "导出备份文件",
                detail = "保存为 .${AppBackup.EXTENSION} 文件，可以拷到别的设备或云盘",
                enabled = state.pendingBackup == null && !state.restoring,
                onClick = viewModel::prepareBackup,
            )
        }

        item(key = "backup-restore") {
            ActionCard(
                title = "从备份文件恢复",
                detail = "选择之前导出的备份，用它覆盖当前的全部数据",
                enabled = !state.restoring && state.pendingBackup == null,
                onClick = { openBackupDocument.launch(arrayOf("*/*")) },
            )
        }

        state.backupResult?.let { message ->
            item(key = "backup-result") {
                ResultLine(
                    message = message,
                    success = message.startsWith("备份已导出"),
                    onClick = viewModel::dismissBackupResult,
                )
            }
        }

        state.restoreResult?.let { message ->
            item(key = "restore-result") {
                ResultLine(
                    message = message,
                    success = message.startsWith("已恢复"),
                    onClick = viewModel::dismissRestoreResult,
                )
            }
        }

        if (state.restoring) {
            item(key = "restoring") {
                Text(
                    text = "正在恢复，请不要退出…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    state.pendingRestore?.let { file ->
        RestoreConfirmDialog(
            file = file,
            onConfirm = viewModel::confirmRestore,
            onDismiss = viewModel::dismissRestore,
        )
    }

    if (state.targetPickerVisible) {
        TargetPickerDialog(
            ledgers = state.ledgers,
            selected = state.target,
            onAll = viewModel::chooseAllLedgers,
            onOne = viewModel::chooseLedger,
            onDismiss = viewModel::dismissTargetPicker,
        )
    }
}

/**
 * Which ledger the CSV covers.
 *
 * A field that opens a list rather than a row of chips: two ledgers fit in chips, ten do
 * not, and the chosen one has to stay readable at any number. Same shape as the ledger
 * picker on the import screen, because it answers the same question.
 */
@Composable
private fun TargetField(label: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "▾",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TargetPickerDialog(
    ledgers: List<LedgerChoice>,
    selected: CsvTarget,
    onAll: () -> Unit,
    onOne: (LedgerChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导出哪个账本") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                PickerRow(
                    label = "全部账本",
                    detail = "每个账本各占一列「账本」区分",
                    selected = selected is CsvTarget.AllLedgers,
                    onClick = onAll,
                )
                ledgers.forEach { choice ->
                    PickerRow(
                        label = choice.name,
                        detail = null,
                        selected = selected is CsvTarget.One && selected.ledgerId == choice.id,
                        onClick = { onOne(choice) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun PickerRow(
    label: String,
    detail: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            detail?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (selected) {
            LedgerIconView(
                icon = LedgerIcon.CHEVRON_RIGHT,
                tint = MaterialTheme.colorScheme.primary,
                size = 18.dp,
            )
        }
    }
}

/**
 * The last gate before a restore.
 *
 * Deliberately not [ConfirmDeleteDialog]: that one promises 「删除后无法恢复」, and here the
 * opposite is true, so reusing it would print a sentence that contradicts the button under
 * it. What this dialog has to make plain is that the file *replaces* everything -- the
 * live data is what gets discarded, not the file.
 */
@Composable
private fun RestoreConfirmDialog(
    file: AppBackup.File,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("恢复备份") },
        text = {
            Column {
                Text(
                    text = "将用这个备份覆盖当前全部数据：" +
                        "${file.tables.size} 张表、${file.rowCount} 条记录。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "备份时间：${DateLabels.dayLabel(exportDateKey(file.createdAt))}" +
                        "　备份版本：${file.appVersion.ifBlank { "未知" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "当前的数据会被替换掉，且无法撤销。建议先导出一份现在的备份。",
                    style = MaterialTheme.typography.bodySmall,
                    color = LedgerTheme.colors.expense,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("覆盖并恢复", color = LedgerTheme.colors.expense)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

private fun exportDateKey(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().toString()

@Composable
private fun SectionTitle(title: String, detail: String) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ResultLine(message: String, success: Boolean, onClick: () -> Unit) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = if (success) LedgerTheme.colors.income else LedgerTheme.colors.expense,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(top = 4.dp),
    )
}

@Composable
private fun ActionCard(
    title: String,
    detail: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ExportOption(
    scope: ExportScope,
    count: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = scope.label,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "${scope.detail} · 共 $count 条",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
