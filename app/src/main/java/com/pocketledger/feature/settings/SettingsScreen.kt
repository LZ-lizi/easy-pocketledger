package com.pocketledger.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Placeholder for the settings hub.
 *
 * Each entry below maps to a milestone item (categories/tags, import, export and
 * backup, security, the desktop widget, appearance). The list is written out now
 * so the navigation shell is complete and the remaining work is visible in the app
 * itself rather than only in the plan.
 */
@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = contentPadding.calculateTopPadding() + 8.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "我的",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))

        SettingsPlaceholder(
            title = "分类与标签",
            detail = "改名、改归属、增删二级项",
        )
        SettingsPlaceholder(
            title = "账单导入",
            detail = "支付宝 / 微信 CSV，自动去重，可整批撤销",
        )
        SettingsPlaceholder(
            title = "导出与备份",
            detail = "导出 CSV、完整备份与恢复、每日自动备份",
        )
        SettingsPlaceholder(
            title = "安全",
            detail = "PIN 码 + 生物识别解锁、自动锁定",
        )
        SettingsPlaceholder(
            title = "桌面小组件",
            detail = "把「本月还能花」放到桌面",
        )
        SettingsPlaceholder(
            title = "外观",
            detail = "动态取色、深色模式",
        )
    }
}

@Composable
private fun SettingsPlaceholder(title: String, detail: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
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
