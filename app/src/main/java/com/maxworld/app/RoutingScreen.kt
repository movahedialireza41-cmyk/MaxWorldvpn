package com.maxworld.app

import android.content.Intent
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RoutingSection() {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    // Adv holds plain fields; this counter makes the UI re-read them after every change.
    var tick by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val observed = tick
    fun change(block: () -> Unit) {
        block()
        Adv.save()
        tick++
    }

    var showApps by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    var geoBusy by remember { mutableStateOf(false) }

    fun downloadGeo() {
        if (geoBusy) return
        geoBusy = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) { Geo.download() }
            geoBusy = false
            tick++
            Toast.makeText(ctx, if (ok) R.string.geo_done else R.string.geo_failed, Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxWidth()) {
        SectionTitle(R.string.rt_title)

            // ---------------------------------------- apps that skip the VPN (main feature)
            GlassCard(Modifier.fillMaxWidth().fadeSlideIn(60)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { showApps = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ForkIcon()
                    Spacer(Modifier.size(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.split_title),
                            color = p.text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            stringResource(R.string.split_sub),
                            color = p.muted, fontSize = 13.sp
                        )
                        if (Adv.splitApps.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.split_count_fmt, Adv.splitApps.size),
                                color = p.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        if (LocalLayoutDirection.current == LayoutDirection.Rtl) "‹" else "›",
                        color = p.muted, fontSize = 28.sp
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            // ---------------------------------------- Iranian sites without VPN
            GlassCard(Modifier.fillMaxWidth().fadeSlideIn(120)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.route_iran),
                            color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Bold
                        )
                        Text(
                            stringResource(R.string.iran_direct_desc),
                            color = p.muted, fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = Adv.routePreset == 1,
                        onCheckedChange = { on ->
                            change { Adv.routePreset = if (on) 1 else 0 }
                            if (on && !Geo.ready()) downloadGeo()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = p.onAccent, checkedTrackColor = p.accent,
                            uncheckedThumbColor = p.muted, uncheckedTrackColor = p.track
                        )
                    )
                }
                if (Adv.routePreset == 1 && !Geo.ready()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(if (geoBusy) R.string.geo_downloading else R.string.geo_missing),
                        color = p.warn, fontSize = 12.sp
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            // ---------------------------------------- ad blocking
            GlassCard(Modifier.fillMaxWidth().fadeSlideIn(150)) {
                SwitchRow(R.string.ads_title, R.string.ads_desc, Adv.blockAds) { v ->
                    change { Adv.blockAds = v }
                    if (v && !Geo.ready()) downloadGeo()
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.rt_apply_hint), color = p.muted, fontSize = 12.sp)

            // ---------------------------------------- advanced (hidden until opened)
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { showAdvanced = !showAdvanced }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.adv_title),
                    color = p.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(if (showAdvanced) "⌃" else "⌄", color = p.accent, fontSize = 18.sp)
            }

            if (showAdvanced) {
                // blocking ads, own rules, geo files
                SectionTitle(R.string.route_title)
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.route_custom),
                        color = p.accent, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { showRules = true }
                    )
                    Spacer(Modifier.height(14.dp))

                    val ready = Geo.ready()
                    Text(stringResource(R.string.geo_title), color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (ready) stringResource(R.string.geo_ready_fmt, Geo.sizeMb()) else stringResource(R.string.geo_missing),
                        color = if (ready) p.ok else p.warn,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip(
                            when {
                                geoBusy -> stringResource(R.string.geo_downloading)
                                ready -> stringResource(R.string.geo_update)
                                else -> stringResource(R.string.geo_download)
                            },
                            selected = geoBusy
                        ) { downloadGeo() }
                        if (ready) {
                            Chip(stringResource(R.string.delete), false) {
                                Geo.delete()
                                tick++
                            }
                        }
                    }
                }

                SectionTitle(R.string.dns_title)
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Cloudflare", "Google", "Quad9", "AdGuard", stringResource(R.string.dns_custom)).forEachIndexed { i, label ->
                            Chip(label, Adv.dnsPreset == i) { change { Adv.dnsPreset = i } }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip(stringResource(R.string.dns_type_udp), !Adv.dnsDoh) { change { Adv.dnsDoh = false } }
                        Chip(stringResource(R.string.dns_type_doh), Adv.dnsDoh) { change { Adv.dnsDoh = true } }
                    }
                    if (Adv.dnsPreset == 4) {
                        Spacer(Modifier.height(10.dp))
                        Field(stringResource(R.string.dns_custom_hint), Adv.dnsCustom, { v -> change { Adv.dnsCustom = v } })
                    }
                }

                SectionTitle(R.string.tweaks_title)
                GlassCard(Modifier.fillMaxWidth()) {
                    SwitchRow(R.string.mux_title, R.string.mux_desc, Adv.mux) { change { Adv.mux = it } }
                    if (Adv.mux) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(4, 8, 16).forEach { n -> Chip("$n", Adv.muxConc == n) { change { Adv.muxConc = n } } }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    SwitchRow(R.string.frag_title, R.string.frag_desc, Adv.fragment) { change { Adv.fragment = it } }
                    if (Adv.fragment) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("100-200" to "10-20", "50-100" to "5-10", "10-50" to "5-10").forEach { (len, interval) ->
                                Chip(len, Adv.fragLength == len) {
                                    change {
                                        Adv.fragLength = len
                                        Adv.fragInterval = interval
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(stringResource(R.string.fp_title), color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("chrome", "firefox", "safari", "ios", "android", "random").forEach { f ->
                            Chip(f, Adv.fingerprint == f) { change { Adv.fingerprint = f } }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    SwitchRow(R.string.ipv6_title, R.string.ipv6_desc, Adv.ipv6) { change { Adv.ipv6 = it } }
                    Spacer(Modifier.height(14.dp))
                    Text(stringResource(R.string.local_proxy), color = p.muted, fontSize = 12.sp)
                }
        }
    }

    if (showApps) {
        AppsDialog(onDismiss = { showApps = false }, onChanged = { tick++ })
    }
    if (showRules) {
        RulesDialog(
            onDismiss = { showRules = false },
            onSave = { text ->
                change { Adv.customRules = text }
                showRules = false
            }
        )
    }
}

/** Rounded tile with a forking arrow, like the "apps without VPN" icon. */
@Composable
private fun ForkIcon() {
    val p = LocalPalette.current
    Box(
        Modifier
            .size(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(p.accent2.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(26.dp)) {
            val s = size.minDimension
            val w = 2.6.dp.toPx()
            val c = p.accent2
            val mid = Offset(s * 0.5f, s * 0.58f)
            drawLine(c, Offset(s * 0.5f, s * 0.95f), mid, strokeWidth = w, cap = StrokeCap.Round)
            drawLine(c, mid, Offset(s * 0.18f, s * 0.2f), strokeWidth = w, cap = StrokeCap.Round)
            drawLine(c, mid, Offset(s * 0.82f, s * 0.2f), strokeWidth = w, cap = StrokeCap.Round)
            drawLine(c, Offset(s * 0.18f, s * 0.2f), Offset(s * 0.18f, s * 0.46f), strokeWidth = w, cap = StrokeCap.Round)
            drawLine(c, Offset(s * 0.18f, s * 0.2f), Offset(s * 0.44f, s * 0.2f), strokeWidth = w, cap = StrokeCap.Round)
            drawLine(c, Offset(s * 0.82f, s * 0.2f), Offset(s * 0.82f, s * 0.46f), strokeWidth = w, cap = StrokeCap.Round)
            drawLine(c, Offset(s * 0.82f, s * 0.2f), Offset(s * 0.56f, s * 0.2f), strokeWidth = w, cap = StrokeCap.Round)
        }
    }
}

// ------------------------------------------------------------------- apps list

private class AppEntry(val label: String, val pkg: String, val icon: Drawable)

/** Banks and most Iranian apps use an "ir" part in their package name (ir.xyz.app, com.xyz.ir). */
private fun looksIranian(pkg: String): Boolean =
    pkg.split('.').contains("ir") || pkg.startsWith("cab.snapp") || pkg.startsWith("com.farsitel")

@Composable
private fun AppsDialog(onDismiss: () -> Unit, onChanged: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(Adv.splitApps) }

    fun commit(next: Set<String>) {
        selected = next
        Adv.splitApps = next
        // These apps skip the VPN; with nothing ticked the feature is simply off.
        Adv.splitMode = if (next.isEmpty()) 0 else 2
        Adv.save()
        onChanged()
    }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val chosen = Adv.splitApps
            pm.queryIntentActivities(intent, 0)
                .distinctBy { it.activityInfo.packageName }
                .filter { it.activityInfo.packageName != ctx.packageName }
                .map { AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.loadIcon(pm)) }
                .sortedWith(compareBy({ it.pkg !in chosen }, { it.label.lowercase() }))
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(p.bgTop, p.bgBottom)))
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.apps_pick_title),
                            color = p.text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold
                        )
                        Text(stringResource(R.string.split_hint_pick), color = p.muted, fontSize = 13.sp)
                    }
                    val back = RoundedCornerShape(16.dp)
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(back)
                            .background(p.card)
                            .border(1.dp, p.cardBorder, back)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (LocalLayoutDirection.current == LayoutDirection.Rtl) "›" else "‹",
                            color = p.text, fontSize = 26.sp
                        )
                    }
                }

                AppSearch(query) { query = it }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.split_hint_apply), color = p.muted, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))

                val list = apps
                if (list != null) {
                    Text(
                        stringResource(R.string.split_auto),
                        color = p.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { commit(selected + list.filter { looksIranian(it.pkg) }.map { it.pkg }) }
                            .padding(vertical = 6.dp)
                    )
                }
                Spacer(Modifier.height(6.dp))

                if (list == null) {
                    Text(stringResource(R.string.apps_loading), color = p.muted, fontSize = 14.sp)
                } else {
                    val shown = list.filter { query.isBlank() || it.label.contains(query, true) }
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(shown, key = { it.pkg }) { app ->
                            val on = app.pkg in selected
                            val bmp = remember(app.pkg) { app.icon.toBitmap(96, 96).asImageBitmap() }
                            val shape = RoundedCornerShape(26.dp)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(shape)
                                    .background(p.card)
                                    .border(1.dp, if (on) p.accent else p.cardBorder, shape)
                                    .clickable { commit(if (on) selected - app.pkg else selected + app.pkg) }
                                    .padding(horizontal = 18.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(bitmap = bmp, contentDescription = null, modifier = Modifier.size(38.dp))
                                Spacer(Modifier.size(14.dp))
                                Text(
                                    app.label,
                                    color = p.text,
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                Checkbox(
                                    checked = on,
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = p.accent,
                                        uncheckedColor = p.text,
                                        checkmarkColor = p.onAccent
                                    )
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun AppSearch(value: String, onChange: (String) -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(26.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(p.card)
            .border(1.dp, p.cardBorder, shape)
            .padding(horizontal = 22.dp, vertical = 20.dp)
    ) {
        if (value.isEmpty()) {
            Text(stringResource(R.string.apps_search), color = p.muted, fontSize = 16.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = p.text, fontSize = 16.sp),
            cursorBrush = SolidColor(p.accent),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ------------------------------------------------------------------ custom rules

@Composable
private fun RulesDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val p = LocalPalette.current
    var text by remember { mutableStateOf(Adv.customRules) }
    val shape = RoundedCornerShape(16.dp)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(p.bgTop, p.bgBottom)))
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                Text(
                    stringResource(R.string.route_custom),
                    color = p.text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(top = 22.dp, bottom = 6.dp)
                )
                Text(stringResource(R.string.route_custom_hint), color = p.muted, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(shape)
                        .background(p.card)
                        .border(1.dp, p.cardBorder, shape)
                        .padding(14.dp)
                ) {
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        textStyle = TextStyle(color = p.text, fontSize = 14.sp),
                        cursorBrush = SolidColor(p.accent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PrimaryButton(stringResource(R.string.cancel), onDismiss, modifier = Modifier.weight(1f), filled = false)
                    PrimaryButton(stringResource(R.string.save), { onSave(text) }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
