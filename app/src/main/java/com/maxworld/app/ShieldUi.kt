package com.maxworld.app

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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

/** The "Shield" section of Settings: ad / tracker blocking lists and the allow list. */
@Composable
fun ShieldSection() {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val observed = tick
    var busy by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }

    fun changed(block: () -> Unit) {
        block()
        Shield.save()
        tick++
    }

    fun refreshAll() {
        if (busy) return
        busy = true
        scope.launch {
            val failed = withContext(Dispatchers.IO) { Shield.sources.filter { it.enabled }.count { Shield.refresh(it) < 0 } }
            busy = false
            tick++
            Toast.makeText(ctx, if (failed == 0) R.string.shield_done else R.string.shield_failed, Toast.LENGTH_SHORT).show()
        }
    }

    fun refreshOne(s: Shield.Source) {
        if (busy) return
        busy = true
        scope.launch {
            val n = withContext(Dispatchers.IO) { Shield.refresh(s) }
            busy = false
            tick++
            Toast.makeText(ctx, if (n >= 0) R.string.shield_done else R.string.shield_failed, Toast.LENGTH_SHORT).show()
        }
    }

    SectionTitle(R.string.shield_title)
    GlassCard(Modifier.fillMaxWidth().fadeSlideIn(70)) {
        SwitchRow(R.string.shield_switch_title, R.string.shield_switch_desc, Shield.enabled) { on ->
            changed { Shield.enabled = on }
            if (on && Shield.sources.isEmpty()) {
                // first use: start with the best-known list so the switch does something right away
                Shield.addPreset("stevenblack")
                refreshOne(Shield.sources.first())
            }
        }
        Spacer(Modifier.height(14.dp))
        SwitchRow(R.string.shield_builtin_title, R.string.shield_builtin_desc, Shield.builtin) { on ->
            changed { Shield.builtin = on }
            if (on && !Geo.ready()) {
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { Geo.download() }
                    tick++
                    Toast.makeText(ctx, if (ok) R.string.geo_done else R.string.geo_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
        if (Shield.builtin && !Geo.ready()) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.geo_missing), color = p.warn, fontSize = 12.sp)
        }

        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.shield_lists), color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.shield_domains_fmt, Shield.totalDomains()), color = p.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        if (Shield.sources.isEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.shield_none), color = p.muted, fontSize = 12.sp)
        }
        Shield.sources.toList().forEach { s ->
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.name, color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(
                        if (s.count > 0) stringResource(R.string.shield_count_fmt, s.count) else stringResource(R.string.shield_not_downloaded),
                        color = if (s.count > 0) p.muted else p.warn, fontSize = 11.sp
                    )
                }
                androidx.compose.material3.Switch(
                    checked = s.enabled,
                    onCheckedChange = { v -> changed { s.enabled = v } },
                    colors = androidx.compose.material3.SwitchDefaults.colors(
                        checkedThumbColor = p.onAccent, checkedTrackColor = p.accent,
                        uncheckedThumbColor = p.muted, uncheckedTrackColor = p.track
                    )
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(stringResource(R.string.shield_update_one), false) { refreshOne(s) }
                Chip(stringResource(R.string.delete), false) { changed { Shield.remove(s.id) } }
            }
        }

        val free = Shield.presets()
        if (free.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                free.forEach { pr ->
                    Chip("+ ${pr.second}", false) {
                        changed { Shield.addPreset(pr.first) }
                        Shield.sources.firstOrNull { it.id == pr.first }?.let { refreshOne(it) }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Field(stringResource(R.string.shield_add_hint), url, { url = it })
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(stringResource(R.string.shield_add), false) {
                if (Shield.addCustom(url)) {
                    val s = Shield.sources.lastOrNull()
                    url = ""
                    tick++
                    if (s != null) refreshOne(s)
                } else {
                    Toast.makeText(ctx, R.string.shield_bad_url, Toast.LENGTH_SHORT).show()
                }
            }
            if (Shield.sources.isNotEmpty()) {
                Chip(if (busy) stringResource(R.string.shield_updating) else stringResource(R.string.shield_update_all), busy) { refreshAll() }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.shield_allow_title), color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Field(stringResource(R.string.shield_allow_hint), Shield.allow, { v -> changed { Shield.allow = v } })

        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.shield_apply_hint), color = p.muted, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.shield_yt_note), color = p.muted, fontSize = 12.sp)
    }
}

/** Card that appears at the top of Settings when a newer release exists. */
@Composable
fun UpdateCard() {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val info = Updater.available ?: return
    GlassCard(Modifier.fillMaxWidth().fadeSlideIn(30)) {
        Text(stringResource(R.string.update_title_fmt, info.version), color = p.accent, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.update_desc), color = p.muted, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        val scope = rememberCoroutineScope()
        when (Updater.dlState) {
            1 -> {
                Text(
                    stringResource(R.string.update_downloading_fmt, Updater.dlPercent),
                    color = p.text, fontSize = 15.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(p.track)) {
                    Box(
                        Modifier
                            .fillMaxWidth(Updater.dlPercent / 100f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Brush.horizontalGradient(listOf(p.accent, p.accent2)))
                    )
                }
            }
            2 -> {
                Text(stringResource(R.string.update_ready), color = p.ok, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Chip(stringResource(R.string.update_install), true) { Updater.install(ctx) }
            }
            else -> {
                if (Updater.dlState == 3) {
                    Text(stringResource(R.string.update_dl_failed), color = p.warn, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(stringResource(R.string.update_download), true) {
                        if (info.apk.isNotEmpty()) {
                            scope.launch(Dispatchers.IO) { Updater.download(ctx.applicationContext, info.apk) }
                        } else {
                            try {
                                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.page)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            } catch (ignored: Exception) {
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "Check for updates" row inside the About card. */
@Composable
fun UpdateCheckRow() {
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    Spacer(Modifier.height(10.dp))
    if (!Updater.enabled()) {
        Text(stringResource(R.string.update_not_set), color = p.muted, fontSize = 12.sp)
        return
    }
    Chip(if (Updater.checking) stringResource(R.string.shield_updating) else stringResource(R.string.update_check), Updater.checking) {
        scope.launch(Dispatchers.IO) { Updater.check() }
    }
    Spacer(Modifier.height(6.dp))
    when (Updater.lastResult) {
        1 -> Text(stringResource(R.string.update_latest), color = p.ok, fontSize = 12.sp)
        2 -> Text(stringResource(R.string.update_failed), color = p.warn, fontSize = 12.sp)
        3 -> Text(stringResource(R.string.update_found_hint), color = p.accent, fontSize = 12.sp)
    }
    if (Updater.lastResult == 2 && Updater.detail.isNotEmpty()) {
        Text(Updater.detail, color = p.muted, fontSize = 11.sp)
    }
    if (Updater.remoteTag.isNotEmpty()) {
        Text(stringResource(R.string.update_status_fmt, Updater.remoteTag, BuildConfigVersion.NAME), color = p.muted, fontSize = 11.sp)
    }
}
