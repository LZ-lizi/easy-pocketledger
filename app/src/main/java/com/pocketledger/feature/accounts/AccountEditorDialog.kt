package com.pocketledger.feature.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.pocketledger.ui.components.ConfirmDeleteDialog
import com.pocketledger.ui.components.DestructiveOutlinedButton

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
 * The 余额 field shows the **live** balance and writes back to it directly, which is
 * why this needs [currentBalanceCents] rather than reading the account alone. Typing a
 * new figure re-anchors the account at that number: earlier transactions stop
 * counting toward the balance, and everything recorded afterwards builds on the new
 * figure. That makes correcting a wrong balance a one-step edit instead of a hunt
 * through months of history.
 *
 * Editing anything else leaves the anchor untouched, so renaming an account cannot
 * silently discard the transactions that produced its balance.
 *
 * Deletion is soft: [onDelete] hides the account while its transactions stay in the
 * ledger, so a mistaken delete never tears a hole in past statistics.
 */
@Composable
fun AccountEditorDialog(
    existing: AccountEntity?,
    currentBalanceCents: Long?,
    onDismiss: () -> Unit,
    onSave: (AccountEntity) -> Unit,
    onDelete: ((AccountEntity) -> Unit)? = null,
) {
    // A new account starts from nothing; an existing one starts from the balance the
    // user can actually see on the accounts screen, falling back to the stored
    // opening figure if balances have not loaded yet.
    val shownBalance = existing?.let { currentBalanceCents ?: it.initialBalanceCents } ?: 0L

    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var type by remember { mutableStateOf(existing?.type ?: AccountType.CASH) }
    var balanceText by remember { mutableStateOf(Money.formatCompact(shownBalance)) }
    // Only a deliberate edit may move the anchor. Compared against the prefill rather
    // than tracked by a focus flag, so typing a figure and undoing it is a no-op.
    var balanceEdited by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
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
    val parsedBalance = if (balanceText.isBlank()) 0L else Money.parseYuanToCents(balanceText)
    val balanceValid = balanceText.isBlank() || parsedBalance != null
    val canSave = name.isNotBlank() && balanceValid

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
                    value = balanceText,
                    onValueChange = {
                        balanceText = it
                        balanceEdited = it != Money.formatCompact(shownBalance)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    prefix = { Text("¥") },
                    label = { Text("余额") },
                    supportingText = {
                        Text(
                            text = if (existing == null) {
                                "记账从这里开始累加。"
                            } else {
                                "改了就按新余额继续记账，之前的流水不会变。"
                            },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    isError = !balanceValid,
                )

                QuickAmountRow { delta ->
                    val current = Money.parseYuanToCents(balanceText) ?: 0L
                    val next = Money.formatCompact(current + delta)
                    balanceText = next
                    balanceEdited = next != Money.formatCompact(shownBalance)
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

                if (existing != null && onDelete != null) {
                    Spacer(Modifier.height(4.dp))
                    DestructiveOutlinedButton(
                        label = "删除",
                        onClick = { confirmDelete = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    val parsed = parsedBalance ?: 0L
                    // The anchor only moves on a deliberate balance edit; for a new
                    // account it must stay 0 so a back-dated first entry still counts.
                    val reanchor = existing != null && balanceEdited
                    onSave(
                        AccountEntity(
                            id = existing?.id ?: 0L,
                            name = name.trim(),
                            type = type,
                            iconKey = existing?.iconKey ?: "wallet",
                            colorArgb = colorArgb,
                            initialBalanceCents = if (existing == null || balanceEdited) {
                                parsed
                            } else {
                                existing.initialBalanceCents
                            },
                            balanceAsOfMillis = if (reanchor) {
                                System.currentTimeMillis()
                            } else {
                                existing?.balanceAsOfMillis ?: 0L
                            },
                            creditLimitCents = creditLimit.takeIf { it.isNotBlank() }
                                ?.let { Money.parseYuanToCents(it) },
                            billDay = billDay.toIntOrNull()?.takeIf { it in 1..31 },
                            repayDay = repayDay.toIntOrNull()?.takeIf { it in 1..31 },
                            includeInTotal = includeInTotal,
                            sortOrder = existing?.sortOrder ?: 0,
                            isArchived = existing?.isArchived ?: false,
                            isHidden = existing?.isHidden ?: false,
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

    val target = existing
    if (confirmDelete && target != null && onDelete != null) {
        ConfirmDeleteDialog(
            title = "删除账户",
            target = "「${target.name}」会从账户列表移除，已有的流水仍然保留。",
            onConfirm = {
                confirmDelete = false
                onDelete(target)
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/**
 * Quick nudges to the balance.
 *
 * These move the anchor and nothing else. They deliberately do not write an income
 * row: filling in the money already on a card must not inflate this month's income,
 * which is exactly what would happen if this were wired to the ledger instead of to
 * the field.
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
