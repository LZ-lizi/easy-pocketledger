package com.pocketledger.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pocketledger.MainActivity
import com.pocketledger.domain.BudgetAlert
import com.pocketledger.domain.Money

/**
 * Posts budget warnings.
 *
 * One channel for all of them: a user who wants fewer of these should be able to
 * silence the category rather than hunt for several. Posting is best-effort -- a
 * missing permission must never break the accounting that triggered it.
 */
class BudgetNotifier(private val context: Context) {

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "预算提醒",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "预算接近上限或已超支时提醒"
        }
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    fun canPost(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    /** @return true when the notification was actually posted. */
    fun post(alert: BudgetAlert): Boolean {
        if (!canPost()) return false
        ensureChannel()

        val title = if (alert.level >= 100) "${alert.name} 已超支" else "${alert.name} 接近上限"
        val text = if (alert.level >= 100) {
            "已花 " + Money.formatWithSymbol(alert.spentCents) +
                "，超出 " + Money.formatWithSymbol(alert.spentCents - alert.limitCents)
        } else {
            "已花 " + Money.formatWithSymbol(alert.spentCents) +
                " / " + Money.formatWithSymbol(alert.limitCents)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            alert.key.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        return runCatching {
            NotificationManagerCompat.from(context).notify(alert.key.hashCode(), notification)
            true
        }.getOrDefault(false)
    }

    companion object {
        const val CHANNEL_ID = "budget_alerts"
    }
}
