package com.maxworld.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun HomeScreen(onToggle: () -> Unit) {
    val p = LocalPalette.current
    val state = VpnState.state
    val cfg = ConfigStore.selected()

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state) {
        while (state == ConnState.CONNECTED) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    val haptic = LocalHapticFeedback.current
    var prev by remember { mutableStateOf(state) }
    LaunchedEffect(state) {
        if (state == ConnState.CONNECTED) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            IpInfo.fetch()
        } else {
            if (prev == ConnState.CONNECTED) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            IpInfo.clear()
        }
        prev = state
    }

    Box(Modifier.fillMaxSize()) {
        WorldBackground(connected = state == ConnState.CONNECTED)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 110.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(18.dp))
            Text(
                text = "MaxWorld",
                style = TextStyle(
                    brush = Brush.horizontalGradient(listOf(p.accent, p.accent2)),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                ),
                modifier = Modifier.fadeSlideIn()
            )

            Spacer(Modifier.weight(1f))

            Box(Modifier.fadeSlideIn(100), contentAlignment = Alignment.Center) {
                Globe(
                    connected = state == ConnState.CONNECTED,
                    lat = IpInfo.info?.lat,
                    lon = IpInfo.info?.lon,
                    modifier = Modifier.requiredSize(340.dp)
                )
                ConnectOrb(state = state, onClick = onToggle)
            }

            Spacer(Modifier.height(26.dp))

            Crossfade(targetState = state, animationSpec = tween(400), label = "status") { s ->
                val (res, color) = when (s) {
                    ConnState.DISCONNECTED -> R.string.status_disconnected to p.muted
                    ConnState.CONNECTING -> R.string.status_connecting to p.warn
                    ConnState.CONNECTED -> R.string.status_connected to p.ok
                }
                Text(
                    text = stringResource(res),
                    color = color,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(6.dp))

            if (state == ConnState.CONNECTED) {
                val secs = ((now - VpnState.connectedAt) / 1000).coerceAtLeast(0)
                Text(
                    text = "%02d:%02d:%02d".format(secs / 3600, (secs / 60) % 60, secs % 60),
                    color = p.text,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 2.sp
                )
            } else {
                Text(
                    text = stringResource(R.string.hint_tap_connect),
                    color = p.muted,
                    fontSize = 14.sp
                )
            }

            IpPill(state)

            VpnState.error?.let { msg ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = msg,
                    color = p.danger,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { VpnState.clearError() }
                )
            }

            if (VpnState.blocking) {
                Spacer(Modifier.height(10.dp))
                val ctx = LocalContext.current
                Text(
                    text = stringResource(R.string.kill_blocked),
                    color = p.danger,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { MaxVpnService.stop(ctx) }
                )
            }

            Spacer(Modifier.weight(1f))

            AnimatedVisibility(
                visible = state == ConnState.CONNECTED,
                enter = fadeIn(tween(500)) + expandVertically(tween(500)),
                exit = fadeOut(tween(300)) + shrinkVertically(tween(300))
            ) {
                Column {
                    TrafficCard()
                    Spacer(Modifier.height(10.dp))
                }
            }

            DataTank(cfg)

            GlassCard(Modifier.fillMaxWidth().fadeSlideIn(200)) {
                Text(
                    text = stringResource(R.string.selected_server),
                    color = p.muted,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(6.dp))
                if (cfg == null) {
                    Text(stringResource(R.string.no_server), color = p.text, fontSize = 15.sp)
                } else {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                cfg.displayName(),
                                color = p.text,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                "${cfg.protocol.uppercase()} · ${cfg.network} · ${cfg.security}",
                                color = p.muted,
                                fontSize = 12.sp
                            )
                            val usedHere = Usage.serverTotal(cfg.id)
                            if (usedHere > 0L) {
                                Text(
                                    stringResource(R.string.server_used_fmt, Traffic.bytes(usedHere)),
                                    color = p.accent,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        PingBadge(if (state == ConnState.CONNECTED && Traffic.livePing != 0L) Traffic.livePing else cfg.ping)
                    }
                }
            }
        }
    }
}

/** Shows where the traffic leaves the internet: flag, city, country and the public IP. */
@Composable
private fun IpPill(state: ConnState) {
    val p = LocalPalette.current
    val info = IpInfo.info
    val shape = RoundedCornerShape(22.dp)
    AnimatedVisibility(
        visible = state == ConnState.CONNECTED && (info != null || IpInfo.loading),
        enter = fadeIn(tween(500)) + expandVertically(tween(500)),
        exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
    ) {
        Row(
            Modifier
                .padding(top = 12.dp)
                .clip(shape)
                .background(p.card)
                .border(1.dp, p.cardBorder, shape)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (info != null) {
                val flag = flagOf(info.countryCode)
                if (flag.isNotEmpty()) {
                    Text(flag, fontSize = 24.sp)
                    Spacer(Modifier.size(10.dp))
                }
                Column {
                    Text(
                        listOf(info.city, info.countryName()).filter { it.isNotBlank() }.joinToString(", "),
                        color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1
                    )
                    Text(info.ip, color = p.muted, fontSize = 12.sp, maxLines = 1)
                }
            } else {
                Text(stringResource(R.string.ip_loading), color = p.muted, fontSize = 13.sp)
            }
        }
    }
}

/** Latency chip: green fast, amber medium, red slow/timeout, grey untested. */
@Composable
fun PingBadge(ping: Long) {
    val p = LocalPalette.current
    val color = when {
        ping == -1L -> p.muted
        ping < 0 -> p.danger
        ping < 300 -> p.ok
        ping < 800 -> p.warn
        else -> p.danger
    }
    val label = when {
        ping == -1L -> stringResource(R.string.ping_untested)
        ping < 0 -> stringResource(R.string.ping_timeout)
        else -> stringResource(R.string.ms_unit, ping.toInt())
    }
    Text(
        text = label,
        color = color,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
fun ConnectOrb(state: ConnState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val haptic = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f),
        label = "orbScale"
    )

    val main by animateColorAsState(
        targetValue = when (state) {
            ConnState.DISCONNECTED -> p.muted
            ConnState.CONNECTING -> p.warn
            ConnState.CONNECTED -> p.ok
        },
        animationSpec = tween(600),
        label = "orbColor"
    )

    val inf = rememberInfiniteTransition(label = "orb")
    val ripple by inf.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing)),
        label = "ripple"
    )
    val spin by inf.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "spin"
    )
    val check by animateFloatAsState(
        targetValue = if (state == ConnState.CONNECTED) 1f else 0f,
        animationSpec = tween(550, easing = FastOutSlowInEasing),
        label = "check"
    )
    val burst = remember { Animatable(1f) }
    LaunchedEffect(state) {
        if (state == ConnState.CONNECTED) {
            burst.snapTo(0f)
            burst.animateTo(1f, tween(1000, easing = FastOutSlowInEasing))
        }
    }
    val breath by inf.animateFloat(
        initialValue = 0.94f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "breath"
    )

    Box(
        modifier = modifier
            .size(240.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = source, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val maxR = size.minDimension / 2f
            val b = burst.value
            val pop = 1f + 0.12f * sin(PI.toFloat() * b)
            val coreR = maxR * 0.50f * (if (state == ConnState.CONNECTED) breath else 1f) * pop

            // one-shot shockwave when the connection comes up
            if (b < 1f) {
                drawCircle(
                    color = main.copy(alpha = (1f - b) * 0.6f),
                    radius = coreR + (maxR * 1.1f - coreR) * b,
                    center = c,
                    style = Stroke(width = 3.dp.toPx())
                )
            }

            // soft glow
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(main.copy(alpha = 0.40f), Color.Transparent),
                    center = c, radius = maxR
                ),
                radius = maxR, center = c
            )

            // ripples while connected
            if (state == ConnState.CONNECTED) {
                for (i in 0 until 3) {
                    val t = (ripple + i / 3f) % 1f
                    drawCircle(
                        color = main.copy(alpha = (1f - t) * 0.5f),
                        radius = coreR + (maxR - coreR) * t,
                        center = c,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }

            // static ring
            drawCircle(
                color = main.copy(alpha = 0.35f),
                radius = coreR * 1.28f,
                center = c,
                style = Stroke(width = 2.dp.toPx())
            )

            // spinner while connecting
            if (state == ConnState.CONNECTING) {
                val r = coreR * 1.28f
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(Color.Transparent, main, Color.Transparent),
                        center = c
                    ),
                    startAngle = spin,
                    sweepAngle = 120f,
                    useCenter = false,
                    topLeft = Offset(c.x - r, c.y - r),
                    size = Size(r * 2, r * 2),
                    style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // core
            drawCircle(
                brush = Brush.linearGradient(
                    listOf(main.copy(alpha = 0.95f), p.accent2.copy(alpha = 0.85f)),
                    start = Offset(c.x - coreR, c.y - coreR),
                    end = Offset(c.x + coreR, c.y + coreR)
                ),
                radius = coreR, center = c
            )

            // power icon that morphs into a check mark
            val iconR = coreR * 0.42f
            val w = 5.dp.toPx()
            val ink = p.onAccent
            if (check < 0.99f) {
                val fade = 1f - check
                drawArc(
                    color = ink.copy(alpha = fade),
                    startAngle = -60f,
                    sweepAngle = 300f,
                    useCenter = false,
                    topLeft = Offset(c.x - iconR, c.y - iconR),
                    size = Size(iconR * 2, iconR * 2),
                    style = Stroke(width = w, cap = StrokeCap.Round)
                )
                drawLine(
                    color = ink.copy(alpha = fade),
                    start = Offset(c.x, c.y - iconR * 1.15f),
                    end = Offset(c.x, c.y - iconR * 0.1f),
                    strokeWidth = w,
                    cap = StrokeCap.Round
                )
            }
            if (check > 0.01f) {
                val a = Offset(c.x - iconR * 0.95f, c.y + iconR * 0.05f)
                val m = Offset(c.x - iconR * 0.25f, c.y + iconR * 0.70f)
                val z = Offset(c.x + iconR * 1.05f, c.y - iconR * 0.65f)
                val l1 = hypot(m.x - a.x, m.y - a.y)
                val l2 = hypot(z.x - m.x, z.y - m.y)
                val prog = check * (l1 + l2)
                val f1 = (prog / l1).coerceIn(0f, 1f)
                val f2 = ((prog - l1) / l2).coerceIn(0f, 1f)
                drawLine(ink, a, Offset(a.x + (m.x - a.x) * f1, a.y + (m.y - a.y) * f1), strokeWidth = w, cap = StrokeCap.Round)
                if (f2 > 0f) {
                    drawLine(ink, m, Offset(m.x + (z.x - m.x) * f2, m.y + (z.y - m.y) * f2), strokeWidth = w, cap = StrokeCap.Round)
                }
            }
        }
    }
}

/** Slowly drifting dots; when connected nearby dots join with glowing lines. */
@Composable
fun WorldBackground(connected: Boolean) {
    val p = LocalPalette.current
    val dots = remember {
        val r = Random(7)
        List(34) {
            floatArrayOf(r.nextFloat(), r.nextFloat(), r.nextFloat() * 6.28f, 0.6f + r.nextFloat() * 1.4f)
        }
    }
    val inf = rememberInfiniteTransition(label = "bg")
    val t by inf.animateFloat(
        initialValue = 0f, targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(26000, easing = LinearEasing)),
        label = "bgT"
    )
    val lineAlpha by animateFloatAsState(
        targetValue = if (connected) 1f else 0f,
        animationSpec = tween(900),
        label = "lines"
    )

    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(p.bgTop, p.bgBottom)))

        val pts = dots.map { d ->
            Offset(
                x = (d[0] + 0.03f * sin(t * d[3] + d[2])).coerceIn(0f, 1f) * size.width,
                y = (d[1] + 0.03f * cos(t * d[3] + d[2])).coerceIn(0f, 1f) * size.height
            )
        }

        if (lineAlpha > 0.01f) {
            val limit = size.minDimension * 0.28f
            for (i in pts.indices) for (j in i + 1 until pts.size) {
                val dist = hypot(pts[i].x - pts[j].x, pts[i].y - pts[j].y)
                if (dist < limit) {
                    drawLine(
                        color = p.accent.copy(alpha = (1f - dist / limit) * 0.35f * lineAlpha),
                        start = pts[i], end = pts[j], strokeWidth = 1.dp.toPx()
                    )
                }
            }
        }
        pts.forEachIndexed { i, o ->
            drawCircle(
                color = (if (connected) p.accent else p.muted).copy(alpha = 0.35f + 0.25f * ((i % 3) / 2f)),
                radius = (1.5f + (i % 3)) .dp.toPx(),
                center = o
            )
        }
    }
}

// ------------------------------------------------------------------ live traffic

private const val CHART_POINTS = 48

@Composable
private fun TrafficCard() {
    val p = LocalPalette.current
    GlassCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(stringResource(R.string.traffic_down), color = p.muted, fontSize = 11.sp)
                Text("↓ " + Traffic.rate(Traffic.downBps), color = p.accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.live_ping), color = p.muted, fontSize = 11.sp)
                PingBadge(if (Traffic.livePing == 0L) -1L else Traffic.livePing)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.traffic_up), color = p.muted, fontSize = 11.sp)
                Text("↑ " + Traffic.rate(Traffic.upBps), color = p.accent2, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().height(60.dp)) {
            val down = Traffic.historyDown.toList()
            val up = Traffic.historyUp.toList()
            val top = maxOf(down.maxOrNull() ?: 0f, up.maxOrNull() ?: 0f, 1024f)
            series(down, top, p.accent)
            series(up, top, p.accent2)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.traffic_session) + ": ↓ " + Traffic.bytes(Traffic.totalDown) +
                "   ↑ " + Traffic.bytes(Traffic.totalUp),
            color = p.muted,
            fontSize = 12.sp
        )
    }
}

private fun DrawScope.series(list: List<Float>, maxV: Float, color: Color) {
    if (list.size < 2) return
    val w = size.width
    val h = size.height
    fun pt(i: Int) = Offset(
        w * (CHART_POINTS - list.size + i) / (CHART_POINTS - 1f),
        h - (list[i] / maxV) * h * 0.9f - 2f
    )
    val line = Path()
    line.moveTo(pt(0).x, pt(0).y)
    for (i in 1 until list.size) {
        val a = pt(i - 1)
        val b = pt(i)
        val mx = (a.x + b.x) / 2f
        line.cubicTo(mx, a.y, mx, b.y, b.x, b.y)
    }
    val fill = Path()
    fill.addPath(line)
    fill.lineTo(pt(list.size - 1).x, h)
    fill.lineTo(pt(0).x, h)
    fill.close()
    drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.30f), Color.Transparent)))
    drawPath(line, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
}
