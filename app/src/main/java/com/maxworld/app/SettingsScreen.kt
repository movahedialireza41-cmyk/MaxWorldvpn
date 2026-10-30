package com.maxworld.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import libv2ray.Libv2ray
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.provider.Settings

@Composable
fun SettingsScreen(themeId: String, onTheme: (String) -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var boot by remember { mutableStateOf(Prefs.connectOnBoot(ctx)) }
    var autoBest by remember { mutableStateOf(Prefs.autoBest(ctx)) }
    var autoReconnect by remember { mutableStateOf(Prefs.autoReconnect(ctx)) }
    var failover by remember { mutableStateOf(Prefs.autoFailover(ctx)) }
    var notifSpeed by remember { mutableStateOf(Prefs.notifSpeed(ctx)) }
    var speedMb by remember { mutableStateOf(Prefs.speedMb(ctx)) }
    var subHours by remember { mutableStateOf(Prefs.subHours(ctx)) }
    var subAlerts by remember { mutableStateOf(Prefs.subAlerts(ctx)) }
    var autoMode by remember { mutableStateOf(Prefs.autoMode(ctx)) }
    var lockOn by remember { mutableStateOf(AppLock.enabled(ctx)) }
    var bioOn by remember { mutableStateOf(AppLock.bioEnabled(ctx)) }
    var killOn by remember { mutableStateOf(Prefs.killSwitch(ctx)) }
    var showThemes by remember { mutableStateOf(false) }
    var showPin by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    var showDoctor by remember { mutableStateOf(false) }
    var showCleanIp by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(Backup.export().toByteArray()) }
                Toast.makeText(ctx, R.string.toast_backup_saved, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                val n = Backup.import(text)
                if (n < 0) Toast.makeText(ctx, R.string.toast_backup_bad, Toast.LENGTH_SHORT).show()
                else Toast.makeText(ctx, ctx.getString(R.string.toast_backup_ok, n), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, R.string.toast_backup_bad, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val coreVersion = remember {
        try {
            Libv2ray.checkVersionX()
        } catch (ignored: Throwable) {
            "?"
        }
    }

    Box(Modifier.fillMaxSize()) {
        WorldBackground(connected = false)

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 120.dp)
        ) {
            Spacer(Modifier.height(18.dp))
            Text(
                stringResource(R.string.settings_title),
                color = p.text,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.fadeSlideIn()
            )
            if (Updater.available != null) {
                Spacer(Modifier.height(14.dp))
                UpdateCard()
            }

            // ------------------------------------------------------------ theme
            SectionTitle(R.string.theme_title)
            GlassCard(Modifier.fillMaxWidth().fadeSlideIn(60)) {
                Row(
                    Modifier.fillMaxWidth().clickable { showThemes = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Brush.linearGradient(listOf(p.accent, p.accent2)))
                    )
                    Spacer(Modifier.size(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(p.nameRes), color = p.text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                        Text(stringResource(R.string.themes_all), color = p.muted, fontSize = 12.sp)
                    }
                    Text(
                        if (androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl) "‹" else "›",
                        color = p.muted, fontSize = 28.sp
                    )
                }
            }

            // routing: apps without VPN, Iran direct, ad blocking (right under the theme)
            RoutingSection()

            // ad / tracker blocking lists (separate section)
            ShieldSection()

            // ---------------------------------------------------------- general
            SectionTitle(R.string.general_title)
            GlassCard(Modifier.fillMaxWidth().fadeSlideIn(120)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.boot_title), color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.boot_desc), color = p.muted, fontSize = 12.sp)
                    }
                    Switch(
                        checked = boot,
                        onCheckedChange = {
                            boot = it
                            Prefs.setConnectOnBoot(ctx, it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = p.onAccent,
                            checkedTrackColor = p.accent,
                            uncheckedThumbColor = p.muted,
                            uncheckedTrackColor = p.track
                        )
                    )
                }
            }

            // ------------------------------------------------------------- smart
            SectionTitle(R.string.smart_title)
            GlassCard(Modifier.fillMaxWidth().fadeSlideIn(150)) {
                SwitchRow(R.string.auto_best_title, R.string.auto_best_desc, autoBest) {
                    autoBest = it
                    Prefs.setAutoBest(ctx, it)
                }
                Spacer(Modifier.height(14.dp))
                SwitchRow(R.string.auto_reconnect_title, R.string.auto_reconnect_desc, autoReconnect) {
                    autoReconnect = it
                    Prefs.setAutoReconnect(ctx, it)
                }
                Spacer(Modifier.height(14.dp))
                SwitchRow(R.string.failover_title, R.string.failover_desc, failover) {
                    failover = it
                    Prefs.setAutoFailover(ctx, it)
                }
                Spacer(Modifier.height(14.dp))
                SwitchRow(R.string.notif_speed_title, R.string.notif_speed_desc, notifSpeed) {
                    notifSpeed = it
                    Prefs.setNotifSpeed(ctx, it)
                }
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.speed_size_title), color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 25, 50).forEach { mb ->
                        Chip("$mb MB", speedMb == mb) {
                            speedMb = mb
                            Prefs.setSpeedMb(ctx, mb)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.sub_auto_title), color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.sub_auto_desc), color = p.muted, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 6, 12, 24, 48).forEach { h ->
                        Chip(if (h == 0) stringResource(R.string.auto_off) else "$h h", subHours == h) {
                            subHours = h
                            Prefs.setSubHours(ctx, h)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                SwitchRow(R.string.sub_alert_title, R.string.sub_alert_desc, subAlerts) {
                    subAlerts = it
                    Prefs.setSubAlerts(ctx, it)
                }
            }

            // ------------------------------------------------------- auto connect
            SectionTitle(R.string.auto_title)
            GlassCard(Modifier.fillMaxWidth().fadeSlideIn(160)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to R.string.auto_off, 1 to R.string.auto_mobile, 2 to R.string.auto_any).forEach { (mode, res) ->
                        Chip(stringResource(res), autoMode == mode) {
                            autoMode = mode
                            Prefs.setAutoMode(ctx, mode)
                            AutoConnect.apply(ctx)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.auto_desc), color = p.muted, fontSize = 12.sp)
            }

            // ------------------------------------------------------------ security
            SectionTitle(R.string.security_title)
            GlassCard(Modifier.fillMaxWidth().fadeSlideIn(170)) {
                SwitchRow(R.string.kill_title, R.string.kill_desc, killOn) {
                    killOn = it
                    Prefs.setKillSwitch(ctx, it)
                }
                Spacer(Modifier.height(14.dp))
                ActionRow(R.string.kill_sys_title, R.string.kill_sys_desc) {
                    try {
                        ctx.startActivity(Intent(Settings.ACTION_VPN_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (ignored: Exception) {
                    }
                }
                Spacer(Modifier.height(14.dp))
                SwitchRow(R.string.lock_switch_title, R.string.lock_switch_desc, lockOn) { on ->
                    if (on) {
                        showPin = true
                    } else {
                        AppLock.disable(ctx)
                        lockOn = false
                        bioOn = false
                    }
                }
                if (lockOn && AppLock.bioAvailable(ctx)) {
                    Spacer(Modifier.height(14.dp))
                    SwitchRow(R.string.bio_title, R.string.bio_desc, bioOn) {
                        bioOn = it
                        AppLock.setBio(ctx, it)
                    }
                }
            }

            // -------------------------------------------------------------- tools
            SectionTitle(R.string.tools_title)
            GlassCard(Modifier.fillMaxWidth().fadeSlideIn(175)) {
                ActionRow(R.string.doctor_title, R.string.doctor_desc) { showDoctor = true }
                Spacer(Modifier.height(14.dp))
                ActionRow(R.string.logs_title, R.string.logs_desc) { showLogs = true }
                Spacer(Modifier.height(14.dp))
                ActionRow(R.string.cip_title, R.string.cip_row_desc) { showCleanIp = true }
                Spacer(Modifier.height(14.dp))
                ActionRow(R.string.srv_usage_reset, R.string.srv_usage_reset_desc) {
                    Usage.clearServers()
                    Toast.makeText(ctx, R.string.srv_usage_reset_done, Toast.LENGTH_SHORT).show()
                }
                Spacer(Modifier.height(14.dp))
                ActionRow(R.string.backup_export, R.string.backup_export_desc) { exportLauncher.launch("maxworld-backup.json") }
                Spacer(Modifier.height(14.dp))
                ActionRow(R.string.backup_import, R.string.backup_import_desc) { importLauncher.launch(arrayOf("*/*")) }
            }


            // -------------------------------------------------------------- usage
            SectionTitle(R.string.usage_title)
            UsageCard()

            // -------------------------------------------------------- keepalive
            SectionTitle(R.string.keep_title)
            GlassCard(Modifier.fillMaxWidth().fadeSlideIn(180)) {
                ActionRow(R.string.battery_opt, R.string.battery_opt_desc) { Keepalive.requestIgnoreBattery(ctx) }
                if (Keepalive.isMiui()) {
                    Spacer(Modifier.height(14.dp))
                    ActionRow(R.string.autostart, R.string.autostart_desc) { Keepalive.openAutostart(ctx) }
                }
                Spacer(Modifier.height(14.dp))
                ActionRow(R.string.notif_settings, R.string.notif_settings_desc) { Keepalive.openNotificationSettings(ctx) }
            }

            // ------------------------------------------------------------ about
            SectionTitle(R.string.about_title)
            GlassCard(Modifier.fillMaxWidth().fadeSlideIn(240)) {
                Text("MaxWorld", color = p.text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.version_fmt, BuildConfigVersion.NAME), color = p.muted, fontSize = 13.sp)
                Text(stringResource(R.string.core_fmt, coreVersion), color = p.muted, fontSize = 13.sp)
                UpdateCheckRow()
                if (Updater.enabled()) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip(stringResource(R.string.about_releases), false) {
                            try {
                                ctx.startActivity(
                                    Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/" + Updater.REPO + "/releases"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } catch (ignored: Exception) {
                            }
                        }
                        Chip(stringResource(R.string.about_share), false) {
                            try {
                                val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                                    .putExtra(Intent.EXTRA_TEXT, "MaxWorld\nhttps://github.com/" + Updater.REPO + "/releases/latest")
                                ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            } catch (ignored: Exception) {
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPin) {
        PinSetupDialog(
            onDismiss = { showPin = false },
            onSet = { pin ->
                AppLock.setPin(ctx, pin)
                lockOn = true
                showPin = false
            }
        )
    }
    if (showThemes) ThemesDialog(themeId, onTheme, onDismiss = { showThemes = false })
    if (showLogs) LogsDialog(onDismiss = { showLogs = false })
    if (showDoctor) DoctorDialog(onDismiss = { showDoctor = false })
    if (showCleanIp) CleanIpDialog(onDismiss = { showCleanIp = false })
}

/** App version, kept here so no BuildConfig generation is needed. */
object BuildConfigVersion {
    const val NAME = "1.3.0"
}

@Composable
fun SectionTitle(res: Int) {
    val p = LocalPalette.current
    Spacer(Modifier.height(22.dp))
    Text(
        stringResource(res),
        color = p.accent,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(bottom = 10.dp, start = 4.dp)
    )
}

@Composable
fun ActionRow(title: Int, desc: Int, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().clickable { onClick() }) {
        Text(stringResource(title), color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(desc), color = p.muted, fontSize = 12.sp)
    }
}

@Composable
private fun ThemeChip(t: Palette, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(16.dp)
    val haptic = LocalHapticFeedback.current
    val border by animateColorAsState(
        if (selected) p.accent else p.cardBorder, tween(300), label = "themeBorder"
    )
    Column(
        modifier = modifier
            .clip(shape)
            .background(p.card)
            .border(if (selected) 2.dp else 1.dp, border, shape)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(66.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.verticalGradient(listOf(t.bgTop, t.bgBottom)))
        ) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 8.dp)
                    .size(26.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.linearGradient(listOf(t.accent, t.accent2)))
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
                    .fillMaxWidth(0.7f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(t.card)
                    .border(1.dp, t.cardBorder, RoundedCornerShape(4.dp))
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(t.nameRes),
            color = if (selected) p.accent else p.muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
fun SwitchRow(title: Int, desc: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(desc), color = p.muted, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = p.onAccent,
                checkedTrackColor = p.accent,
                uncheckedThumbColor = p.muted,
                uncheckedTrackColor = p.track
            )
        )
    }
}
