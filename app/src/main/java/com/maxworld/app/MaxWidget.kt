package com.maxworld.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Home-screen widget: shows the state and toggles the VPN with a tap. */
class MaxWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE = "com.maxworld.app.WIDGET_TOGGLE"

        fun refresh(ctx: Context) {
            try {
                val mgr = AppWidgetManager.getInstance(ctx)
                val ids = mgr.getAppWidgetIds(ComponentName(ctx, MaxWidget::class.java))
                if (ids.isNotEmpty()) apply(ctx, mgr, ids)
            } catch (ignored: Exception) {
            }
        }

        private fun apply(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            val views = RemoteViews(ctx.packageName, R.layout.widget_main)
            val (textRes, color) = when (VpnState.state) {
                ConnState.CONNECTED -> R.string.status_connected to 0xFF00F5A0.toInt()
                ConnState.CONNECTING -> R.string.status_connecting to 0xFFFFB74D.toInt()
                ConnState.DISCONNECTED -> R.string.status_disconnected to 0xFF8A97B5.toInt()
            }
            views.setTextViewText(R.id.widget_status, ctx.getString(textRes))
            views.setTextColor(R.id.widget_status, color)

            val toggle = PendingIntent.getBroadcast(
                ctx, 2,
                Intent(ctx, MaxWidget::class.java).setAction(ACTION_TOGGLE),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_root, toggle)
            mgr.updateAppWidget(ids, views)
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        apply(context, manager, ids)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE) {
            ConfigStore.init(context)
            Surfaces.toggle(context)
        }
    }
}
