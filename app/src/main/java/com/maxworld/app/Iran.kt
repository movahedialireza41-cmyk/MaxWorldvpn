package com.maxworld.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Known Iranian apps (banks, shops, messengers, taxis, media) plus a name-based guess. */
object IranApps {
    private val known = setOf(
        // taxis, delivery, shopping
        "cab.snapp.passenger", "cab.snapp.driver", "com.snappfood.android", "ir.tapsi.passenger", "ir.nobitex.app",
        "com.digikala", "ir.divar", "ir.torob.app", "ir.basalam.app", "ir.alibaba", "ir.ostadkar.customer",
        "ir.mci.ecareapp", "com.mtn.irancell", "ir.rightel.myrightel", "ir.shatelland.myshatel",
        // messengers and social
        "ir.nasim", "ir.eitaa.messenger", "org.rubika.messenger", "ir.resaneh1.iptv", "ir.rubika",
        "ir.medu.shad", "com.gap.messenger", "ir.soroush.messenger", "ir.iGap", "net.iGap",
        // media
        "com.aparat", "ir.filimo.app", "com.farsitel.bazaar", "ir.mservices.market", "ir.namava.app", "ir.telewebion",
        "ir.mci.ecareapp", "ir.irib.telewebion", "com.namava",
        // money and banks
        "ir.mobillet.app", "com.pasargad.mobile", "ir.bmi.mobile", "ir.mellat.mobilebank", "com.samanpl.sepah",
        "ir.sep.sdk.sam", "com.sadad.melli", "ir.bsi.mobile", "ir.tejaratbank.tata.mobile.android.tejarat",
        "ir.iranpay.shaparak", "ir.asanpardakht.android.appayment", "com.fanap.azki", "ir.hamrah.parsian",
        "com.refahbank", "ir.postbank", "ir.shahr.android", "ir.keshavarzi.mobile", "ir.bki.mobile",
        "ir.tosan.mb.blu", "ir.blubank.app", "ir.ansarbank.mobile", "ir.eghtesadnovin.mobile",
        "ir.sina.mobile", "com.ayandeh.mobile", "ir.gharzolhasaneh.mobile", "ir.mizan.mobile",
        // government and daily life
        "ir.ghabzino.app", "ir.top.android", "ir.part.app.merat", "ir.irancell.nodi", "ir.mamnoon.app", "ir.ketabrah.app",
        "ir.taaghche.app", "ir.fidibo.app", "ir.cinematicket.app", "ir.ticket.cinematicket", "ir.tikato.app",
        "ir.neshan", "org.neshan.neshan", "ir.balad", "ir.carpino.passenger", "com.sibche.aspardproject"
    )

    private fun looksIranian(pkg: String): Boolean =
        pkg in known || pkg.split('.').contains("ir") || pkg.startsWith("cab.snapp") || pkg.startsWith("com.farsitel")

    /** Package names of installed launcher apps that look Iranian. */
    fun installed(ctx: Context): List<String> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != ctx.packageName && looksIranian(it) }
    }

    /** Adds every installed Iranian app to the "skip the VPN" list. Returns how many were added. */
    fun apply(ctx: Context): Int {
        val found = installed(ctx)
        val before = Adv.splitApps.size
        Adv.splitApps = Adv.splitApps + found
        if (Adv.splitApps.isNotEmpty()) Adv.splitMode = 2
        Adv.save()
        return Adv.splitApps.size - before
    }
}

/** Wi-Fi networks the user trusts: the VPN turns itself off there and stays off. */
object TrustedWifi {
    private var prefs: SharedPreferences? = null
    var enabled = false
    var ssids: Set<String> = emptySet()

    fun init(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences("trusted_wifi", Context.MODE_PRIVATE)
        prefs = p
        enabled = p.getBoolean("enabled", false)
        ssids = p.getStringSet("ssids", emptySet())?.toSet() ?: emptySet()
    }

    fun save() {
        prefs?.edit()?.putBoolean("enabled", enabled)?.putStringSet("ssids", ssids)?.apply()
    }

    fun hasPermission(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Name of the Wi-Fi we are on, or null (no Wi-Fi, no permission, or location is switched off). */
    @Suppress("DEPRECATION")
    fun currentSsid(ctx: Context): String? {
        try {
            if (!hasPermission(ctx)) return null
            val cm = ctx.getSystemService(ConnectivityManager::class.java)
            val onWifi = cm.allNetworks.any {
                val c = cm.getNetworkCapabilities(it)
                c != null && c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && !c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            }
            if (!onWifi) return null
            val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val s = wm.connectionInfo?.ssid?.trim('"') ?: return null
            return if (s.isEmpty() || s == "<unknown ssid>") null else s
        } catch (e: Exception) {
            return null
        }
    }

    fun isTrustedNow(ctx: Context): Boolean {
        if (!enabled || ssids.isEmpty()) return false
        val s = currentSsid(ctx) ?: return false
        return s in ssids
    }

    /** Called on every network change. Returns true when we are on a trusted network (VPN is stopped if running). */
    fun onNetwork(ctx: Context): Boolean {
        init(ctx)
        if (!isTrustedNow(ctx)) return false
        if (VpnState.state != ConnState.DISCONNECTED) {
            AppLog.add("Trusted Wi-Fi: VPN turned off")
            try {
                ctx.startService(Intent(ctx, MaxVpnService::class.java).setAction(MaxVpnService.ACTION_DISCONNECT))
            } catch (ignored: Exception) {
            }
        }
        return true
    }
}

@Composable
fun TrustedWifiSection() {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    TrustedWifi.init(ctx)
    var tick by remember { mutableStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val observed = tick

    fun addCurrent() {
        val s = TrustedWifi.currentSsid(ctx)
        if (s == null) {
            Toast.makeText(ctx, R.string.twifi_no_ssid, Toast.LENGTH_LONG).show()
        } else {
            TrustedWifi.ssids = TrustedWifi.ssids + s
            TrustedWifi.enabled = true
            TrustedWifi.save()
            AutoConnect.apply(ctx)
            tick++
        }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) addCurrent() else Toast.makeText(ctx, R.string.twifi_need_perm, Toast.LENGTH_LONG).show()
    }

    SectionTitle(R.string.twifi_title)
    GlassCard(Modifier.fillMaxWidth().fadeSlideIn(165)) {
        SwitchRow(R.string.twifi_switch, R.string.twifi_desc, TrustedWifi.enabled) { on ->
            TrustedWifi.enabled = on
            TrustedWifi.save()
            AutoConnect.apply(ctx)
            tick++
        }
        Spacer(Modifier.height(12.dp))
        if (TrustedWifi.ssids.isEmpty()) {
            Text(stringResource(R.string.twifi_none), color = p.muted, fontSize = 12.sp)
        }
        TrustedWifi.ssids.sorted().forEach { s ->
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(s, color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
                Chip(stringResource(R.string.delete), false) {
                    TrustedWifi.ssids = TrustedWifi.ssids - s
                    TrustedWifi.save()
                    tick++
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(stringResource(R.string.twifi_add_current), false) {
                if (TrustedWifi.hasPermission(ctx)) addCurrent() else permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.twifi_note), color = p.muted, fontSize = 11.sp)
    }
}
