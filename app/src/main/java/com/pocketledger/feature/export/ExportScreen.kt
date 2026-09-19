package com.pocketledger.feature.export

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketledger.ui.theme.LedgerTheme

/**
 * Exports the current ledger to CSV.
 *
 * The file is written through the system file picker rather than to a fixed path, so
 * the user decides where it goes -- which is also what makes it land somewhere a
 * backup can reach.
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

    // The content is built before the picker opens, so this only has to hand over a
    // file name once there is something real to write.
    LaunchedEffect(state.pending) {
        state.pending?.let { createDocument.launch(it.fileName) }
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
                    text = "导出数据",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        item(key = "hint") {
            Text(
                text = "导出当前账本（${state.ledgerName.ifBlank { "账本" }}）为 CSV 文件。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.loaded && !state.hasData) {
            item(key = "empty") {
                Text(
                    text = "这个账本还没有记录可导出。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
            return@LazyColumn
        }

        item(key = "all") {
            ExportOption(
                scope = ExportScope.ALL,
                count = state.totalCount,
                enabled = state.pending == null,
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
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (message.startsWith("已导出")) {
                        LedgerTheme.colors.income
                    } else {
                        LedgerTheme.colors.expense
                    },
                    modifier = Modifier
                        .clickable { viewModel.dismissResult() }
                        .padding(top = 8.dp),
                )
            }
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
