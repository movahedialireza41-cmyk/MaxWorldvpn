package com.maxworld.app

import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {

    private val vpnConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK) {
            MaxVpnService.start(this)
        } else {
            VpnState.failed(getString(R.string.err_vpn_denied))
        }
    }

    private val scope = MainScope()
    private val locked = mutableStateOf(false)
    private var stoppedAt = 0L

    override fun onStop() {
        stoppedAt = android.os.SystemClock.elapsedRealtime()
        Usage.flush()
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        // Lock again when the app was away for a little while.
        if (AppLock.enabled(this) && stoppedAt != 0L &&
            android.os.SystemClock.elapsedRealtime() - stoppedAt > 20_000
        ) {
            locked.value = true
        }
    }
    private var bestJob: Job? = null

    /** Pings the servers, switches to the fastest one, then connects. */
    private fun connectBest() {
        VpnState.connecting()
        Toast.makeText(this, R.string.finding_best, Toast.LENGTH_SHORT).show()
        bestJob = scope.launch {
            val all = ConfigStore.configs.toList()
            val favs = all.filter { it.favorite }
            val candidates = (if (favs.size >= 2) favs else all).take(12)
            val done = java.util.Collections.synchronizedList(ArrayList<Pair<VpnConfig, Long>>())
            withTimeoutOrNull(20_000) {
                candidates.map { c -> async { done.add(c to Tester.delay(c)) } }.awaitAll()
            }
            val finished = done.toList()
            finished.forEach { (c, ms) -> ConfigStore.setPing(c.id, if (ms > 0) ms else -2L) }
            finished.filter { it.second > 0 }.minByOrNull { it.second }?.let { ConfigStore.select(it.first.id) }
            bestJob = null
            MaxVpnService.start(this@MainActivity)
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Links opened from other apps (vless://… etc.), shared text, or v2rayng://install-config. */
    private fun handleIntent(i: android.content.Intent?) {
        if (i == null) return
        val action = i.action
        if (action != android.content.Intent.ACTION_VIEW && action != android.content.Intent.ACTION_SEND) return
        val uri = i.data
        i.action = null
        i.data = null
        if (uri != null && uri.scheme == "v2rayng") {
            val url = uri.getQueryParameter("url").orEmpty()
            if (url.startsWith("http://") || url.startsWith("https://")) addSub(uri.getQueryParameter("name").orEmpty(), url)
            return
        }
        val text = uri?.toString() ?: i.getStringExtra(android.content.Intent.EXTRA_TEXT).orEmpty()
        when (val r = Importer.fromText(text)) {
            is Imported.Servers -> Toast.makeText(
                this,
                if (r.count == 0) getString(R.string.toast_none) else getString(R.string.toast_added, r.count),
                Toast.LENGTH_SHORT
            ).show()
            is Imported.SubUrl -> addSub("", r.url)
            Imported.None -> Toast.makeText(this, R.string.toast_none, Toast.LENGTH_SHORT).show()
        }
    }

    private fun addSub(name: String, url: String) {
        val sub = Subs.add(name, url)
        Toast.makeText(this, R.string.toast_imported_sub, Toast.LENGTH_SHORT).show()
        scope.launch {
            val n = Subs.refresh(sub)
            Toast.makeText(
                this@MainActivity,
                if (n < 0) getString(R.string.sub_failed) else getString(R.string.sub_updated_fmt, n),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun toggle() {
        when (VpnState.state) {
            ConnState.CONNECTED, ConnState.CONNECTING -> {
                val job = bestJob
                if (job != null) {
                    job.cancel()
                    bestJob = null
                    VpnState.disconnected()
                } else {
                    MaxVpnService.stop(this)
                }
            }
            ConnState.DISCONNECTED -> {
                if (ConfigStore.selected() == null) {
                    Toast.makeText(this, R.string.err_no_config, Toast.LENGTH_SHORT).show()
                    return
                }
                VpnState.clearError()
                val consent = VpnService.prepare(this)
                if (consent != null) {
                    vpnConsent.launch(consent)
                } else if (Prefs.autoBest(this) && ConfigStore.configs.size > 1) {
                    connectBest()
                } else {
                    MaxVpnService.start(this)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ConfigStore.init(this)
        locked.value = AppLock.enabled(this)
        Themes.loadCustom(this)
        handleIntent(intent)

        setContent {
            var themeId by remember { mutableStateOf(Prefs.theme(this)) }
            val palette = Themes.byId(themeId)
            var showOnboard by remember { mutableStateOf(!Prefs.onboardDone(this)) }
            var showPerms by remember { mutableStateOf(!notifGranted(this)) }
            var tab by remember { mutableIntStateOf(0) }

            LaunchedEffect(palette.light) { applySystemBars(palette.light) }
            LaunchedEffect(Unit) {
                try {
                    Subs.autoUpdate()
                } catch (e: Exception) {
                }
            }

            MaxWorldTheme(palette) {
                Box(Modifier.fillMaxSize().background(palette.bgBottom)) {
                    if (locked.value) {
                        LockScreen(onUnlock = { locked.value = false })
                    } else if (showOnboard) {
                        OnboardingScreen(onDone = {
                            Prefs.setOnboardDone(this@MainActivity, true)
                            showOnboard = false
                        })
                    } else if (showPerms) {
                        PermissionScreen(onDone = { showPerms = false })
                    } else {
                        AnimatedContent(
                            targetState = tab,
                            transitionSpec = {
                                val dir = if (targetState > initialState) 1 else -1
                                (fadeIn(tween(320)) + slideInHorizontally(tween(380)) { w -> w / 8 * dir }) togetherWith
                                    (fadeOut(tween(220)) + slideOutHorizontally(tween(380)) { w -> -w / 8 * dir })
                            },
                            label = "tabs"
                        ) { t ->
                            when (t) {
                                0 -> HomeScreen(onToggle = { toggle() })
                                1 -> ServersScreen(onSelected = {
                                    // Switching server while connected: reconnect to the new one.
                                    if (VpnState.state == ConnState.CONNECTED) MaxVpnService.start(this@MainActivity)
                                })
                                else -> SettingsScreen(
                                    themeId = themeId,
                                    onTheme = {
                                        themeId = it
                                        Prefs.setTheme(this@MainActivity, it)
                                    }
                                )
                            }
                        }
                        BottomBar(
                            selected = tab,
                            onSelect = { tab = it },
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ bottom nav

@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(32.dp)
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 14.dp)
            .fillMaxWidth()
            .height(68.dp)
            .clip(shape)
            .background(p.card)
            .border(1.dp, p.cardBorder, shape)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val labels = listOf(R.string.nav_home, R.string.nav_servers, R.string.nav_settings)
        labels.forEachIndexed { i, res ->
            NavItem(i, stringResource(res), selected == i, Modifier.weight(1f)) { onSelect(i) }
        }
    }
}

@Composable
private fun NavItem(kind: Int, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val haptic = LocalHapticFeedback.current
    val color by animateColorAsState(if (selected) p.accent else p.muted, tween(300), label = "navColor")
    val bg by animateColorAsState(
        if (selected) p.accent.copy(alpha = 0.14f) else Color.Transparent, tween(300), label = "navBg"
    )
    val lift by animateFloatAsState(if (selected) -3f else 0f, spring(dampingRatio = 0.5f), label = "navLift")

    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(26.dp))
            .background(bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Canvas(
            Modifier
                .size(24.dp)
                .graphicsLayer { translationY = lift * density }
        ) { drawNavIcon(kind, color) }
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNavIcon(kind: Int, color: Color) {
    val w = 2.2.dp.toPx()
    val s = size.minDimension
    val st = Stroke(width = w, cap = StrokeCap.Round)
    when (kind) {
        0 -> { // home
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(s * 0.12f, s * 0.48f)
                lineTo(s * 0.5f, s * 0.12f)
                lineTo(s * 0.88f, s * 0.48f)
                moveTo(s * 0.22f, s * 0.42f)
                lineTo(s * 0.22f, s * 0.86f)
                lineTo(s * 0.78f, s * 0.86f)
                lineTo(s * 0.78f, s * 0.42f)
            }
            drawPath(path, color, style = st)
        }
        1 -> { // servers: two stacked rounded bars
            for (i in 0..1) {
                val top = s * (0.16f + i * 0.42f)
                drawRoundRect(
                    color, topLeft = Offset(s * 0.1f, top), size = Size(s * 0.8f, s * 0.3f),
                    cornerRadius = CornerRadius(s * 0.08f), style = st
                )
                drawCircle(color, radius = w * 0.7f, center = Offset(s * 0.26f, top + s * 0.15f))
            }
        }
        else -> { // settings: ring with ticks
            val c = Offset(s / 2, s / 2)
            drawCircle(color, radius = s * 0.18f, center = c, style = st)
            for (i in 0 until 8) {
                val a = i * Math.PI / 4
                val dx = cos(a).toFloat()
                val dy = sin(a).toFloat()
                drawLine(
                    color,
                    Offset(c.x + dx * s * 0.30f, c.y + dy * s * 0.30f),
                    Offset(c.x + dx * s * 0.42f, c.y + dy * s * 0.42f),
                    strokeWidth = w, cap = StrokeCap.Round
                )
            }
        }
    }
}
