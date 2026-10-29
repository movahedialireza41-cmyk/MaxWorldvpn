package com.maxworld.app

import android.content.Context

/** Things outside the app window that mirror the connection state. */
object Surfaces {
    fun refresh(ctx: Context) {
        val app = ctx.applicationContext
        MaxTileService.refresh(app)
        MaxWidget.refresh(app)
    }

    /** Shared by the tile and the widget: connect, disconnect, or open the app if consent is missing. */
    fun toggle(ctx: Context) {
        when (VpnState.state) {
            ConnState.CONNECTED, ConnState.CONNECTING -> MaxVpnService.stop(ctx)
            ConnState.DISCONNECTED -> {
                val ready = ConfigStore.selected() != null && android.net.VpnService.prepare(ctx) == null
                if (ready) {
                    MaxVpnService.start(ctx)
                } else {
                    openApp(ctx)
                }
            }
        }
    }

    fun openApp(ctx: Context) {
        try {
            ctx.startActivity(
                android.content.Intent(ctx, MainActivity::class.java)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (ignored: Exception) {
        }
    }
}
