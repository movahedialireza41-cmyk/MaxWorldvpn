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

    class Info(val version: String, val page: String, val apk: String, val notes: String = "")

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

    /** What the last check saw: the newest version on GitHub, or the reason it failed. Shown in About. */
    var remoteTag by mutableStateOf("")
    var detail by mutableStateOf("")

    @Volatile
    private var ticket = 0

    private fun proxy(): java.net.Proxy =
        if (VpnState.state == ConnState.CONNECTED) {
            java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress("127.0.0.1", XrayConfig.HTTP_PORT))
        } else {
            java.net.Proxy.NO_PROXY
        }

    /** Newest release tag. First asks github.com for the "latest" redirect (no API limits), then the API. */
    private fun fetchLatestTag(): String? {
        try {
            val c = URL("https://github.com/$REPO/releases/latest").openConnection(proxy()) as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 10000
            c.readTimeout = 10000
            c.setRequestProperty("User-Agent", "MaxWorld")
            try {
                val code = c.responseCode
                val loc = c.getHeaderField("Location")
                if (code in 300..399 && loc != null && loc.contains("/tag/")) {
                    return java.net.URLDecoder.decode(loc.substringAfter("/tag/").substringBefore('?').trim(), "UTF-8")
                }
                detail = "github.com HTTP $code"
            } finally {
                c.disconnect()
            }
        } catch (e: Exception) {
            detail = e.javaClass.simpleName
        }
        try {
            val c = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection(proxy()) as HttpURLConnection
            c.connectTimeout = 10000
            c.readTimeout = 10000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", "MaxWorld")
            try {
                val code = c.responseCode
                if (code == 200) {
                    val tag = JSONObject(c.inputStream.bufferedReader().use { it.readText() }).optString("tag_name")
                    if (tag.isNotEmpty()) return tag
                    detail = "no tag"
                } else {
                    detail = "API HTTP $code"
                }
            } finally {
                c.disconnect()
            }
        } catch (e: Exception) {
            detail = e.javaClass.simpleName
        }
        return null
    }

    /** Picks the Persian or English part of RELEASE_NOTES.md ("## fa" / "## en" headings). */
    private fun pickNotes(text: String): String {
        val sections = HashMap<String, StringBuilder>()
        var cur: StringBuilder? = null
        for (line in text.lines()) {
            val h = line.trim().lowercase()
            if (h == "## fa" || h == "## en") {
                cur = StringBuilder().also { sections[h.removePrefix("## ")] = it }
            } else cur?.append(line)?.append('\n')
        }
        val lang = if (java.util.Locale.getDefault().language == "fa") "fa" else "en"
        return (sections[lang] ?: sections["en"])?.toString()?.trim() ?: text.trim()
    }

    /** Release notes of [tag]: the RELEASE_NOTES.md file of that version, else the release description. */
    private fun fetchNotes(tag: String): String {
        try {
            val c = URL("https://raw.githubusercontent.com/$REPO/$tag/RELEASE_NOTES.md").openConnection(proxy()) as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.setRequestProperty("User-Agent", "MaxWorld")
            try {
                if (c.responseCode == 200) {
                    val text = c.inputStream.bufferedReader().use { it.readText().take(20000) }
                    if (text.isNotBlank()) return pickNotes(text)
                }
            } finally {
                c.disconnect()
            }
        } catch (ignored: Exception) {
        }
        try {
            val c = URL("https://api.github.com/repos/$REPO/releases/tags/$tag").openConnection(proxy()) as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", "MaxWorld")
            try {
                if (c.responseCode == 200) {
                    val body = JSONObject(c.inputStream.bufferedReader().use { it.readText() }).optString("body")
                    if (body.isNotBlank() && !body.startsWith("MaxWorld ")) return pickNotes(body)
                }
            } finally {
                c.disconnect()
            }
        } catch (ignored: Exception) {
        }
        return ""
    }

    /** Blocking; call from a background thread. A second call replaces one that is still running. */
    fun check() {
        if (!enabled()) return
        val mine = ++ticket
        checking = true
        detail = ""
        val pool = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            val job = pool.submit(java.util.concurrent.Callable<String?> { fetchLatestTag() })
            val tag = try {
                job.get(25, java.util.concurrent.TimeUnit.SECONDS)
            } catch (e: java.util.concurrent.TimeoutException) {
                job.cancel(true)
                detail = "timeout"
                null
            } catch (e: Exception) {
                detail = e.javaClass.simpleName
                null
            }
            if (mine != ticket) return
            if (tag == null) {
                lastResult = 2
                return
            }
            remoteTag = tag
            if (isNewer(tag, BuildConfigVersion.NAME)) {
                val ver = tag.removePrefix("v").removePrefix("V")
                available = Info(
                    ver,
                    "https://github.com/$REPO/releases/tag/$tag",
                    "https://github.com/$REPO/releases/download/$tag/MaxWorld-$ver.apk",
                    fetchNotes(tag)
                )
                lastResult = 3
            } else {
                available = null
                lastResult = 1
            }
        } finally {
            pool.shutdownNow()
            if (mine == ticket) checking = false
        }
    }
}
