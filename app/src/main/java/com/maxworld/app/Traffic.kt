package com.maxworld.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import hev.htproxy.TProxyService

/** Live speed and usage of the current connection, read from the tunnel's counters. */
object Traffic {
    private const val HISTORY = 48

    var downBps by mutableLongStateOf(0L)
        private set
    var upBps by mutableLongStateOf(0L)
        private set
    var totalDown by mutableLongStateOf(0L)
        private set
    var totalUp by mutableLongStateOf(0L)
        private set

    /** Live latency of the current connection in ms; 0 = not measured yet, -2 = timed out. */
    var livePing by mutableLongStateOf(0L)
        private set
    val pingHistory = mutableStateListOf<Float>()

    val historyDown = mutableStateListOf<Float>()
    val historyUp = mutableStateListOf<Float>()

    private var lastTime = 0L
    private var lastDown = 0L
    private var lastUp = 0L

    fun reset() {
        downBps = 0
        upBps = 0
        totalDown = 0
        totalUp = 0
        lastTime = 0
        lastDown = 0
        lastUp = 0
        historyDown.clear()
        historyUp.clear()
        livePing = 0
        pingHistory.clear()
    }

    /** Round trip through the local proxy to a tiny endpoint; blocking, call off the main thread. */
    fun measurePing() {
        val t0 = System.nanoTime()
        val ms = try {
            val proxy = java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress("127.0.0.1", XrayConfig.HTTP_PORT))
            val c = java.net.URL("http://cp.cloudflare.com/generate_204").openConnection(proxy) as java.net.HttpURLConnection
            c.connectTimeout = 4000
            c.readTimeout = 4000
            c.useCaches = false
            try {
                val code = c.responseCode
                if (code in 200..399) ((System.nanoTime() - t0) / 1_000_000L).coerceAtLeast(1) else -2L
            } finally {
                c.disconnect()
            }
        } catch (e: Exception) {
            -2L
        }
        livePing = ms
        if (ms > 0) push(pingHistory, ms.toFloat())
    }

    /** Call about once per second while connected. */
    fun sample() {
        val s = try {
            TProxyService.TProxyGetStats()
        } catch (t: Throwable) {
            null
        } ?: return
        if (s.size < 4) return
        val up = s[1]
        val down = s[3]
        val now = System.nanoTime()

        if (lastTime != 0L) {
            val dt = (now - lastTime) / 1e9
            if (dt > 0.2) {
                upBps = ((up - lastUp) / dt).toLong().coerceAtLeast(0)
                downBps = ((down - lastDown) / dt).toLong().coerceAtLeast(0)
                push(historyDown, downBps.toFloat())
                push(historyUp, upBps.toFloat())
            } else {
                return
            }
        }
        lastTime = now
        lastUp = up
        lastDown = down
        totalUp = up
        totalDown = down
    }

    private fun push(list: MutableList<Float>, v: Float) {
        list.add(v)
        while (list.size > HISTORY) list.removeAt(0)
    }

    fun bytes(n: Long): String = when {
        n < 1024 -> "$n B"
        n < 1024 * 1024 -> "%.1f KB".format(n / 1024.0)
        n < 1024L * 1024 * 1024 -> "%.1f MB".format(n / 1048576.0)
        else -> "%.2f GB".format(n / 1073741824.0)
    }

    fun rate(bps: Long): String = bytes(bps) + "/s"
}
