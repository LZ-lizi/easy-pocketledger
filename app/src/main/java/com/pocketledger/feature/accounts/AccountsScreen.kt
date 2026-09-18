package com.pocketledger.feature.accounts

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketledger.data.entity.AccountType
import com.pocketledger.domain.Money
import com.pocketledger.ui.components.LedgerIcon
import com.pocketledger.ui.components.LedgerIconView
import com.pocketledger.ui.components.LedgerSwitcherDialog
import com.pocketledger.ui.theme.LedgerTheme
import com.pocketledger.ui.theme.MoneyTextStyles

/**
 * Account list with derived balances.
 *
 * Credit-card debt is reported separately, because "you have ¥8,000" reads very
 * differently once ¥3,000 of it is owed.
 */
@Composable
fun AccountsScreen(
    viewModel: AccountsViewModel,
    contentPadding: PaddingValues,
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
                Text(
                    text = "账户",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable { viewModel.openLedgerSwitcher() }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = state.ledgerName.ifBlank { "账本" },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 84.dp),
                    )
                }
                if (!state.accountsUnsupported) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable { viewModel.createAccount() }
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
        }

        if (state.accountsUnsupported) {
            // A 累计模式 ledger has no accounts by design, so the page says so rather
            // than offering a button that would create something it cannot use.
            item(key = "unsupported") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = "当前账本是累计模式",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "累计模式只记录收支，不涉及账户。需要账户和余额，请切换到预算模式账本。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            return@LazyColumn
        }

        item(key = "networth") { NetWorthCard(state) }

        if (state.accounts.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = "还没有账户，点右上角「添加」建一个",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 32.dp),
                )
            }
        }

        items(state.accounts, key = { it.account.id }) { row ->
            AccountCard(row = row, onClick = { viewModel.editAccount(row.account) })
        }
    }

    if (state.editorVisible) {
        AccountEditorDialog(
            existing = state.editorTarget,
            currentBalanceCents = state.editorBalanceCents,
            onDismiss = viewModel::dismissEditor,
            onSave = viewModel::saveAccount,
            onDelete = viewModel::deleteAccount,
        )
    }

    if (state.ledgerSwitcherVisible) {
        LedgerSwitcherDialog(
            ledgers = state.ledgers,
            selectedId = state.selectedLedgerId,
            onSelect = viewModel::selectLedger,
            onDismiss = viewModel::dismissLedgerSwitcher,
        )
    }
}

@Composable
private fun NetWorthCard(state: AccountsUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = "净资产",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = Money.formatWithSymbol(state.netWorthCents),
                style = MoneyTextStyles.Hero,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            Row {
                StatPill("资产", Money.format(state.assetsCents), LedgerTheme.colors.income)
                Spacer(Modifier.width(14.dp))
                StatPill("负债", Money.format(state.liabilitiesCents), LedgerTheme.colors.expense)
            }
        }
    }
}

@Composable
private fun StatPill(label: String, amount: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "$label $amount",
            style = MoneyTextStyles.Small,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AccountCard(row: AccountRow, onClick: () -> Unit) {
    val accent = Color(row.account.colorArgb)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                LedgerIconView(
                    icon = LedgerIcon.forKey(row.account.iconKey),
                    tint = accent,
                    size = 22.dp,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = row.account.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = accountSubtitle(row.account),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = Money.formatWithSymbol(row.balanceCents),
                style = MoneyTextStyles.Medium,
                color = when {
                    row.balanceCents < 0L -> LedgerTheme.colors.expense
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

private fun accountSubtitle(account: com.pocketledger.data.entity.AccountEntity): String {
    val typeLabel = accountTypeLabel(account.type)
    return when {
        !account.includeInTotal -> "$typeLabel · 不计入总资产"
        account.type == AccountType.CREDIT_CARD && account.repayDay != null ->
            "$typeLabel · 每月 ${account.repayDay} 日还款"

        else -> typeLabel
    }
}

private fun accountTypeLabel(type: AccountType): String = when (type) {
    AccountType.CASH -> "现金"
    AccountType.BANK_CARD -> "银行卡"
    AccountType.CREDIT_CARD -> "信用卡"
    AccountType.ALIPAY -> "支付宝"
    AccountType.WECHAT -> "微信"
    AccountType.PREPAID -> "储值卡"
    AccountType.OTHER -> "其他"
}
