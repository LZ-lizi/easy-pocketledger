package com.pocketledger.feature.imports

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketledger.data.entity.AccountEntity
import com.pocketledger.data.entity.CategoryEntity
import com.pocketledger.data.entity.CategoryKind
import com.pocketledger.data.entity.TxnType
import com.pocketledger.domain.Money
import com.pocketledger.ui.theme.LedgerTheme
import com.pocketledger.ui.theme.MoneyTextStyles
import com.pocketledger.ui.util.DateLabels
import java.time.Instant
import java.time.ZoneId

/**
 * Imports WeChat / Alipay / generic CSV bills.
 *
 * Two steps on purpose. The file is parsed and shown first, because a bill file is
 * never quite what it looks like -- refunds, 不计收支 rows and rows already imported
 * from last month's export all have to be seen before they become ledger entries.
 */
@Composable
fun ImportScreen(
    viewModel: ImportViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // `*/*` rather than a CSV mime type: WeChat and Alipay hand their exports to the
    // picker as `application/octet-stream`, so a narrow filter hides the very files
    // this screen exists to read.
    val openDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "账单.csv"
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
        if (bytes == null) {
            viewModel.onFilePicked(name, ByteArray(0))
        } else {
            viewModel.onFilePicked(name, bytes)
        }
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
            ScreenHeader(title = "导入账单", onBack = onBack)
        }

        state.message?.let { message ->
            item(key = "message") {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable { viewModel.dismissMessage() }
                        .padding(12.dp),
                )
            }
        }

        when (state.stage) {
            ImportStage.PICK -> {
                item(key = "intro") { IntroCard() }
                item(key = "pick") {
                    ActionButton(
                        label = if (state.busy) "读取中…" else "选择账单文件",
                        enabled = !state.busy,
                        onClick = { openDocument.launch(arrayOf("*/*")) },
                    )
                }
                item(key = "batches-title") {
                    Text(
                        text = "导入记录",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                if (state.batches.isEmpty()) {
                    item(key = "batches-empty") {
                        Text(
                            text = "还没有导入过账单。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(state.batches.size, key = { state.batches[it].batch.id }) { index ->
                    val batch = state.batches[index]
                    BatchCard(batch = batch, onUndo = { viewModel.undoImport(batch.batch.id) })
                }
            }

            ImportStage.REVIEW -> {
                item(key = "file") { ReviewSummary(state) }

                item(key = "account") {
                    AccountPicker(
                        accounts = state.accounts,
                        selectedId = state.accountId,
                        onSelect = viewModel::setAccount,
                    )
                }

                item(key = "bulk") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { viewModel.setAllIncluded(true) }) { Text("全选") }
                        TextButton(onClick = { viewModel.setAllIncluded(false) }) { Text("全不选") }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = viewModel::reset) { Text("重新选择文件") }
                    }
                }

                items(state.rows.size, key = { state.rows[it].row.lineNumber }) { index ->
                    ImportRowCard(
                        view = state.rows[index],
                        categories = state.categories,
                        onToggle = { viewModel.toggleRow(index) },
                        onPickCategory = { id -> viewModel.setRowCategory(index, id) },
                    )
                }

                item(key = "commit") {
                    ActionButton(
                        label = if (state.busy) {
                            "导入中…"
                        } else {
                            "导入 ${state.selected.size} 条 · ${Money.formatWithSymbol(state.selectedTotalCents)}"
                        },
                        enabled = state.canImport,
                        onClick = viewModel::commitImport,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScreenHeader(title: String, onBack: () -> Unit) {
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
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun IntroCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "支持微信、支付宝导出的账单，以及本应用导出的 CSV。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "导入前会先列出每一条让你确认，重复的记录会自动勾掉；" +
                    "导入后可以整批撤销，也可以再导入一次更新的账单补齐新记录。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReviewSummary(state: ImportUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = state.format?.label ?: "CSV 文件",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            state.fileName?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val parts = buildList {
                add("共 ${state.rows.size} 条")
                if (state.duplicateCount > 0) add("${state.duplicateCount} 条已存在")
                if (state.skippedCount > 0) add("${state.skippedCount} 条已跳过")
            }
            Text(
                text = parts.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.skippedReasons.isNotEmpty()) {
                Text(
                    text = state.skippedReasons.joinToString("；"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.allDuplicates) {
                Text(
                    text = "这个文件里的记录都已经在账本里了。",
                    style = MaterialTheme.typography.labelSmall,
                    color = LedgerTheme.colors.income,
                )
            }
        }
    }
}

@Composable
private fun AccountPicker(
    accounts: List<AccountEntity>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "记入账户",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (accounts.isEmpty()) {
            Text(
                text = "当前账本没有可用账户，先在账户页建一个。",
                style = MaterialTheme.typography.bodySmall,
                color = LedgerTheme.colors.expense,
            )
            return@Column
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            accounts.forEach { account ->
                Chip(
                    label = account.name,
                    selected = account.id == selectedId,
                    onClick = { onSelect(account.id) },
                )
            }
        }
    }
}

@Composable
private fun ImportRowCard(
    view: ImportRowView,
    categories: List<CategoryEntity>,
    onToggle: () -> Unit,
    onPickCategory: (Long?) -> Unit,
) {
    val row = view.row
    val isIncome = row.type == TxnType.INCOME

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = view.include, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f)) {
                Text(
                    text = row.merchant ?: row.note ?: "未命名记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(DateLabels.dayLabel(row.dateKey))
                        row.time?.let { append(" %02d:%02d".format(it.hour, it.minute)) }
                        row.note?.takeIf { it.isNotBlank() && it != row.merchant }?.let {
                            append(" · ")
                            append(it)
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (view.alreadyImported) {
                    Text(
                        text = "已导入过",
                        style = MaterialTheme.typography.labelSmall,
                        color = LedgerTheme.colors.income,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = Money.formatSigned(row.amountCents, negative = !isIncome),
                    style = MoneyTextStyles.Small,
                    color = if (isIncome) LedgerTheme.colors.income else LedgerTheme.colors.expense,
                )
                CategoryDropTarget(
                    name = view.categoryName ?: "未分类",
                    categories = categories.filter { category ->
                        (category.kind == CategoryKind.INCOME) == isIncome
                    },
                    onPick = onPickCategory,
                )
            }
        }
    }
}

/**
 * The category currently assigned to a row, tappable to change it.
 *
 * A dropdown rather than a picker screen: the whole review list is about making a
 * dozen small decisions in a row, and each one has to cost a single tap.
 */
@Composable
private fun CategoryDropTarget(
    name: String,
    categories: List<CategoryEntity>,
    onPick: (Long?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Text(
            text = "$name ▾",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .clip(CircleShape)
                .clickable { expanded = true }
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("未分类") },
                onClick = {
                    onPick(null)
                    expanded = false
                },
            )
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.name) },
                    onClick = {
                        onPick(category.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun BatchCard(batch: ImportBatchView, onUndo: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = batch.sourceLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = buildString {
                        append("${batch.batch.txnCount} 条")
                        if (batch.batch.skippedCount > 0) append(" · 跳过 ${batch.batch.skippedCount} 条")
                        append(" · ")
                        append(DateLabels.dayLabel(dayKeyOf(batch.batch.importedAt)))
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onUndo) {
                Text("撤销", color = LedgerTheme.colors.expense)
            }
        }
    }
}

@Composable
private fun ActionButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(MaterialTheme.shapes.large)
            .background(
                if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = if (enabled) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                }
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

/** Import timestamps are shown as a plain day, matching the rest of the app. */
private fun dayKeyOf(millis: Long): String =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .toString()
