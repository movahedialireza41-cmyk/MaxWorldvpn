package com.maxworld.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Leak test. MaxWorld itself is kept outside the tunnel, so a plain request from the app shows the
 * real connection, and a request through the local proxy shows what the world sees while the VPN is on.
 */
object Leak {
    class Trace(val ip: String, val country: String)

    class Result(
        val connected: Boolean,
        val real: Trace?,
        val vpn: Trace?,
        val ipOk: Boolean,
        val killOn: Boolean,
        val dnsPrivate: Boolean,
        val quicBlocked: Boolean
    )

    private fun trace(proxy: java.net.Proxy): Trace? = try {
        val c = URL("https://www.cloudflare.com/cdn-cgi/trace").openConnection(proxy) as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "MaxWorld")
        try {
            val map = c.inputStream.bufferedReader().use { it.readText() }.lines()
                .mapNotNull { l -> l.indexOf('=').takeIf { it > 0 }?.let { l.substring(0, it) to l.substring(it + 1) } }.toMap()
            val ip = map["ip"].orEmpty()
            if (ip.isEmpty()) null else Trace(ip, map["loc"].orEmpty())
        } finally {
            c.disconnect()
        }
    } catch (e: Exception) {
        null
    }

    fun run(ctx: android.content.Context): Result {
        val connected = VpnState.state == ConnState.CONNECTED
        val real = trace(java.net.Proxy.NO_PROXY)
        val vpn = if (connected) trace(java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress("127.0.0.1", XrayConfig.HTTP_PORT))) else null
        val ipOk = connected && vpn != null && (real == null || real.ip != vpn.ip)
        // DNS is private when queries go through the tunnel's resolver over HTTPS, or the config
        // uses its own resolver list instead of the phone's (always the case in MaxWorld).
        return Result(connected, real, vpn, ipOk, Prefs.killSwitch(ctx), Adv.dnsDoh, Shield.enabled && Shield.blockQuic)
    }
}

@Composable
private fun LeakRow(title: String, ok: Boolean?, detail: String) {
    val p = LocalPalette.current
    GlassCard(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                when (ok) { true -> "✓"; false -> "✕"; null -> "•" },
                color = when (ok) { true -> p.ok; false -> p.danger; null -> p.muted },
                fontSize = 24.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(end = 14.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(title, color = p.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(detail, color = p.muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun LeakDialog(onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var res by remember { mutableStateOf<Leak.Result?>(null) }

    fun go() {
        if (running) return
        running = true
        scope.launch {
            res = withContext(Dispatchers.IO) { Leak.run(ctx.applicationContext) }
            running = false
        }
    }

    FullScreenDialog(onDismiss) {
        Text(
            stringResource(R.string.leak_title), color = p.text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(top = 24.dp)
        )
        Text(
            stringResource(R.string.leak_desc), color = p.muted, fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )
        Chip(if (running) stringResource(R.string.leak_running) else stringResource(R.string.leak_start), running) { go() }
        Spacer(Modifier.height(14.dp))
        val r = res
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (r != null) {
                if (!r.connected) {
                    Text(stringResource(R.string.leak_not_connected), color = p.warn, fontSize = 14.sp, modifier = Modifier.padding(bottom = 10.dp))
                }
                LeakRow(
                    stringResource(R.string.leak_ip_title),
                    if (r.connected) r.ipOk else null,
                    when {
                        !r.connected -> stringResource(R.string.leak_ip_real_fmt, r.real?.ip ?: "?", r.real?.country ?: "")
                        r.ipOk -> stringResource(R.string.leak_ip_ok_fmt, r.real?.ip ?: "?", r.vpn?.ip ?: "?", r.vpn?.country ?: "")
                        else -> stringResource(R.string.leak_ip_bad)
                    }
                )
                LeakRow(
                    stringResource(R.string.leak_kill_title), r.killOn,
                    stringResource(if (r.killOn) R.string.leak_kill_on else R.string.leak_kill_off)
                )
                LeakRow(
                    stringResource(R.string.leak_dns_title), r.dnsPrivate,
                    stringResource(if (r.dnsPrivate) R.string.leak_dns_on else R.string.leak_dns_off)
                )
                LeakRow(
                    stringResource(R.string.leak_quic_title), r.quicBlocked,
                    stringResource(if (r.quicBlocked) R.string.leak_quic_on else R.string.leak_quic_off)
                )
                Text(stringResource(R.string.leak_note), color = p.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp, bottom = 20.dp))
            }
        }
        Chip(stringResource(R.string.close), false) { onDismiss() }
        Spacer(Modifier.height(20.dp))
    }
}
