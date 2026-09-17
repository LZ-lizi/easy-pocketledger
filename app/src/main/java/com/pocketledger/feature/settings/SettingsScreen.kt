package com.pocketledger.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private class SettingsEntry(
    val title: String,
    val detail: String,
    val onClick: (() -> Unit)? = null,
)

/**
 * The settings hub.
 *
 * Entries that are not implemented yet are still listed, greyed by their wording
 * rather than hidden: the remaining work is then visible in the app itself instead
 * of only in the plan document.
 */
@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    onOpenTerms: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = listOf(
        SettingsEntry(
            title = "学期设置",
            detail = "统计页「学期」用的日期区间，比如 2026 秋季学期",
            onClick = onOpenTerms,
        ),
        SettingsEntry("分类与标签", "改名、改归属、增删二级项"),
        SettingsEntry("账单导入", "支付宝 / 微信 CSV，自动去重，可整批撤销"),
        SettingsEntry("导出与备份", "导出 CSV、完整备份与恢复、每日自动备份"),
        SettingsEntry("预算与提醒", "总预算 / 主分类预算 / 分类预算，超支通知"),
        SettingsEntry("安全", "PIN 码 + 生物识别解锁、自动锁定"),
        SettingsEntry("桌面小组件", "把「本月还能花」放到桌面"),
        SettingsEntry("外观", "动态取色、深色模式"),
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
        item(key = "header") {
            Text(
                text = "我的",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        items(entries, key = { it.title }) { entry ->
            SettingsCard(entry)
        }
    }
}

@Composable
private fun SettingsCard(entry: SettingsEntry) {
    val enabled = entry.onClick != null
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (enabled) Modifier.clickable { entry.onClick?.invoke() } else Modifier
            ),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = entry.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
