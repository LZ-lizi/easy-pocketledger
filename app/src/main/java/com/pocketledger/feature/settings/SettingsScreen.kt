package com.pocketledger.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pocketledger.ui.components.LedgerIcon
import com.pocketledger.ui.components.LedgerIconView

private class SettingsEntry(
    val title: String,
    val detail: String,
    val onClick: () -> Unit,
)

private class SettingsSection(
    val title: String,
    val entries: List<SettingsEntry>,
)

/**
 * The settings hub, as a two-level menu: sections, entries, then a page.
 *
 * Every entry navigates to a real screen. The earlier version listed sections that
 * were not built yet with no click handler at all, which read as broken rather than
 * unfinished -- an entry that cannot be opened should not be listed.
 */
@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    onOpenLedgers: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenInstallments: () -> Unit,
    onOpenPinned: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sections = listOf(
        SettingsSection(
            title = "账本",
            entries = listOf(
                SettingsEntry("账本管理", "新建、改名、归档，以及每个账本自己的预算", onOpenLedgers),
            ),
        ),
        SettingsSection(
            title = "记账",
            entries = listOf(
                SettingsEntry("类别管理", "增删改类别，调整大类归属", onOpenCategories),
                SettingsEntry("记账页显示", "选择哪些类别直接显示，其余收进「更多」", onOpenPinned),
                SettingsEntry("月付", "分期计划与到期自动扣款", onOpenInstallments),
                SettingsEntry("学期设置", "统计页「学期」用的日期区间", onOpenTerms),
            ),
        ),
        SettingsSection(
            title = "数据",
            entries = listOf(
                SettingsEntry("导入账单", "从微信、支付宝导出的账单批量记账", onOpenImport),
                SettingsEntry("导出数据", "把当前账本导出为 CSV", onOpenExport),
            ),
        ),
        SettingsSection(
            title = "关于",
            entries = listOf(
                SettingsEntry("关于记账本", "版本、当前账本、数据存放位置", onOpenAbout),
            ),
        ),
    )

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
        item(key = "title") {
            Text(
                text = "设置",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        sections.forEach { section ->
            item(key = "section-${section.title}") {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                )
            }
            items(section.entries.size, key = { "entry-${section.title}-$it" }) { index ->
                SettingsCard(section.entries[index])
            }
        }
    }
}

@Composable
private fun SettingsCard(entry: SettingsEntry) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = entry.onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = entry.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            LedgerIconView(
                icon = LedgerIcon.CHEVRON_RIGHT,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                size = 18.dp,
            )
        }
    }
}
