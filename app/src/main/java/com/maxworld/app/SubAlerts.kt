package com.maxworld.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** Tells the user once when a subscription is almost out of data or about to expire. */
object SubAlerts {
    private const val CHANNEL = "sub_alerts"

    fun check(ctx: Context, s: Sub) {
        if (!Prefs.subAlerts(ctx)) return
        try {
            val now = System.currentTimeMillis()
            val days = if (s.expire > 0L) Math.ceil((s.expire - now) / 86400000.0).toLong() else Long.MAX_VALUE
            val low = s.total > 0L && (s.total - s.used) <= s.total / 10
            val conds = listOf(
                Triple("d10", low, ctx.getString(R.string.alert_data_low, s.name)),
                Triple("e3", s.expire > 0L && days in 1..3, ctx.getString(R.string.alert_expiring, s.name, days.toInt())),
                Triple("e0", s.expire > 0L && days <= 0, ctx.getString(R.string.alert_expired, s.name))
            )
            val p = ctx.applicationContext.getSharedPreferences("alerts", Context.MODE_PRIVATE)
            for ((k, on, text) in conds) {
                val key = s.id + ":" + k
                val was = p.getBoolean(key, false)
                if (on && !was) {
                    p.edit().putBoolean(key, true).apply()
                    post(ctx, key.hashCode(), text)
                } else if (!on && was) {
                    p.edit().putBoolean(key, false).apply()
                }
            }
        } catch (ignored: Exception) {
        }
    }

    private fun post(ctx: Context, id: Int, text: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, ctx.getString(R.string.alert_channel), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val open = PendingIntent.getActivity(
            ctx, 2,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        nm.notify(
            id,
            Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_vpn)
                .setContentTitle("MaxWorld")
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        )
    }
}
