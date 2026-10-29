package com.maxworld.app

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// ---------------------------------------------------------------- activity log

@Composable
fun LogsDialog(onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val observed = AppLog.version
    val lines = AppLog.lines().asReversed()

    FullDialog(onDismiss) {
        Text(
            stringResource(R.string.logs_title),
            color = p.text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(top = 24.dp, bottom = 12.dp)
        )
        val shape = RoundedCornerShape(p.cornerDp.dp)
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(shape)
                .background(p.card)
                .border(1.dp, p.cardBorder, shape)
                .padding(14.dp)
        ) {
            if (lines.isEmpty()) {
                Text(stringResource(R.string.logs_empty), color = p.muted, fontSize = 14.sp)
            } else {
                LazyColumn {
                    items(lines) { l ->
                        Text(
                            l, color = p.text, fontSize = 12.sp,
                            style = TextStyle(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PrimaryButton(stringResource(R.string.usage_clear), { AppLog.clear() }, Modifier.weight(1f), filled = false)
            PrimaryButton(stringResource(R.string.logs_copy), {
                val cm = ctx.getSystemService(ClipboardManager::class.java)
                cm.setPrimaryClip(ClipData.newPlainText("MaxWorld log", AppLog.lines().joinToString("\n")))
                Toast.makeText(ctx, R.string.toast_copied, Toast.LENGTH_SHORT).show()
            }, Modifier.weight(1f), filled = false)
            PrimaryButton(stringResource(R.string.close), onDismiss, Modifier.weight(1f))
        }
    }
}

// ---------------------------------------------------------------- troubleshooter

@Composable
fun DoctorDialog(onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var round by remember { mutableIntStateOf(0) }
    var checks by remember { mutableStateOf<List<Check>>(emptyList()) }
    var running by remember { mutableStateOf(true) }

    LaunchedEffect(round) {
        running = true
        checks = emptyList()
        try {
            Doctor.run(ctx) { checks = it }
        } catch (e: Exception) {
        }
        running = false
    }

    FullDialog(onDismiss) {
        Text(
            stringResource(R.string.dr_title),
            color = p.text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(top = 24.dp)
        )
        Text(
            stringResource(if (running) R.string.dr_running else R.string.doctor_desc),
            color = p.muted, fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
        )
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(checks) { c ->
                val color = when (c.state) {
                    Check.OK -> p.ok
                    Check.BAD -> p.danger
                    else -> p.muted
                }
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(c.title), color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            if (c.extra.isNotEmpty()) Text(c.extra, color = p.muted, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                    if (c.state == Check.BAD && c.hint != 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(c.hint), color = p.warn, fontSize = 13.sp)
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PrimaryButton(
                stringResource(R.string.dr_again),
                { if (!running) round++ },
                Modifier.weight(1f), filled = false
            )
            PrimaryButton(stringResource(R.string.close), onDismiss, Modifier.weight(1f))
        }
    }
}

// ---------------------------------------------------------------- usage

@Composable
fun UsageCard() {
    val p = LocalPalette.current
    var tick by remember { mutableIntStateOf(0) }
    val days = remember(tick) { Usage.lastDays(7) }
    val today = days.last()
    val week = days.sumOf { it.down + it.up }
    val top = maxOf(days.maxOf { it.down + it.up }, 1L)

    GlassCard(Modifier.fillMaxWidth().fadeSlideIn(100)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(stringResource(R.string.usage_today), color = p.muted, fontSize = 12.sp)
                Text(Traffic.bytes(today.down + today.up), color = p.text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.usage_week), color = p.muted, fontSize = 12.sp)
                Text(Traffic.bytes(week), color = p.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(14.dp))
        Canvas(Modifier.fillMaxWidth().height(90.dp)) {
            val slot = size.width / days.size
            val barW = slot * 0.5f
            days.forEachIndexed { i, d ->
                val x = i * slot + (slot - barW) / 2f
                val hDown = size.height * (d.down.toFloat() / top)
                val hUp = size.height * (d.up.toFloat() / top)
                val r = CornerRadius(barW / 4f)
                // faint track
                drawRoundRect(p.track, Offset(x, 0f), Size(barW, size.height), r)
                if (hDown > 0f) {
                    drawRoundRect(
                        Brush.verticalGradient(listOf(p.accent, p.accent2)),
                        Offset(x, size.height - hDown), Size(barW, hDown), r
                    )
                }
                if (hUp > 0f) {
                    drawRoundRect(
                        p.accent2.copy(alpha = 0.55f),
                        Offset(x, size.height - hDown - hUp), Size(barW, hUp), r
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            days.forEach {
                Text(
                    it.label, color = p.muted, fontSize = 11.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.usage_note), color = p.muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Chip(stringResource(R.string.usage_clear), false) {
                Usage.clear()
                tick++
            }
        }
    }
}

// ---------------------------------------------------------------- shared

@Composable
private fun FullDialog(onDismiss: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(p.bgTop, p.bgBottom)))
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), content = content)
        }
    }
}
