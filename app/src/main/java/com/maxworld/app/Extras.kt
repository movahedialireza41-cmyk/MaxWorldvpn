package com.maxworld.app

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.net.Inet4Address
import java.net.NetworkInterface

/** Changes the launcher name and icon of the app (activity aliases), so it can look like an ordinary app. */
object Disguise {
    private val ALIASES = listOf(".AliasDefault", ".AliasCalc", ".AliasNotes", ".AliasWeather")
    val labels = listOf(R.string.disg_default, R.string.disg_calc, R.string.disg_notes, R.string.disg_weather)

    fun current(ctx: Context): Int {
        val pm = ctx.packageManager
        val i = ALIASES.indexOfFirst {
            pm.getComponentEnabledSetting(ComponentName(ctx.packageName, "com.maxworld.app$it")) ==
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }
        return if (i < 0) 0 else i
    }

    fun set(ctx: Context, index: Int) {
        val pm = ctx.packageManager
        // enable the new one first so there is always a launcher entry
        ALIASES.forEachIndexed { i, a ->
            if (i == index) {
                pm.setComponentEnabledSetting(ComponentName(ctx.packageName, "com.maxworld.app$a"), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            }
        }
        ALIASES.forEachIndexed { i, a ->
            if (i != index) {
                pm.setComponentEnabledSetting(ComponentName(ctx.packageName, "com.maxworld.app$a"), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            }
        }
    }
}

object Lan {
    /** Addresses this phone has on its networks (hotspot usually 192.168.x.1 / 10.x). */
    fun addresses(): List<String> = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.name.startsWith("tun") && !it.name.startsWith("rmnet") }
            .flatMap { n -> n.inetAddresses.toList().filterIsInstance<Inet4Address>().map { it.hostAddress.orEmpty() } }
            .filter { it.isNotEmpty() }
    } catch (e: Exception) {
        emptyList()
    }
}

@Composable
fun ExtrasSection() {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val observed = tick
    var disg by remember { mutableIntStateOf(Disguise.current(ctx)) }

    SectionTitle(R.string.share_title)
    GlassCard(Modifier.fillMaxWidth().fadeSlideIn(180)) {
        SwitchRow(R.string.share_switch, R.string.share_desc, Adv.shareLan) { on ->
            Adv.shareLan = on
            Adv.save()
            tick++
        }
        if (Adv.shareLan) {
            Spacer(Modifier.height(10.dp))
            val addrs = Lan.addresses()
            if (addrs.isEmpty()) {
                Text(stringResource(R.string.share_no_addr), color = p.warn, fontSize = 12.sp)
            } else {
                addrs.forEach { a ->
                    Text(stringResource(R.string.share_addr_fmt, a, XrayConfig.HTTP_PORT, XrayConfig.SOCKS_PORT), color = p.accent, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.share_warn), color = p.warn, fontSize = 12.sp)
        }
    }

    SectionTitle(R.string.disg_title)
    GlassCard(Modifier.fillMaxWidth().fadeSlideIn(185)) {
        Text(stringResource(R.string.disg_desc), color = p.muted, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Disguise.labels.forEachIndexed { i, res ->
                Chip(stringResource(res), disg == i) {
                    disg = i
                    Disguise.set(ctx, i)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.disg_note), color = p.muted, fontSize = 11.sp)
    }
}
