package com.pocketledger.feature.detail

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketledger.data.entity.TxnType
import com.pocketledger.domain.Money
import com.pocketledger.ui.components.LedgerIcon
import com.pocketledger.ui.components.LedgerIconView
import com.pocketledger.ui.theme.LedgerTheme
import com.pocketledger.ui.theme.MoneyTextStyles

/**
 * Read-only view of one entry.
 *
 * Opening a row used to jump straight into the edit form, which made an accidental
 * tap feel like it had changed something and put the destructive controls one
 * gesture away from the list. Viewing is now the default and editing is an explicit
 * second step.
 */
@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
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
                    text = "记录详情",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        if (state.missing) {
            item(key = "missing") {
                Text(
                    text = "这笔记录已经不存在了",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 32.dp),
                )
            }
            return@LazyColumn
        }

        item(key = "amount") { AmountCard(state) }

        item(key = "fields") { FieldsCard(state) }

        item(key = "edit") {
            val accent = when (state.type) {
                TxnType.INCOME -> LedgerTheme.colors.income
                TxnType.TRANSFER -> LedgerTheme.colors.transfer
                TxnType.EXPENSE -> MaterialTheme.colorScheme.primary
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(accent)
                    .clickable { onEdit(state.id) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "修改",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun AmountCard(state: DetailUiState) {
    val ledger = LedgerTheme.colors
    val accent = when (state.type) {
        TxnType.INCOME -> ledger.income
        TxnType.TRANSFER -> ledger.transfer
        TxnType.EXPENSE -> ledger.expense
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                LedgerIconView(
                    icon = LedgerIcon.forKey(state.iconKey),
                    tint = accent,
                    size = 26.dp,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = state.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = when (state.type) {
                    TxnType.INCOME -> Money.formatSigned(state.amountCents, negative = false)
                    TxnType.EXPENSE -> Money.formatSigned(state.amountCents, negative = true)
                    TxnType.TRANSFER -> Money.formatWithSymbol(state.amountCents)
                },
                style = MoneyTextStyles.Hero,
                color = if (state.type == TxnType.EXPENSE) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    accent
                },
            )
            if (state.isExcludedFromStats) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "不计收支",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FieldsCard(state: DetailUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            DetailRow("时间", state.timeLabel)
            if (state.type == TxnType.TRANSFER) {
                DetailRow("转出", state.accountName)
                DetailRow("转入", state.toAccountName.orEmpty())
                state.feeCents?.takeIf { it > 0L }?.let {
                    DetailRow("手续费", Money.formatWithSymbol(it))
                }
            } else {
                DetailRow("分类", state.categoryName.orEmpty())
                DetailRow("账户", state.accountName)
                state.merchant?.takeIf { it.isNotBlank() }?.let { DetailRow("商家", it) }
            }
            state.note?.takeIf { it.isNotBlank() }?.let { DetailRow("备注", it) }
            if (state.type != TxnType.TRANSFER) {
                DetailRow("来源", state.sourceLabel)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        Text(
            text = value.ifBlank { "—" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}
