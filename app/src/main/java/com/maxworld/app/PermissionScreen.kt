package com.maxworld.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

fun notifGranted(ctx: Context): Boolean =
    Build.VERSION.SDK_INT < 33 ||
        ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

@Composable
fun PermissionScreen(onDone: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current

    // Bumped on every resume so the rows re-read the real permission state.
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(ctx) {
        val lifecycle = (ctx as ComponentActivity).lifecycle
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    @Suppress("UNUSED_VARIABLE") val t = tick
    val notifOk = notifGranted(ctx)
    val batteryOk = Keepalive.isIgnoringBattery(ctx)
    val miuiOk = Prefs.miuiDone(ctx)

    Box(Modifier.fillMaxSize()) {
        WorldBackground(connected = false)
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 24.dp)
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.perm_title),
                color = p.text,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.fadeSlideIn()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.perm_subtitle),
                color = p.muted,
                fontSize = 15.sp,
                modifier = Modifier.fadeSlideIn(60)
            )
            Spacer(Modifier.height(28.dp))

            PermRow(
                title = R.string.perm_notif_title, desc = R.string.perm_notif_desc,
                granted = notifOk, delay = 120
            ) {
                if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            Spacer(Modifier.height(12.dp))
            PermRow(
                title = R.string.perm_battery_title, desc = R.string.perm_battery_desc,
                granted = batteryOk, delay = 180
            ) { Keepalive.requestIgnoreBattery(ctx) }

            if (Keepalive.isMiui()) {
                Spacer(Modifier.height(12.dp))
                PermRow(
                    title = R.string.perm_miui_title, desc = R.string.perm_miui_desc,
                    granted = miuiOk, delay = 240
                ) {
                    Keepalive.openAutostart(ctx)
                    Prefs.setMiuiDone(ctx, true)
                    tick++
                }
            }

            Spacer(Modifier.weight(1f))
            if (!notifOk) {
                Text(
                    stringResource(R.string.perm_hint),
                    color = p.warn,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
            }
            PrimaryButton(
                text = stringResource(R.string.perm_continue),
                onClick = { if (notifOk) onDone() },
                colors = if (notifOk) listOf(p.accent, p.accent2) else listOf(p.track, p.track)
            )
        }
    }
}

@Composable
private fun PermRow(title: Int, desc: Int, granted: Boolean, delay: Int, onAllow: () -> Unit) {
    val p = LocalPalette.current
    GlassCard(Modifier.fillMaxWidth().fadeSlideIn(delay)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(desc), color = p.muted, fontSize = 12.sp)
            }
            if (granted) {
                Text("✓", color = p.ok, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            } else {
                Text(
                    stringResource(R.string.perm_allow),
                    color = p.accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(8.dp)
                        .clickable { onAllow() }
                )
            }
        }
    }
}
