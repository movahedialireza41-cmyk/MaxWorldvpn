package com.maxworld.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Looks for a newer GitHub release. Set [REPO] to "username/repo" to turn it on.
 * The check goes through the local proxy while the VPN is connected, so it works when GitHub is filtered.
 */
object Updater {
    const val REPO = "" // example: "alireza/MaxWorld"

    class Info(val version: String, val page: String, val apk: String)

    var available by mutableStateOf<Info?>(null)
    var checking by mutableStateOf(false)
    var lastResult by mutableStateOf(0) // 0 not checked, 1 up to date, 2 failed, 3 update found

    fun enabled() = REPO.contains('/')

    private fun nums(v: String): List<Int> =
        v.trim().removePrefix("v").removePrefix("V").split('.', '-').mapNotNull { it.toIntOrNull() }

    fun isNewer(remote: String, local: String): Boolean {
        val a = nums(remote)
        val b = nums(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Blocking; call from a background thread. */
    fun check() {
        if (!enabled()) return
        checking = true
        try {
            val proxy = if (VpnState.state == ConnState.CONNECTED) {
                java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress("127.0.0.1", XrayConfig.HTTP_PORT))
            } else {
                java.net.Proxy.NO_PROXY
            }
            val conn = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection(proxy) as HttpURLConnection
            conn.connectTimeout = 12000
            conn.readTimeout = 12000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "MaxWorld")
            try {
                if (conn.responseCode != 200) {
                    lastResult = 2
                    return
                }
                val o = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val tag = o.optString("tag_name")
                var apk = ""
                val assets = o.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val u = assets.getJSONObject(i).optString("browser_download_url")
                        if (u.endsWith(".apk", true)) {
                            apk = u
                            break
                        }
                    }
                }
                if (tag.isNotEmpty() && isNewer(tag, BuildConfigVersion.NAME)) {
                    available = Info(tag.removePrefix("v"), o.optString("html_url"), apk)
                    lastResult = 3
                } else {
                    available = null
                    lastResult = 1
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            lastResult = 2
        } finally {
            checking = false
        }
    }
}
