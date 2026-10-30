package com.maxworld.app

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

// ------------------------------------------------------------------ what's new

@Composable
private fun FullScreenDialog(onDismiss: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(p.bgTop, p.bgBottom)))) {
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), content = content)
        }
    }
}

/** Shown once, the first time the app opens after an update. */
@Composable
fun WhatsNewDialog(onDismiss: () -> Unit) {
    val p = LocalPalette.current
    FullScreenDialog(onDismiss) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(40.dp))
            Text(stringResource(R.string.whatsnew_title), color = p.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(
                stringResource(R.string.whatsnew_version_fmt, BuildConfigVersion.NAME),
                color = p.text, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(18.dp))
            GlassCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.whatsnew_body), color = p.text, fontSize = 15.sp, lineHeight = 26.sp)
            }
        }
        PrimaryButton(stringResource(R.string.whatsnew_ok), onDismiss, Modifier.padding(vertical = 16.dp))
    }
}

// ------------------------------------------------------------------ clean IP scanner

object CleanIp {
    class Hit(val ip: String, val ms: Long)

    // Cloudflare's published IPv4 ranges: base address and prefix length.
    private val ranges = listOf(
        "104.16.0.0" to 13, "104.24.0.0" to 14, "172.64.0.0" to 13, "173.245.48.0" to 20,
        "103.21.244.0" to 22, "103.22.200.0" to 22, "103.31.4.0" to 22, "141.101.64.0" to 18,
        "108.162.192.0" to 18, "190.93.240.0" to 20, "188.114.96.0" to 20, "197.234.240.0" to 22,
        "198.41.128.0" to 17, "162.158.0.0" to 15, "131.0.72.0" to 22
    )

    private fun toLong(ip: String): Long = ip.split('.').fold(0L) { a, b -> a * 256 + b.toLong() }
    private fun toIp(v: Long) = "${(v shr 24) and 255}.${(v shr 16) and 255}.${(v shr 8) and 255}.${v and 255}"

    fun randomIps(n: Int): List<String> {
        val rnd = java.util.Random()
        val out = LinkedHashSet<String>()
        while (out.size < n) {
            val (base, bits) = ranges[rnd.nextInt(ranges.size)]
            val size = 1L shl (32 - bits)
            val ip = toLong(base) + (rnd.nextLong() and Long.MAX_VALUE) % (size - 2) + 1
            out.add(toIp(ip))
        }
        return out.toList()
    }

    private val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    })

    /** Time in ms for a TLS handshake with [sni] against [ip]:443, or -1. Only measures; sends no data. */
    fun probe(ip: String, sni: String): Long {
        val start = System.nanoTime()
        return try {
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, trustAll, SecureRandom())
            val plain = java.net.Socket()
            plain.connect(InetSocketAddress(ip, 443), 2500)
            val ssl = ctx.socketFactory.createSocket(plain, sni, 443, true) as SSLSocket
            try {
                ssl.soTimeout = 3000
                val params = ssl.sslParameters
                params.serverNames = listOf(SNIHostName(sni))
                ssl.sslParameters = params
                ssl.startHandshake()
            } finally {
                try { ssl.close() } catch (ignored: Exception) {}
            }
            (System.nanoTime() - start) / 1_000_000
        } catch (e: Exception) {
            -1L
        }
    }

    suspend fun scan(sni: String, count: Int, onProgress: (Int) -> Unit): List<Hit> = coroutineScope {
        val gate = Semaphore(16)
        var done = 0
        val results = randomIps(count).map { ip ->
            async(Dispatchers.IO) {
                val ms = gate.withPermit { probe(ip, sni) }
                synchronized(this@CleanIp) { done++; onProgress(done) }
                if (ms >= 0) Hit(ip, ms) else null
            }
        }.awaitAll()
        results.filterNotNull().sortedBy { it.ms }
    }
}

@Composable
fun CleanIpDialog(onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val server = ConfigStore.selected()
    val sni = server?.let { it.sni.ifEmpty { it.host.ifEmpty { it.address } } } ?: ""
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var hits by remember { mutableStateOf<List<CleanIp.Hit>>(emptyList()) }
    var finished by remember { mutableStateOf(false) }
    val total = 80

    FullScreenDialog(onDismiss) {
        Text(
            stringResource(R.string.cip_title), color = p.text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(top = 24.dp)
        )
        Text(
            stringResource(R.string.cip_desc), color = p.muted, fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
        )
        if (server == null || server.security != "tls" || sni.isEmpty()) {
            Text(stringResource(R.string.cip_need_server), color = p.warn, fontSize = 14.sp)
        } else {
            Text(stringResource(R.string.cip_server_fmt, server.displayName(), sni), color = p.accent, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            Chip(
                if (running) stringResource(R.string.cip_scanning_fmt, progress, total) else stringResource(R.string.cip_start),
                running
            ) {
                if (!running) {
                    running = true
                    finished = false
                    progress = 0
                    hits = emptyList()
                    scope.launch {
                        val r = CleanIp.scan(sni, total) { n -> progress = n }
                        hits = r.take(10)
                        running = false
                        finished = true
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            if (finished && hits.isEmpty()) {
                Text(stringResource(R.string.cip_none), color = p.warn, fontSize = 13.sp)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                hits.forEach { h ->
                    GlassCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(h.ip, color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text("${h.ms} ms", color = p.ok, fontSize = 12.sp)
                            }
                            Chip(stringResource(R.string.cip_use), true) {
                                val c = ConfigStore.selected()
                                if (c != null) {
                                    val ws = c.network == "ws" || c.network == "httpupgrade" || c.network == "xhttp"
                                    ConfigStore.update(
                                        c.copy(
                                            address = h.ip,
                                            sni = c.sni.ifEmpty { c.address },
                                            host = if (ws) c.host.ifEmpty { c.address } else c.host
                                        )
                                    )
                                    Toast.makeText(ctx, R.string.cip_applied, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            }
        }
        if (server == null || server.security != "tls" || sni.isEmpty()) Spacer(Modifier.weight(1f))
        PrimaryButton(stringResource(R.string.close), onDismiss, Modifier.padding(vertical = 14.dp))
    }
}
