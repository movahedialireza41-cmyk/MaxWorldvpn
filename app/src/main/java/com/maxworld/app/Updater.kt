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
    const val REPO = "movahedialireza41-cmyk/MaxWorldvpn"

    class Info(val version: String, val page: String, val apk: String)

    var available by mutableStateOf<Info?>(null)
    var checking by mutableStateOf(false)
    var lastResult by mutableStateOf(0) // 0 not checked, 1 up to date, 2 failed, 3 update found

    fun enabled() = REPO.contains('/')

    // in-app download: 0 idle, 1 downloading, 2 ready to install, 3 failed
    var dlState by mutableStateOf(0)
    var dlPercent by mutableStateOf(0)
    private var apkFile: java.io.File? = null

    /** Blocking; call from a background thread. Saves the APK in the cache folder and reports progress. */
    fun download(ctx: android.content.Context, url: String) {
        if (dlState == 1 || url.isEmpty()) return
        dlState = 1
        dlPercent = 0
        try {
            val dir = java.io.File(ctx.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val out = java.io.File(dir, "MaxWorld-update.apk")
            val proxy = if (VpnState.state == ConnState.CONNECTED) {
                java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress("127.0.0.1", XrayConfig.HTTP_PORT))
            } else {
                java.net.Proxy.NO_PROXY
            }
            val conn = URL(url).openConnection(proxy) as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.setRequestProperty("User-Agent", "MaxWorld")
            conn.instanceFollowRedirects = true
            try {
                if (conn.responseCode !in 200..299) {
                    dlState = 3
                    return
                }
                val total = conn.contentLengthLong
                var got = 0L
                conn.inputStream.use { input ->
                    out.outputStream().use { o ->
                        val buf = ByteArray(32768)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            o.write(buf, 0, n)
                            got += n
                            if (total > 0) dlPercent = ((got * 100) / total).toInt().coerceIn(0, 100)
                        }
                    }
                }
                if (out.length() < 1_000_000L || (total > 0 && got < total)) {
                    out.delete()
                    dlState = 3
                    return
                }
                apkFile = out
                dlPercent = 100
                dlState = 2
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            dlState = 3
        }
    }

    /** Opens Android's installer for the downloaded file (asks for the "install unknown apps" permission first). */
    fun install(ctx: android.content.Context) {
        val f = apkFile?.takeIf { it.exists() } ?: run {
            dlState = 0
            return
        }
        try {
            if (!ctx.packageManager.canRequestPackageInstalls()) {
                ctx.startActivity(
                    android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        android.net.Uri.parse("package:" + ctx.packageName)
                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return
            }
            val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
            ctx.startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            dlState = 3
        }
    }

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
