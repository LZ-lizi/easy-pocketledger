package com.pocketledger.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pocketledger.ui.theme.LedgerTheme

/**
 * A destructive action -- 删除, 终止 -- drawn as an outlined button.
 *
 * These used to be borderless `TextButton`s, which made 删除这个账户 look like a caption
 * sitting under the form rather than something you can press: nothing about the layout
 * said "control" until you happened to tap it. An outline restores the affordance without
 * making the action any louder than it already is -- the colour still carries the warning,
 * and the border carries "this is a button".
 *
 * The border is drawn at partial alpha and the label at full strength so the text stays
 * the readable part; a solid red box would compete with the real primary action.
 */
@Composable
fun DestructiveOutlinedButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
) {
    val accent = LedgerTheme.colors.expense
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        border = BorderStroke(1.dp, accent.copy(alpha = if (enabled) 0.6f else 0.3f)),
        contentPadding = contentPadding,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = accent,
            disabledContentColor = accent.copy(alpha = 0.38f),
        ),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * The same action with no outline, for a row that is already a control.
 *
 * The outline exists to say "this is pressable" when the button stands alone under a form.
 * In the 大类 header it does the opposite: the row is a tinted card with its own 「添加」
 * action in it, so a bordered box next to plain text reads as a second, competing card
 * rather than as a button. The colour still carries the warning.
 */
@Composable
fun DestructiveTextButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
) {
    val accent = LedgerTheme.colors.expense
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        contentPadding = contentPadding,
        colors = ButtonDefaults.textButtonColors(
            contentColor = accent,
            disabledContentColor = accent.copy(alpha = 0.38f),
        ),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
    }
}
