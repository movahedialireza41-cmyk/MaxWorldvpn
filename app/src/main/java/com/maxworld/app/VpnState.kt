package com.maxworld.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ConnState { DISCONNECTED, CONNECTING, CONNECTED }

/** Connection status shared between the VPN service and the UI (same process). */
object VpnState {
    var state by mutableStateOf(ConnState.DISCONNECTED)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    /** True while the kill switch is holding the internet closed. */
    var blocking by mutableStateOf(false)
        private set
    var connectedAt by mutableLongStateOf(0L)
        private set

    fun connecting() {
        error = null
        AppLog.add("Connecting…")
        state = ConnState.CONNECTING
    }

    fun markBlocking(v: Boolean) {
        blocking = v
    }

    fun connected() {
        blocking = false
        connectedAt = System.currentTimeMillis()
        AppLog.add("Connected")
        state = ConnState.CONNECTED
    }

    fun disconnected() {
        blocking = false
        if (state != ConnState.DISCONNECTED) AppLog.add("Disconnected")
        connectedAt = 0L
        state = ConnState.DISCONNECTED
    }

    fun failed(message: String) {
        AppLog.add("Failed: " + message)
        error = message
        connectedAt = 0L
        state = ConnState.DISCONNECTED
    }

    fun clearError() {
        error = null
    }
}
