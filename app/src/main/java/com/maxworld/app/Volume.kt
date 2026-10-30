package com.maxworld.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Remaining data and days of the selected server's subscription, read from the provider's own numbers.
 * A row of small cells empties as the data is used up. Hidden when the provider sends no numbers.
 */
@Composable
fun DataTank(cfg: VpnConfig?) {
    val p = LocalPalette.current
    val sub = cfg?.let { c -> Subs.list.firstOrNull { it.id == c.group } } ?: return
    // keep the numbers fresh: ask the provider again when they are older than 15 minutes
    LaunchedEffect(sub.id) {
        try {
            Subs.refreshIfStale(sub.id)
        } catch (ignored: Exception) {
        }
    }
    val hasData = sub.total > 0L
    val hasDays = sub.expire > 0L
    if (!hasData && !hasDays) return

    val left = (sub.total - sub.used).coerceAtLeast(0L)
    val frac = if (hasData) left.toFloat() / sub.total.toFloat() else 1f
    val days = if (hasDays) Math.ceil((sub.expire - System.currentTimeMillis()) / 86400000.0).toLong() else 0L
    val color = when {
        hasData && frac <= 0.10f -> p.danger
        hasData && frac <= 0.30f -> p.warn
        else -> p.ok
    }
    val cells = 20
    val lit = if (hasData) Math.ceil(frac * cells.toDouble()).toInt().coerceIn(if (left > 0) 1 else 0, cells) else cells

    val scope = androidx.compose.runtime.rememberCoroutineScope()
    GlassCard(
        Modifier
            .fillMaxWidth()
            .fadeSlideIn(160)
            .clickable { scope.launch { try { Subs.refresh(sub) } catch (ignored: Exception) {} } }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (hasData) {
                    Text(stringResource(R.string.tank_left), color = p.muted, fontSize = 11.sp)
                    Text(Traffic.bytes(left), color = color, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        stringResource(R.string.tank_of_fmt, Traffic.bytes(sub.total), Traffic.bytes(sub.used)),
                        color = p.muted, fontSize = 11.sp
                    )
                } else {
                    Text(stringResource(R.string.tank_unlimited), color = p.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (hasDays) {
                Column(horizontalAlignment = Alignment.End) {
                    if (days < 0) {
                        Text(stringResource(R.string.sub_expired), color = p.danger, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    } else {
                        Text(
                            days.toString(),
                            color = if (days <= 3) p.warn else p.text,
                            fontSize = 30.sp, fontWeight = FontWeight.ExtraBold
                        )
                        Text(stringResource(R.string.tank_days), color = p.muted, fontSize = 11.sp)
                    }
                }
            }
        }
        if (hasData) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(cells) { i ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (i < lit) color else p.track)
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
}
