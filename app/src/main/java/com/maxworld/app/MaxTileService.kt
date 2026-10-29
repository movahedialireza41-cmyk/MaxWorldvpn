package com.maxworld.app

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick Settings tile: one tap to connect or disconnect. */
class MaxTileService : TileService() {

    companion object {
        fun refresh(ctx: Context) {
            try {
                TileService.requestListeningState(ctx, ComponentName(ctx, MaxTileService::class.java))
            } catch (ignored: Exception) {
            }
        }
    }

    override fun onStartListening() {
        update()
    }

    override fun onClick() {
        val consent = android.net.VpnService.prepare(this)
        if (VpnState.state == ConnState.DISCONNECTED && (consent != null || ConfigStore.selected() == null)) {
            openApp()
        } else {
            Surfaces.toggle(this)
        }
    }

    private fun update() {
        val tile = qsTile ?: return
        val (state, text) = when (VpnState.state) {
            ConnState.CONNECTED -> Tile.STATE_ACTIVE to R.string.status_connected
            ConnState.CONNECTING -> Tile.STATE_ACTIVE to R.string.status_connecting
            ConnState.DISCONNECTED -> Tile.STATE_INACTIVE to R.string.status_disconnected
        }
        tile.state = state
        tile.label = getString(R.string.app_name)
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = getString(text)
        tile.updateTile()
    }

    @Suppress("DEPRECATION")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
        } else {
            startActivityAndCollapse(intent)
        }
    }
}
