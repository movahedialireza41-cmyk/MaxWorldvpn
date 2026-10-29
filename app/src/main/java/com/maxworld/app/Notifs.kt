package com.maxworld.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object Notifs {
    const val CHANNEL = "vpn"
    const val ID = 1001

    private fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            val ch = NotificationChannel(
                CHANNEL,
                ctx.getString(R.string.channel_vpn),
                NotificationManager.IMPORTANCE_MIN // silent, no status-bar icon clutter
            ).apply {
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            nm.createNotificationChannel(ch)
        }
    }

    fun vpn(ctx: Context, blocked: Boolean = false, detail: String? = null): Notification {
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            ctx, 1,
            Intent(ctx, MaxVpnService::class.java).setAction(MaxVpnService.ACTION_DISCONNECT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_vpn)
            .setContentTitle(ctx.getString(if (blocked) R.string.kill_notif_title else R.string.vpn_notif_title))
            .setContentText(detail ?: ctx.getString(if (blocked) R.string.kill_notif_text else R.string.vpn_notif_text))
            .setContentIntent(open)
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(
                Notification.Action.Builder(null, ctx.getString(R.string.disconnect), stop).build()
            )
            .build()
    }
}
