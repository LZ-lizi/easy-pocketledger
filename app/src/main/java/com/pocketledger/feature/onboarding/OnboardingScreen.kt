package com.pocketledger.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketledger.data.entity.LedgerType
import com.pocketledger.ui.theme.LedgerTheme

/**
 * First launch: name a ledger and choose how it tracks.
 *
 * The mode is asked for up front rather than buried in settings because it decides
 * what the whole app looks like -- whether there is an account page at all, and
 * whether the home screen shows a monthly allowance.
 */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "创建账本",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "每个账本独立统计。之后可以再建账本，在「设置 → 账本」里切换。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(28.dp))
        OutlinedTextField(
            value = state.name,
            onValueChange = viewModel::setName,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("账本名称") },
        )

        Spacer(Modifier.height(20.dp))
        Text(
            text = "记账方式",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))

        LedgerTypeCard(
            title = "预算模式",
            detail = "有账户和余额，可以设每月生活费，首页显示「本月还能花」。",
            accent = LedgerTheme.colors.daily,
            selected = state.type == LedgerType.BUDGET,
            onClick = { viewModel.setType(LedgerType.BUDGET) },
        )
        Spacer(Modifier.height(10.dp))
        LedgerTypeCard(
            title = "累计模式",
            detail = "只累计收入和支出，不涉及账户，首页显示本月收支。",
            accent = LedgerTheme.colors.transfer,
            selected = state.type == LedgerType.ACCUMULATE,
            onClick = { viewModel.setType(LedgerType.ACCUMULATE) },
        )

        Spacer(Modifier.height(28.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(MaterialTheme.shapes.large)
                .background(
                    if (state.canCreate) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                )
                .clickable(enabled = state.canCreate, onClick = viewModel::create),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (state.creating) "正在创建…" else "创建账本",
                style = MaterialTheme.typography.titleMedium,
                color = if (state.canCreate) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun LedgerTypeCard(
    title: String,
    detail: String,
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(
                if (selected) accent.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceContainer
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(
                    if (selected) accent else MaterialTheme.colorScheme.outlineVariant
                )
        )
        Spacer(Modifier.width(12.dp))
        Column {
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
}
