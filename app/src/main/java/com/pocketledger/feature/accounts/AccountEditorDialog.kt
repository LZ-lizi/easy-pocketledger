package com.pocketledger.feature.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import com.pocketledger.data.entity.AccountEntity
import com.pocketledger.data.entity.AccountType
import com.pocketledger.domain.Money

/** Palette offered for account accents; index-stable so a colour never moves. */
private val ACCOUNT_COLORS = listOf(
    0xFF2F6BFF, 0xFF00A88F, 0xFFFF7A45, 0xFF8B5CF6, 0xFFEC4899,
    0xFFF59E0B, 0xFF10B981, 0xFF6366F1, 0xFFEF4444, 0xFF64748B,
).map { it.toInt() }

private val ACCOUNT_TYPES = listOf(
    AccountType.CASH to "现金",
    AccountType.BANK_CARD to "银行卡",
    AccountType.CREDIT_CARD to "信用卡",
    AccountType.ALIPAY to "支付宝",
    AccountType.WECHAT to "微信",
    AccountType.PREPAID to "储值卡",
    AccountType.OTHER to "其他",
)

/**
 * Create or edit one account.
 *
 * Initial balance is only editable as a starting figure, not as the live balance:
 * the live balance is always derived from transactions, so letting someone type
 * over it here would silently desynchronise the two.
 */
@Composable
fun AccountEditorDialog(
    existing: AccountEntity?,
    onDismiss: () -> Unit,
    onSave: (AccountEntity) -> Unit,
    onArchive: ((AccountEntity) -> Unit)? = null,
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var type by remember { mutableStateOf(existing?.type ?: AccountType.CASH) }
    var initialBalance by remember {
        mutableStateOf(
            existing?.initialBalanceCents
                ?.takeIf { it != 0L }
                ?.let { Money.formatCompact(it) }
                .orEmpty()
        )
    }
    var creditLimit by remember {
        mutableStateOf(
            existing?.creditLimitCents?.let { Money.formatCompact(it) }.orEmpty()
        )
    }
    var billDay by remember { mutableStateOf(existing?.billDay?.toString().orEmpty()) }
    var repayDay by remember { mutableStateOf(existing?.repayDay?.toString().orEmpty()) }
    var includeInTotal by remember { mutableStateOf(existing?.includeInTotal ?: true) }
    var colorArgb by remember {
        mutableStateOf(existing?.colorArgb ?: ACCOUNT_COLORS.first())
    }

    val isCreditCard = type == AccountType.CREDIT_CARD
    val parsedInitial = if (initialBalance.isBlank()) 0L else Money.parseYuanToCents(initialBalance)
    val initialValid = initialBalance.isBlank() || parsedInitial != null
    val canSave = name.isNotBlank() && initialValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "添加账户" else "编辑账户") },
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

                Text(
                    text = "类型",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ACCOUNT_TYPES.forEach { (value, label) ->
                        SelectChip(
                            label = label,
                            selected = type == value,
                            onClick = { type = value },
                        )
                    }
                }

                OutlinedTextField(
                    value = initialBalance,
                    onValueChange = { initialBalance = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    prefix = { Text("¥") },
                    label = { Text("初始余额") },
                    supportingText = {
                        Text(
                            text = "当前余额由流水自动算出，这里只是起始金额。",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    isError = !initialValid,
                )

                QuickAmountRow { delta ->
                    val current = Money.parseYuanToCents(initialBalance) ?: 0L
                    initialBalance = Money.formatCompact(current + delta)
                }

                if (isCreditCard) {
                    OutlinedTextField(
                        value = creditLimit,
                        onValueChange = { creditLimit = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        prefix = { Text("¥") },
                        label = { Text("额度（可留空）") },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = billDay,
                            onValueChange = { billDay = it.filter(Char::isDigit).take(2) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("账单日") },
                        )
                        OutlinedTextField(
                            value = repayDay,
                            onValueChange = { repayDay = it.filter(Char::isDigit).take(2) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("还款日") },
                        )
                    }
                }

                Text(
                    text = "颜色",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ACCOUNT_COLORS.forEach { candidate ->
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(candidate))
                                .clickable { colorArgb = candidate },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (candidate == colorArgb) {
                                Box(
                                    Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "计入总资产",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "注销的卡可以关掉，流水仍保留。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = includeInTotal, onCheckedChange = { includeInTotal = it })
                }

                if (existing != null && onArchive != null) {
                    TextButton(onClick = { onArchive(existing) }) {
                        Text(
                            text = if (existing.isArchived) "取消归档" else "归档这个账户",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    onSave(
                        AccountEntity(
                            id = existing?.id ?: 0L,
                            name = name.trim(),
                            type = type,
                            iconKey = existing?.iconKey ?: "wallet",
                            colorArgb = colorArgb,
                            initialBalanceCents = parsedInitial ?: 0L,
                            creditLimitCents = creditLimit.takeIf { it.isNotBlank() }
                                ?.let { Money.parseYuanToCents(it) },
                            billDay = billDay.toIntOrNull()?.takeIf { it in 1..31 },
                            repayDay = repayDay.toIntOrNull()?.takeIf { it in 1..31 },
                            includeInTotal = includeInTotal,
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
}

/**
 * Quick nudges to the opening balance.
 *
 * These edit the account's starting figure and nothing else. They deliberately do
 * not write an income row: filling in a card you already have money on must not
 * inflate this month's income, which is exactly what would happen if this were
 * wired to the ledger instead of to the field.
 */
@Composable
private fun QuickAmountRow(onAdd: (Long) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QUICK_AMOUNTS_YUAN.forEach { yuan ->
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable { onAdd(yuan * 100L) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+$yuan",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }
}

private val QUICK_AMOUNTS_YUAN = listOf(100L, 500L, 1000L, 5000L)

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
