package com.pocketledger.feature.settings

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * What every part of the app does, in the app's own words.
 *
 * Written as a reference rather than as marketing: the app is local-only and has no
 * onboarding tour, so this is the one place a user can read what a feature is for
 * without discovering it by tapping.
 */
@Composable
fun FeatureGuideScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "‹",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "功能介绍",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        FEATURES.forEach { (section, entries) ->
            item(key = "section-$section") {
                Text(
                    text = section,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                )
            }
            item(key = "card-$section") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        entries.forEach { (name, description) ->
                            Column {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val FEATURES: List<Pair<String, List<Pair<String, String>>>> = listOf(
    "记账" to listOf(
        "记账录入" to
            "在「明细」页点击右下角按钮进入记账页，通过数字键盘输入金额，" +
            "并选择收支方向、类别与账户。日期默认为当天，可在顶部修改。",
        "修改与删除" to
            "在「明细」页点击任意一条记录进入详情页，可查看完整信息或进入编辑页修改，" +
            "修改后详情页与各统计页面立即更新。",
        "转账" to
            "记录账户之间的资金划转。转入转出金额相同，手续费单独计为支出，不计入收入或支出总额。",
        "月付" to
            "记录分期或按月扣款的支出计划，例如数码产品分期、房租月付。" +
            "计划到期后自动生成对应的记账条目。",
    ),
    "账本" to listOf(
        "账本管理" to
            "支持建立多个账本，分别记录不同的账目。可对账本进行新建、改名、归档与删除操作。",
        "预算模式" to
            "具备账户与余额概念，可设置每月生活费与各类别预算，适用于日常收支管理。",
        "累计模式" to
            "只记录收入与支出，不区分账户，适用于记录单项累计账目。",
        "归档" to
            "将不再使用的账本收起。归档后的账本仍可查看，但不能修改其中的记账条目。",
    ),
    "类别与账户" to listOf(
        "类别管理" to
            "增删改各类别，并调整其归属的大类。类别用于记账时的归类和统计时的汇总。",
        "类目快捷选择" to
            "指定记账页直接显示的类别，其余类别收进「更多」菜单。未指定时按最近使用情况自动排列。",
        "账户" to
            "管理现金、银行卡、支付宝、微信等账户，并查看各账户余额。余额可通过设置余额锚点进行校准。",
    ),
    "查看与统计" to listOf(
        "明细" to
            "按日列出当月全部记账条目，并显示每日支出与收入合计。可切换日历视图，" +
            "日历上按日显示收支金额，点击某日可只看当天的记录。",
        "搜索" to
            "按关键词、日期区间、类别、账户、收支方向与金额区间筛选记账条目。",
        "统计" to
            "按本月、近三十天、今年、学期或全部时间范围，以图表展示支出构成与收支趋势。",
        "学期设置" to
            "设置统计页「学期」模式所使用的日期区间，适用于按学期查看开支。",
    ),
    "数据" to listOf(
        "导入账单" to
            "导入微信、支付宝导出的账单文件（CSV 或 xlsx），或本应用导出的 CSV 文件。" +
            "导入前会逐条列出待确认的记录，可选择目标账本、修改收支方向与类别；" +
            "重复记录会自动识别并跳过，导入后可整批撤销。",
        "导出数据" to
            "将当前账本的全部记账条目导出为 CSV 文件，用于备份或在其他软件中查看。",
        "预算提醒" to
            "当本月支出接近或超出所设预算时，通过通知提醒。",
        "桌面小组件" to
            "在主屏幕添加小组件，直接查看当月的支出或剩余额度。",
    ),
)
