package com.maxworld.app

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Which look the app uses: the new "Neo" design or the Classic one from earlier versions. */
object Design {
    var neo by mutableStateOf(true)
        private set

    fun init(ctx: Context) {
        neo = Prefs.design(ctx) != 0
    }

    fun set(ctx: Context, value: Boolean) {
        neo = value
        Prefs.setDesign(ctx, if (value) 1 else 0)
    }
}

/**
 * Connect button of the Neo design: a turning gradient ring, a ticking dial, a glass core
 * and waves that leave it when the tunnel is up.
 */
@Composable
fun NeoOrb(state: ConnState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val haptic = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 260f),
        label = "neoScale"
    )
    val c1 by animateColorAsState(
        when (state) {
            ConnState.DISCONNECTED -> p.accent
            ConnState.CONNECTING -> p.warn
            ConnState.CONNECTED -> p.ok
        },
        tween(700), label = "neoC1"
    )
    val c2 by animateColorAsState(
        when (state) {
            ConnState.DISCONNECTED -> p.accent2
            ConnState.CONNECTING -> p.accent
            ConnState.CONNECTED -> p.accent
        },
        tween(700), label = "neoC2"
    )
    val glow by animateFloatAsState(
        when (state) {
            ConnState.DISCONNECTED -> 0.22f
            ConnState.CONNECTING -> 0.40f
            ConnState.CONNECTED -> 0.55f
        },
        tween(700), label = "neoGlow"
    )
    val check by animateFloatAsState(
        if (state == ConnState.CONNECTED) 1f else 0f,
        tween(600, easing = FastOutSlowInEasing), label = "neoCheck"
    )
    val speedTarget by animateFloatAsState(
        when (state) {
            ConnState.DISCONNECTED -> 26f
            ConnState.CONNECTING -> 300f
            ConnState.CONNECTED -> 55f
        },
        tween(900), label = "neoSpeed"
    )
    val speed by rememberUpdatedState(speedTarget)

    // ring angle advances by the current speed, so changing state never makes it jump
    var angle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { t ->
                if (last != 0L) {
                    val dt = (t - last) / 1_000_000_000f
                    angle = (angle + speed * dt) % 360f
                }
                last = t
            }
        }
    }

    val inf = rememberInfiniteTransition(label = "neoInf")
    val wave by inf.animateFloat(
        0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "neoWave"
    )
    val phase by inf.animateFloat(
        0f, (2 * PI).toFloat(), infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "neoPhase"
    )
    val breath by inf.animateFloat(
        0.95f, 1f, infiniteRepeatable(tween(1900), RepeatMode.Reverse), label = "neoBreath"
    )
    val burst = remember { Animatable(1f) }
    LaunchedEffect(state) {
        if (state == ConnState.CONNECTED) {
            burst.snapTo(0f)
            burst.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
        }
    }

    Box(
        modifier = modifier
            .size(250.dp)
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
            val connected = state == ConnState.CONNECTED
            val active = state != ConnState.DISCONNECTED
            val pop = 1f + 0.10f * sin(PI.toFloat() * b)
            val coreR = maxR * 0.50f * (if (connected) breath else 1f) * pop

            // big soft glow
            drawCircle(
                brush = Brush.radialGradient(listOf(c1.copy(alpha = glow), Color.Transparent), center = c, radius = maxR),
                radius = maxR, center = c
            )

            // waves leaving the core while connected
            if (connected) {
                for (i in 0 until 2) {
                    val t = (wave + i * 0.5f) % 1f
                    drawCircle(
                        color = c1.copy(alpha = (1f - t) * 0.35f),
                        radius = coreR * 1.3f + (maxR - coreR * 1.3f) * t,
                        center = c,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }

            // dial ticks; a bright wave runs around them while active
            val r1 = maxR * 0.74f
            for (i in 0 until 60) {
                val a = (i * 6f) * (PI.toFloat() / 180f)
                val long = i % 5 == 0
                val r2 = r1 + (if (long) 9.dp.toPx() else 5.dp.toPx())
                val lit = if (active) 0.5f + 0.5f * sin(phase * 2f - i * 0.35f) else 0.15f
                drawLine(
                    color = (if (long) c2 else c1).copy(alpha = (if (long) 0.35f else 0.2f) + 0.45f * lit),
                    start = Offset(c.x + cos(a) * r1, c.y + sin(a) * r1),
                    end = Offset(c.x + cos(a) * r2, c.y + sin(a) * r2),
                    strokeWidth = if (long) 2.dp.toPx() else 1.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // turning gradient ring
            val ringR = coreR * 1.30f
            rotate(angle, pivot = c) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(c1, c2, c1.copy(alpha = 0f), c1.copy(alpha = 0f), c1),
                        center = c
                    ),
                    startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    topLeft = Offset(c.x - ringR, c.y - ringR),
                    size = Size(ringR * 2, ringR * 2),
                    style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            drawCircle(
                color = c1.copy(alpha = 0.18f), radius = ringR, center = c,
                style = Stroke(width = 1.dp.toPx())
            )

            // glass core
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(c2.copy(alpha = 0.95f), c1.copy(alpha = 0.95f)),
                    center = Offset(c.x - coreR * 0.3f, c.y - coreR * 0.4f),
                    radius = coreR * 1.7f
                ),
                radius = coreR, center = c
            )
            drawArc(
                color = Color.White.copy(alpha = 0.28f),
                startAngle = 200f, sweepAngle = 100f, useCenter = false,
                topLeft = Offset(c.x - coreR * 0.86f, c.y - coreR * 0.86f),
                size = Size(coreR * 1.72f, coreR * 1.72f),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
            drawCircle(color = Color.White.copy(alpha = 0.16f), radius = coreR, center = c, style = Stroke(width = 1.dp.toPx()))

            // little dots circling the core while connecting
            if (state == ConnState.CONNECTING) {
                for (i in 0 until 3) {
                    val a = (angle * 3f + i * 120f) * (PI.toFloat() / 180f)
                    drawCircle(
                        color = p.onAccent.copy(alpha = 0.85f), radius = 3.dp.toPx(),
                        center = Offset(c.x + cos(a) * coreR * 0.62f, c.y + sin(a) * coreR * 0.62f)
                    )
                }
            }

            // power icon that turns into a check mark
            val ink = p.onAccent
            val ir = coreR * 0.40f
            val w = 4.5.dp.toPx()
            if (check < 0.99f && state != ConnState.CONNECTING) {
                val fade = 1f - check
                drawArc(
                    color = ink.copy(alpha = fade), startAngle = -60f, sweepAngle = 300f, useCenter = false,
                    topLeft = Offset(c.x - ir, c.y - ir), size = Size(ir * 2, ir * 2),
                    style = Stroke(width = w, cap = StrokeCap.Round)
                )
                drawLine(
                    color = ink.copy(alpha = fade),
                    start = Offset(c.x, c.y - ir * 1.15f), end = Offset(c.x, c.y - ir * 0.1f),
                    strokeWidth = w, cap = StrokeCap.Round
                )
            }
            if (check > 0.01f) {
                val a = Offset(c.x - ir * 0.95f, c.y + ir * 0.05f)
                val m = Offset(c.x - ir * 0.25f, c.y + ir * 0.70f)
                val z = Offset(c.x + ir * 1.05f, c.y - ir * 0.65f)
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

            // one-shot burst of dots when the connection comes up
            if (b < 1f) {
                for (i in 0 until 18) {
                    val a = (i * 20f) * (PI.toFloat() / 180f)
                    val rr = coreR * 1.3f + (maxR * 1.05f - coreR * 1.3f) * b
                    drawCircle(
                        color = c1.copy(alpha = (1f - b) * 0.9f), radius = 2.5.dp.toPx() * (1f - b * 0.5f),
                        center = Offset(c.x + cos(a) * rr, c.y + sin(a) * rr)
                    )
                }
            }
        }
    }
}

/** Slowly drifting colour clouds behind everything in the Neo design. */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNeoAurora(p: Palette, t: Float, boost: Float) {
    val r = size.minDimension * 0.95f
    val k = 0.16f + 0.12f * boost
    val a1 = Offset(size.width * (0.22f + 0.16f * sin(t)), size.height * (0.16f + 0.08f * cos(t * 2f)))
    val a2 = Offset(size.width * (0.82f + 0.12f * cos(t)), size.height * (0.46f + 0.10f * sin(t * 2f)))
    val a3 = Offset(size.width * (0.40f + 0.18f * sin(t * 3f + 1f)), size.height * (0.88f + 0.06f * cos(t)))
    drawCircle(Brush.radialGradient(listOf(p.accent.copy(alpha = k + 0.04f), Color.Transparent), center = a1, radius = r), radius = r, center = a1)
    drawCircle(Brush.radialGradient(listOf(p.accent2.copy(alpha = k), Color.Transparent), center = a2, radius = r), radius = r, center = a2)
    drawCircle(Brush.radialGradient(listOf(p.accent.copy(alpha = k * 0.8f), Color.Transparent), center = a3, radius = r), radius = r, center = a3)
}

/** Small animated emblem for full-page messages: a turning ring around a pulsing sparkle. */
@Composable
fun NeoBadge(modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val inf = rememberInfiniteTransition(label = "badge")
    val a by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "badgeA")
    val pulse by inf.animateFloat(0.85f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "badgeP")
    Canvas(modifier.size(96.dp)) {
        val c = center
        val r = size.minDimension / 2f
        drawCircle(
            Brush.radialGradient(listOf(p.accent.copy(alpha = 0.35f), Color.Transparent), center = c, radius = r),
            radius = r, center = c
        )
        rotate(a, pivot = c) {
            drawArc(
                brush = Brush.sweepGradient(listOf(p.accent, p.accent2, p.accent.copy(alpha = 0f), p.accent), center = c),
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(c.x - r * 0.78f, c.y - r * 0.78f),
                size = Size(r * 1.56f, r * 1.56f),
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        val s = r * 0.42f * pulse
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(c.x, c.y - s)
            quadraticBezierTo(c.x, c.y, c.x + s, c.y)
            quadraticBezierTo(c.x, c.y, c.x, c.y + s)
            quadraticBezierTo(c.x, c.y, c.x - s, c.y)
            quadraticBezierTo(c.x, c.y, c.x, c.y - s)
            close()
        }
        drawPath(
            path,
            Brush.linearGradient(listOf(p.accent, p.accent2), start = Offset(c.x - s, c.y - s), end = Offset(c.x + s, c.y + s))
        )
    }
}
