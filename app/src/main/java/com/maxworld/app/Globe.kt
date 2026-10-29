package com.maxworld.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Dots on a sphere: sin(lat), cos(lat), longitude in radians. */
private val GLOBE_DOTS: List<FloatArray> by lazy {
    val out = ArrayList<FloatArray>()
    var lat = -80
    while (lat <= 80) {
        val la = Math.toRadians(lat.toDouble())
        val n = max(4, (46 * cos(la)).toInt())
        for (i in 0 until n) {
            val lo = (i + if ((lat / 8) % 2 == 0) 0f else 0.5f) * 2 * Math.PI / n
            out.add(floatArrayOf(sin(la).toFloat(), cos(la).toFloat(), lo.toFloat()))
        }
        lat += 8
    }
    out
}

/**
 * A slowly turning dotted planet. Once connected it swings round to show where the server is
 * and puts a pulsing pin there.
 */
@Composable
fun Globe(connected: Boolean, lat: Double?, lon: Double?, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val rot = remember { Animatable(0f) }
    val tilt = remember { Animatable(18f) }
    val tint by animateColorAsState(if (connected) p.accent else p.muted, tween(800), label = "globeTint")

    LaunchedEffect(connected, lat, lon) {
        if (connected && lat != null && lon != null) {
            launch { tilt.animateTo((lat - 25.0).toFloat().coerceIn(-55f, 55f), tween(1800, easing = FastOutSlowInEasing)) }
            val cur = rot.value
            val diff = (((-lon).toFloat() - cur) % 360f + 540f) % 360f - 180f
            rot.animateTo(cur + diff, tween(2000, easing = FastOutSlowInEasing))
        } else {
            launch { tilt.animateTo(18f, tween(1200)) }
            while (true) {
                rot.animateTo(rot.value + 360f, tween(48000, easing = LinearEasing))
            }
        }
    }

    val inf = rememberInfiniteTransition(label = "globePulse")
    val pulse by inf.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "pin"
    )

    Canvas(modifier) {
        val r = size.minDimension / 2f * 0.92f
        val c = center
        val rotR = Math.toRadians(rot.value.toDouble())
        val tiltR = Math.toRadians(tilt.value.toDouble())
        val cosT = cos(tiltR).toFloat()
        val sinT = sin(tiltR).toFloat()

        // soft atmosphere
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color.Transparent, tint.copy(alpha = 0.16f), Color.Transparent),
                center = c, radius = r * 1.18f
            ),
            radius = r * 1.18f, center = c
        )
        drawCircle(tint.copy(alpha = 0.14f), radius = r, center = c, style = Stroke(width = 1.dp.toPx()))

        for (d in GLOBE_DOTS) {
            val sinLat = d[0]
            val cosLat = d[1]
            val l = d[2] + rotR.toFloat()
            val x = cosLat * sin(l)
            val z0 = cosLat * cos(l)
            val y = sinLat * cosT - z0 * sinT
            val z = sinLat * sinT + z0 * cosT
            val a = if (z > 0f) 0.14f + 0.50f * z else 0.04f
            val rad = (if (z > 0f) 1.0f + 0.9f * z else 0.7f) * density
            drawCircle(tint.copy(alpha = a), radius = rad, center = Offset(c.x + r * x, c.y - r * y))
        }

        if (connected && lat != null && lon != null) {
            val la = Math.toRadians(lat)
            val l = Math.toRadians(lon).toFloat() + rotR.toFloat()
            val cosLat = cos(la).toFloat()
            val sinLat = sin(la).toFloat()
            val x = cosLat * sin(l)
            val z0 = cosLat * cos(l)
            val y = sinLat * cosT - z0 * sinT
            val z = sinLat * sinT + z0 * cosT
            if (z > 0f) {
                val o = Offset(c.x + r * x, c.y - r * y)
                for (i in 0 until 2) {
                    val t = (pulse + i * 0.5f) % 1f
                    drawCircle(
                        p.ok.copy(alpha = (1f - t) * 0.7f),
                        radius = (4f + 22f * t) * density, center = o,
                        style = Stroke(width = 1.5f * density)
                    )
                }
                drawCircle(p.ok, radius = 4.5f * density, center = o)
                drawCircle(Color.White.copy(alpha = 0.9f), radius = 1.8f * density, center = o)
            }
        }
    }
}
