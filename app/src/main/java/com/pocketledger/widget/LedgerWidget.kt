package com.pocketledger.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.pocketledger.LedgerApp
import com.pocketledger.MainActivity
import com.pocketledger.data.entity.LedgerType
import com.pocketledger.data.repo.WidgetSnapshot
import com.pocketledger.domain.Money

/**
 * Home-screen widget showing this month at a glance, with a one-tap way in.
 *
 * Two separate widgets rather than one resizable one: the launcher then offers
 * 「2×2」 and 「2×4」 as distinct choices, which is how people actually add them, and
 * each lays out for its own shape instead of stretching.
 *
 * Data is read with an explicit ledger because a widget can be the only reason the
 * process starts -- nothing has set the app's "current ledger" at that point.
 */
private suspend fun loadSnapshot(context: Context): WidgetSnapshot? {
    val app = context.applicationContext as? LedgerApp ?: return null
    return runCatching { app.container.repository.widgetSnapshot() }.getOrNull()
}

@Composable
private fun WidgetBody(snapshot: WidgetSnapshot?, wide: Boolean, context: Context) {
    GlanceTheme {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(20.dp)
                .padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = snapshot?.ledgerName ?: "记账本",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp,
                ),
                maxLines = 1,
            )
            Spacer(GlanceModifier.height(6.dp))

            if (snapshot == null) {
                Text(
                    text = "打开应用以完成初始化",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 13.sp,
                    ),
                )
                return@Column
            }

            // The headline answers the same question the home screen answers first.
            val remaining = snapshot.remainingCents
            val headline = when {
                snapshot.ledgerType == LedgerType.ACCUMULATE -> snapshot.monthExpenseCents
                remaining != null -> remaining
                else -> snapshot.monthExpenseCents
            }
            Text(
                text = Money.formatWithSymbol(headline),
                style = TextStyle(
                    color = ColorProvider(
                        if (remaining != null && remaining < 0L) Color(0xFFE5484D)
                        else Color(0xFF11151C)
                    ),
                    fontSize = if (wide) 26.sp else 22.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = when {
                    snapshot.ledgerType == LedgerType.ACCUMULATE -> "本月支出"
                    remaining != null -> "预算剩余"
                    else -> "本月支出"
                },
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp,
                ),
            )

            if (wide && snapshot.allowanceCents > 0L) {
                Spacer(GlanceModifier.height(8.dp))
                // Glance has no progress primitive and no fractional fill, so the
                // allowance is reported as a figure plus a percentage rather than a
                // bar that would have to guess the widget's pixel width.
                Text(
                    text = "已花 " + Money.format(snapshot.monthExpenseCents) +
                        " / " + Money.format(snapshot.allowanceCents) +
                        "（" + (snapshot.progress * 100).toInt() + "%）",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 10.sp,
                    ),
                    maxLines = 1,
                )
            }

            Spacer(GlanceModifier.height(10.dp))

            AddEntryButton(wide = wide, context = context)
        }
    }
}

/** One tap straight into the keypad, skipping the app's home screen. */
@Composable
private fun AddEntryButton(wide: Boolean, context: Context) {
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(MainActivity.EXTRA_START_ENTRY, true)
    }
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(34.dp)
            .cornerRadius(17.dp)
            .background(GlanceTheme.colors.primary)
            .clickable(actionStartActivity(intent)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (wide) "＋ 记一笔" else "＋",
            style = TextStyle(
                color = GlanceTheme.colors.onPrimary,
                fontSize = if (wide) 13.sp else 16.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

/** 2×2: the month figure and a button. */
class LedgerWidgetSmall : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = loadSnapshot(context)
        provideContent { WidgetBody(snapshot = snapshot, wide = false, context = context) }
    }
}

/** 2×4: the same figure plus the allowance bar and a labelled button. */
class LedgerWidgetWide : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = loadSnapshot(context)
        provideContent { WidgetBody(snapshot = snapshot, wide = true, context = context) }
    }
}

class LedgerWidgetSmallReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LedgerWidgetSmall()
}

class LedgerWidgetWideReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LedgerWidgetWide()
}

/** Re-renders every placed widget; called at startup and after a ledger switch. */
suspend fun refreshLedgerWidgets(context: Context) {
    runCatching { LedgerWidgetSmall().updateAll(context) }
    runCatching { LedgerWidgetWide().updateAll(context) }
}