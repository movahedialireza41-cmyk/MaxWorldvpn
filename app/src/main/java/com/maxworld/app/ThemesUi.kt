package com.maxworld.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Full-screen theme picker. Tapping a theme applies it at once, so the screen itself is the preview. */
@Composable
fun ThemesDialog(themeId: String, onTheme: (String) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var cat by remember { mutableIntStateOf(0) } // 0 all, 1 dark, 2 light, 3 colorful, 4 own

    val list = Themes.all.filter {
        when (cat) {
            1 -> it.group == 0
            2 -> it.group == 1
            3 -> it.group == 2
            else -> true
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .screenBackdrop()
                .statusBarsPadding()
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.theme_title), color = p.text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                        Text(stringResource(R.string.themes_hint), color = p.muted, fontSize = 13.sp)
                    }
                    Text(
                        stringResource(R.string.close),
                        color = p.accent, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onDismiss() }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        R.string.cat_all, R.string.cat_dark, R.string.cat_light, R.string.cat_color, R.string.cat_custom
                    ).forEachIndexed { i, res -> Chip(stringResource(res), cat == i) { cat = i } }
                }
                Spacer(Modifier.height(12.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 30.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (cat != 4) {
                        items(list, key = { it.id }) { t ->
                            ThemeTile(t, selected = t.id == themeId) { onTheme(t.id) }
                        }
                    }
                    if (cat == 0 || cat == 4) {
                        item(key = "custom_tile") {
                            ThemeTile(Themes.custom(), selected = themeId == "custom") { onTheme("custom") }
                        }
                    }
                    if (cat == 4) {
                        item(key = "custom_builder", span = { GridItemSpan(maxLineSpan) }) {
                            CustomBuilder(onChanged = { accent, light ->
                                Themes.saveCustom(ctx, accent, light)
                                onTheme("custom")
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomBuilder(onChanged: (Int, Boolean) -> Unit) {
    val p = LocalPalette.current
    GlassCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.custom_title), color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.custom_color), color = p.muted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Themes.swatches.forEach { argb ->
                val on = Themes.customAccent == argb
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(androidx.compose.ui.graphics.Color(argb))
                        .border(if (on) 3.dp else 1.dp, if (on) p.text else p.cardBorder, CircleShape)
                        .clickable { onChanged(argb, Themes.customLight) }
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(stringResource(R.string.custom_dark), !Themes.customLight) { onChanged(Themes.customAccent, false) }
            Chip(stringResource(R.string.custom_light), Themes.customLight) { onChanged(Themes.customAccent, true) }
        }
    }
}

@Composable
private fun ThemeTile(t: Palette, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(16.dp)
    val border by animateColorAsState(if (selected) p.accent else p.cardBorder, tween(300), label = "tileBorder")
    Column(
        Modifier
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
                .height(70.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.verticalGradient(listOf(t.bgTop, t.bgBottom)))
        ) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 10.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(t.accent, t.accent2)))
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
                    .fillMaxWidth(0.7f)
                    .height(9.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(t.card)
                    .border(1.dp, t.cardBorder, RoundedCornerShape(5.dp))
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(t.nameRes),
            color = if (selected) p.accent else p.muted,
            fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1
        )
    }
}
