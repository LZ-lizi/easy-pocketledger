package com.pocketledger.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pocketledger.ui.theme.LedgerTheme

/**
 * The one delete confirmation in the app.
 *
 * Every destructive action routes through this so the warning is worded and placed
 * identically. Deleting is the only irreversible thing here -- accounts, categories,
 * terms, ledgers and entries are all soft-deleted or simply gone from the UI -- and a
 * dialog that warns on some of them and not others is a dialog users learn to dismiss
 * without reading.
 *
 * [target] says what specifically will be removed, in the caller's own words, because
 * "are you sure?" without naming the thing is not a question anyone can answer. It may be
 * left out where the title already names the thing and the line would only repeat it; when
 * it is, nothing is drawn -- not an empty line -- so the dialog keeps its spacing.
 */
@Composable
fun ConfirmDeleteDialog(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    target: String? = null,
    confirmLabel: String = "删除",
    enabled: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (!target.isNullOrBlank()) {
                    Text(
                        text = target,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Text(
                    text = "删除后无法恢复，请谨慎操作。",
                    style = MaterialTheme.typography.bodySmall,
                    color = LedgerTheme.colors.expense,
                )
            }
        },
        confirmButton = {
            DestructiveOutlinedButton(label = confirmLabel, onClick = onConfirm, enabled = enabled)
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
