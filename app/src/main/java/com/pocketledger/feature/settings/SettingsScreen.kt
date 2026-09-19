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
                SettingsEntry("账本管理", onOpenLedgers),
            ),
        ),
        SettingsSection(
            title = "记账",
            entries = listOf(
                SettingsEntry("类别管理", onOpenCategories),
                SettingsEntry("类目快捷选择", onOpenPinned),
                SettingsEntry("月付管理", onOpenInstallments),
                SettingsEntry("学期设置", onOpenTerms),
            ),
        ),
        SettingsSection(
            title = "数据",
            entries = listOf(
                SettingsEntry("导入账单", onOpenImport),
                SettingsEntry("导出数据", onOpenExport),
            ),
        ),
        SettingsSection(
            title = "关于",
            entries = listOf(
                SettingsEntry("关于记账本", onOpenAbout),
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
                // No sub-description: the titles are self-explanatory, and a line of
                // grey text under every row made the list longer than it needed to be
                // while telling the user nothing they could not read off the title.
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
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
