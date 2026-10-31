package com.maxworld.app

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Fades and slides an element up when it first appears. */
@Composable
fun Modifier.fadeSlideIn(delayMs: Int = 0): Modifier {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val a by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = 500, delayMillis = delayMs, easing = FastOutSlowInEasing),
        label = "enter"
    )
    val neo = Design.neo
    return this.graphicsLayer {
        alpha = a.coerceIn(0f, 1f)
        translationY = (1f - a) * (if (neo) 70f else 48f)
        if (neo) {
            val s = 0.94f + 0.06f * a
            scaleX = s
            scaleY = s
        }
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val p = LocalPalette.current
    if (Design.neo) {
        // Neo: rounder, a faint light from the top, and an outline that glows from one accent to the other
        val neoShape = RoundedCornerShape((p.cornerDp + 6f).dp)
        Column(
            modifier = modifier
                .clip(neoShape)
                .background(Brush.verticalGradient(listOf(p.card, p.card.copy(alpha = p.card.alpha * 0.82f))))
                .background(Brush.linearGradient(listOf(p.accent.copy(alpha = 0.07f), Color.Transparent, p.accent2.copy(alpha = 0.06f))))
                .border(
                    1.dp,
                    Brush.linearGradient(listOf(p.accent.copy(alpha = 0.55f), p.cardBorder, p.accent2.copy(alpha = 0.45f))),
                    neoShape
                )
                .padding(18.dp),
            content = content
        )
        return
    }
    val shape = RoundedCornerShape(p.cornerDp.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(p.card)
            .border(1.dp, p.cardBorder, shape)
            .padding(18.dp),
        content = content
    )
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    colors: List<Color> = listOf(LocalPalette.current.accent, LocalPalette.current.accent2)
) {
    val p = LocalPalette.current
    val haptic = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "press"
    )
    val shape = RoundedCornerShape((p.cornerDp - 2f).dp)
    val base = modifier
        .fillMaxWidth()
        .height(60.dp)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clip(shape)
    val shimmer = if (Design.neo && filled) {
        val sh by androidx.compose.animation.core.rememberInfiniteTransition(label = "shimmer").animateFloat(
            initialValue = -0.4f, targetValue = 1.4f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                androidx.compose.animation.core.tween(2800, easing = androidx.compose.animation.core.LinearEasing)
            ),
            label = "shimmerX"
        )
        sh
    } else {
        -1f
    }
    val styled = if (filled) {
        base.background(Brush.horizontalGradient(colors)).then(
            if (shimmer > -0.99f) Modifier.drawWithContent {
                drawContent()
                val x = size.width * shimmer
                drawRect(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.28f), Color.Transparent),
                        startX = x - size.width * 0.25f, endX = x + size.width * 0.25f
                    )
                )
            } else Modifier
        )
    } else {
        base
            .background(colors.first().copy(alpha = 0.08f))
            .border(2.dp, colors.first(), shape)
    }
    Box(
        modifier = styled.clickable(interactionSource = source, indication = null) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (filled) p.onAccent else colors.first(),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}

/** Outline of cards and fields: a plain line in Classic, a two-colour glow in Neo. */
@Composable
fun Modifier.cardBorder(shape: androidx.compose.ui.graphics.Shape, width: androidx.compose.ui.unit.Dp = 1.dp): Modifier {
    val p = LocalPalette.current
    return if (Design.neo) {
        this.border(width, Brush.linearGradient(listOf(p.accent.copy(alpha = 0.50f), p.cardBorder, p.accent2.copy(alpha = 0.42f))), shape)
    } else {
        this.border(width, p.cardBorder, shape)
    }
}

/** Background of full-screen pages and dialogs: plain gradient in Classic, gradient plus drifting light in Neo. */
@Composable
fun Modifier.screenBackdrop(): Modifier {
    val p = LocalPalette.current
    return if (!Design.neo) {
        this.background(Brush.verticalGradient(listOf(p.bgTop, p.bgBottom)))
    } else {
        val t by androidx.compose.animation.core.rememberInfiniteTransition(label = "backdrop").animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                androidx.compose.animation.core.tween(30000, easing = androidx.compose.animation.core.LinearEasing)
            ),
            label = "backdropT"
        )
        this.drawBehind {
            drawRect(Brush.verticalGradient(listOf(p.bgTop, p.bgBottom)))
            drawNeoAurora(p, t, 0.3f)
        }
    }
}
