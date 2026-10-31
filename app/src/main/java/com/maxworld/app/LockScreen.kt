package com.maxworld.app

import android.app.Activity
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private const val PIN_LEN = 4

/** Shown over the app when the lock is on. */
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }
    val bio = remember { AppLock.bioEnabled(ctx) && AppLock.bioAvailable(ctx) }

    LaunchedEffect(Unit) {
        if (bio) (ctx as? Activity)?.let { AppLock.promptBio(it, onUnlock) }
    }
    LaunchedEffect(pin) {
        if (pin.length == PIN_LEN) {
            if (AppLock.check(ctx, pin)) {
                onUnlock()
            } else {
                wrong = true
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                for (x in listOf(22f, -22f, 14f, -14f, 0f)) shake.animateTo(x, tween(60))
                pin = ""
            }
        }
    }

    PinLayout(
        title = stringResource(R.string.lock_title),
        subtitle = stringResource(if (wrong) R.string.lock_wrong else R.string.lock_enter),
        error = wrong,
        pin = pin,
        shakeX = shake.value,
        onDigit = { if (pin.length < PIN_LEN) { wrong = false; pin += it } },
        onBack = { pin = pin.dropLast(1) },
        onBio = if (bio) ({ (ctx as? Activity)?.let { AppLock.promptBio(it, onUnlock) } }) else null
    )
}

/** Two-step PIN creation: type it, then type it again. */
@Composable
fun PinSetupDialog(onDismiss: () -> Unit, onSet: (String) -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf("") }
    var mismatch by remember { mutableStateOf(false) }

    LaunchedEffect(pin) {
        if (pin.length == PIN_LEN) {
            val f = first
            if (f == null) {
                first = pin
                pin = ""
                mismatch = false
            } else if (f == pin) {
                onSet(pin)
            } else {
                mismatch = true
                first = null
                pin = ""
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        PinLayout(
            title = stringResource(R.string.lock_switch_title),
            subtitle = stringResource(
                when {
                    mismatch -> R.string.lock_mismatch
                    first == null -> R.string.lock_new
                    else -> R.string.lock_again
                }
            ),
            error = mismatch,
            pin = pin,
            shakeX = 0f,
            onDigit = { if (pin.length < PIN_LEN) { mismatch = false; pin += it } },
            onBack = { pin = pin.dropLast(1) },
            onBio = null
        )
    }
}

@Composable
private fun PinLayout(
    title: String,
    subtitle: String,
    error: Boolean,
    pin: String,
    shakeX: Float,
    onDigit: (Char) -> Unit,
    onBack: () -> Unit,
    onBio: (() -> Unit)?
) {
    val p = LocalPalette.current
    Box(
        Modifier
            .fillMaxSize()
            .screenBackdrop()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(title, color = p.text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(8.dp))
            Text(
                subtitle,
                color = if (error) p.danger else p.muted,
                fontSize = 14.sp, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))
            Row(
                Modifier.graphicsLayer { translationX = shakeX * density },
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                repeat(PIN_LEN) { i ->
                    val on = i < pin.length
                    Box(
                        Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(if (on) (if (error) p.danger else p.accent) else p.track)
                    )
                }
            }
            Spacer(Modifier.height(36.dp))

            val rows = listOf(listOf('1', '2', '3'), listOf('4', '5', '6'), listOf('7', '8', '9'))
            rows.forEach { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    r.forEach { d -> Key(d.toString()) { onDigit(d) } }
                }
                Spacer(Modifier.height(16.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                if (onBio != null) Key("☝", onBio) else Spacer(Modifier.size(72.dp))
                Key("0") { onDigit('0') }
                Key("⌫", onBack)
            }
        }
    }
}

@Composable
private fun Key(label: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(p.card)
            .cardBorder(CircleShape)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = p.text, fontSize = 26.sp, fontWeight = FontWeight.Medium)
    }
}
