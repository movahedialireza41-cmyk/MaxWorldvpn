package com.maxworld.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val OB_PAGES = 3

/** Three short welcome pages shown once, before the permission screen. */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val p = LocalPalette.current
    val pager = rememberPagerState(pageCount = { OB_PAGES })
    val scope = rememberCoroutineScope()

    val titles = listOf(R.string.ob1_title, R.string.ob2_title, R.string.ob3_title)
    val texts = listOf(R.string.ob1_text, R.string.ob2_text, R.string.ob3_text)

    Box(Modifier.fillMaxSize()) {
        WorldBackground(connected = false)

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                Text(
                    stringResource(R.string.ob_skip),
                    color = p.muted, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onDone() }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                val off = abs((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
                Column(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = 1f - off
                            scaleX = 1f - 0.15f * off
                            scaleY = 1f - 0.15f * off
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
                        when (page) {
                            0 -> Globe(connected = false, lat = null, lon = null, modifier = Modifier.size(260.dp))
                            1 -> BoltArt()
                            else -> ForkArt()
                        }
                    }
                    Spacer(Modifier.height(30.dp))
                    Text(
                        stringResource(titles[page]),
                        color = p.text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(texts[page]),
                        color = p.muted, fontSize = 15.sp, textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(vertical = 18.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(OB_PAGES) { i ->
                    val on = pager.currentPage == i
                    val w by animateDpAsState(if (on) 28.dp else 8.dp, tween(300), label = "dotW")
                    val col by animateColorAsState(if (on) p.accent else p.track, tween(300), label = "dotC")
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .width(w)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(col)
                    )
                }
            }

            val last = pager.currentPage == OB_PAGES - 1
            PrimaryButton(
                text = stringResource(if (last) R.string.ob_start else R.string.ob_next),
                onClick = {
                    if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                }
            )
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun BoltArt() {
    val p = LocalPalette.current
    Canvas(Modifier.size(220.dp)) {
        val s = size.minDimension
        val c = center
        drawCircle(
            Brush.radialGradient(listOf(p.accent.copy(alpha = 0.35f), androidx.compose.ui.graphics.Color.Transparent), center = c, radius = s / 2),
            radius = s / 2, center = c
        )
        val bolt = Path().apply {
            moveTo(s * 0.58f, s * 0.08f)
            lineTo(s * 0.26f, s * 0.54f)
            lineTo(s * 0.47f, s * 0.54f)
            lineTo(s * 0.40f, s * 0.92f)
            lineTo(s * 0.74f, s * 0.43f)
            lineTo(s * 0.53f, s * 0.43f)
            close()
        }
        drawPath(bolt, Brush.linearGradient(listOf(p.accent, p.accent2), Offset(0f, 0f), Offset(s, s)))
    }
}

@Composable
private fun ForkArt() {
    val p = LocalPalette.current
    Canvas(Modifier.size(220.dp)) {
        val s = size.minDimension
        val c = center
        drawCircle(
            Brush.radialGradient(listOf(p.accent2.copy(alpha = 0.35f), androidx.compose.ui.graphics.Color.Transparent), center = c, radius = s / 2),
            radius = s / 2, center = c
        )
        val w = 10.dp.toPx()
        val col = p.accent2
        val mid = Offset(s * 0.5f, s * 0.58f)
        val l = Offset(s * 0.2f, s * 0.22f)
        val r = Offset(s * 0.8f, s * 0.22f)
        drawLine(col, Offset(s * 0.5f, s * 0.92f), mid, strokeWidth = w, cap = StrokeCap.Round)
        drawLine(col, mid, l, strokeWidth = w, cap = StrokeCap.Round)
        drawLine(p.accent, mid, r, strokeWidth = w, cap = StrokeCap.Round)
        drawCircle(col, radius = 12.dp.toPx(), center = l)
        drawCircle(p.accent, radius = 12.dp.toPx(), center = r)
    }
}
