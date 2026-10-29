package com.maxworld.app

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** Every visual design is a Palette: colors plus a few shape choices. */
data class Palette(
    val id: String,
    @StringRes val nameRes: Int,
    val light: Boolean,
    val bgTop: Color,
    val bgBottom: Color,
    val card: Color,
    val cardBorder: Color,
    val track: Color,
    val accent: Color,
    val accent2: Color,
    val danger: Color,
    val warn: Color,
    val ok: Color,
    val text: Color,
    val muted: Color,
    val onAccent: Color,
    val cornerDp: Float,
    /** 0 dark, 1 light, 2 colorful. */
    val group: Int = 0
)

val NeonPalette = Palette(
    id = "neon", nameRes = R.string.theme_neon, light = false,
    bgTop = Color(0xFF0B1030), bgBottom = Color(0xFF04060F),
    card = Color(0xD9111A33), cardBorder = Color(0xFF22305A), track = Color(0xFF18223F),
    accent = Color(0xFF00E5FF), accent2 = Color(0xFF7C4DFF),
    danger = Color(0xFFFF4D6D), warn = Color(0xFFFFB74D), ok = Color(0xFF00F5A0),
    text = Color(0xFFEAF2FF), muted = Color(0xFF8A97B5), onAccent = Color(0xFF03111A),
    cornerDp = 24f, group = 0
)

val GlassPalette = Palette(
    id = "glass", nameRes = R.string.theme_glass, light = false,
    bgTop = Color(0xFF4A1D96), bgBottom = Color(0xFF0F1B4D),
    card = Color(0x33FFFFFF), cardBorder = Color(0x55FFFFFF), track = Color(0x33FFFFFF),
    accent = Color(0xFFFF7AD9), accent2 = Color(0xFF7AE7FF),
    danger = Color(0xFFFF5C7A), warn = Color(0xFFFFD27A), ok = Color(0xFF7AFFC4),
    text = Color(0xFFFFFFFF), muted = Color(0xFFC9C3F0), onAccent = Color(0xFF1B0B3A),
    cornerDp = 32f, group = 2
)

val AmoledPalette = Palette(
    id = "amoled", nameRes = R.string.theme_amoled, light = false,
    bgTop = Color(0xFF000000), bgBottom = Color(0xFF000000),
    card = Color(0xFF0D0D0D), cardBorder = Color(0xFF1F1F1F), track = Color(0xFF1A1A1A),
    accent = Color(0xFF00E676), accent2 = Color(0xFF00BFA5),
    danger = Color(0xFFFF3B3B), warn = Color(0xFFFFB300), ok = Color(0xFF00E676),
    text = Color(0xFFFFFFFF), muted = Color(0xFF7A7A7A), onAccent = Color(0xFF000000),
    cornerDp = 18f, group = 0
)

val MinimalPalette = Palette(
    id = "minimal", nameRes = R.string.theme_minimal, light = true,
    bgTop = Color(0xFFF6F7FB), bgBottom = Color(0xFFEDEFF7),
    card = Color(0xFFFFFFFF), cardBorder = Color(0xFFE2E5F0), track = Color(0xFFE3E6F1),
    accent = Color(0xFF2F6BFF), accent2 = Color(0xFF6C8DFF),
    danger = Color(0xFFE5334F), warn = Color(0xFFF59E0B), ok = Color(0xFF16A34A),
    text = Color(0xFF14182B), muted = Color(0xFF7A8099), onAccent = Color(0xFFFFFFFF),
    cornerDp = 20f, group = 1
)


/** Themes built from a few colors; the rest (cards, borders, text) is derived. */
private fun hex(s: String) = Color(0xFF000000 or s.toLong(16))

private val SPECS = listOf(
    Spec("midnight", R.string.theme_midnight, 0, false, "0B0F1A", "05070D", "6C8CFF", "A06CFF"),
    Spec("ocean", R.string.theme_ocean, 0, false, "06202B", "021017", "00C2D1", "3A86FF"),
    Spec("forest", R.string.theme_forest, 0, false, "0B1F14", "04100A", "3DDC84", "B6F26B"),
    Spec("sunset", R.string.theme_sunset, 2, false, "2A0F2E", "12060F", "FF7A59", "FFC857"),
    Spec("rose", R.string.theme_rose, 0, false, "2B0F1C", "12060B", "FF4D8D", "FF9CC2"),
    Spec("volcano", R.string.theme_volcano, 0, false, "1F0B0B", "0D0404", "FF5A36", "FFB000"),
    Spec("aurora", R.string.theme_aurora, 2, false, "071A2B", "0A0A1F", "3CF0C5", "8A5CFF"),
    Spec("cyber", R.string.theme_cyber, 2, false, "0A0A12", "000000", "F5FF00", "FF00E5"),
    Spec("mocha", R.string.theme_mocha, 0, false, "1E1612", "0E0A08", "D9A066", "F2D3A0"),
    Spec("slate", R.string.theme_slate, 0, false, "1B2028", "0F1318", "7DD3FC", "A5B4FC"),
    Spec("violet", R.string.theme_violet, 0, false, "1A0B33", "0A0418", "B388FF", "FF80AB"),
    Spec("emerald", R.string.theme_emerald, 0, false, "062A22", "02130F", "10E0A0", "22D3EE"),
    Spec("gold", R.string.theme_gold, 0, false, "14110A", "080604", "FFC83D", "FF8F3D"),
    Spec("candy", R.string.theme_candy, 2, false, "6A1B9A", "1A0B3D", "FF8AD8", "8AF0FF"),
    Spec("lava", R.string.theme_lava, 2, false, "3A0A0A", "14040A", "FF3D00", "FFD600"),
    Spec("galaxy", R.string.theme_galaxy, 2, false, "1B0E4B", "060316", "7C4DFF", "18FFFF"),
    Spec("mono", R.string.theme_mono, 0, false, "1A1A1A", "0A0A0A", "FFFFFF", "BDBDBD"),
    Spec("snow", R.string.theme_snow, 1, true, "F8FAFC", "EEF2F7", "0EA5E9", "6366F1"),
    Spec("mint", R.string.theme_mint, 1, true, "F0FDF9", "DDF7EE", "10B981", "06B6D4"),
    Spec("peach", R.string.theme_peach, 1, true, "FFF6F0", "FFE6D6", "F97316", "EC4899"),
    Spec("lavender", R.string.theme_lavender, 1, true, "F6F2FF", "E9E0FF", "8B5CF6", "EC4899"),
    Spec("sky", R.string.theme_sky, 1, true, "EFF8FF", "DCEEFF", "2563EB", "06B6D4"),
    Spec("sand", R.string.theme_sand, 1, true, "FAF6EE", "EFE6D2", "B7791F", "D97706"),
    Spec("sakura", R.string.theme_sakura, 1, true, "FFF1F5", "FFDDE7", "E11D74", "FB7185"),
    Spec("lemon", R.string.theme_lemon, 1, true, "FFFDE7", "FFF59D", "F59E0B", "84CC16"),
)

private class Spec(
    val id: String, @StringRes val name: Int, val group: Int, val light: Boolean,
    val top: String, val bottom: String, val a1: String, val a2: String
)

private fun derive(
    id: String, @StringRes name: Int, group: Int, light: Boolean,
    top: Color, bottom: Color, a1: Color, a2: Color
): Palette {
    val onAccent = if (a1.luminance() > 0.5f) Color(0xFF0A0A0A) else Color.White
    return if (!light) {
        val text = Color(0xFFF2F6FF)
        Palette(
            id = id, nameRes = name, light = false, bgTop = top, bgBottom = bottom,
            card = lerp(top, Color.White, 0.07f).copy(alpha = 0.92f),
            cardBorder = lerp(top, Color.White, 0.18f), track = lerp(top, Color.White, 0.12f),
            accent = a1, accent2 = a2,
            danger = Color(0xFFFF4D6D), warn = Color(0xFFFFB74D), ok = Color(0xFF00F5A0),
            text = text, muted = lerp(text, top, 0.55f), onAccent = onAccent,
            cornerDp = 24f, group = group
        )
    } else {
        Palette(
            id = id, nameRes = name, light = true, bgTop = top, bgBottom = bottom,
            card = Color.White, cardBorder = lerp(bottom, Color.Black, 0.10f), track = lerp(bottom, Color.Black, 0.07f),
            accent = a1, accent2 = a2,
            danger = Color(0xFFE5334F), warn = Color(0xFFF59E0B), ok = Color(0xFF16A34A),
            text = Color(0xFF14182B), muted = Color(0xFF6B7390), onAccent = onAccent,
            cornerDp = 22f, group = group
        )
    }
}

object Themes {
    private val built: List<Palette> by lazy {
        SPECS.map { derive(it.id, it.name, it.group, it.light, hex(it.top), hex(it.bottom), hex(it.a1), hex(it.a2)) }
    }

    /** Ready-made themes, in the order they are shown. */
    val all: List<Palette> by lazy { listOf(NeonPalette, GlassPalette, AmoledPalette, MinimalPalette) + built }

    // The user's own theme: one main color plus dark or light.
    var customAccent by mutableIntStateOf(0xFF7C4DFF.toInt())
    var customLight by mutableStateOf(false)

    val swatches = listOf(
        0xFF7C4DFF, 0xFF00E5FF, 0xFF00E676, 0xFFFFC107, 0xFFFF7043,
        0xFFFF4081, 0xFF2979FF, 0xFFE91E63, 0xFF1DE9B6, 0xFFAEEA00
    ).map { it.toInt() }

    fun loadCustom(ctx: Context) {
        customAccent = Prefs.customAccent(ctx)
        customLight = Prefs.customLight(ctx)
    }

    fun saveCustom(ctx: Context, accent: Int, light: Boolean) {
        customAccent = accent
        customLight = light
        Prefs.setCustom(ctx, accent, light)
    }

    fun custom(): Palette {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(customAccent, hsv)
        val h = hsv[0]
        fun c(s: Float, v: Float) = Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)))
        val a1 = Color(customAccent)
        val a2 = Color(android.graphics.Color.HSVToColor(floatArrayOf((h + 40f) % 360f, hsv[1].coerceAtLeast(0.5f), hsv[2].coerceAtLeast(0.8f))))
        return if (customLight) {
            derive("custom", R.string.theme_custom, 1, true, c(0.05f, 0.99f), c(0.12f, 0.95f), a1, a2)
        } else {
            derive("custom", R.string.theme_custom, 0, false, c(0.60f, 0.20f), c(0.70f, 0.07f), a1, a2)
        }
    }

    fun byId(id: String): Palette =
        if (id == "custom") custom() else all.firstOrNull { it.id == id } ?: NeonPalette
}

val LocalPalette = staticCompositionLocalOf { NeonPalette }

@Composable
fun MaxWorldTheme(target: Palette = NeonPalette, content: @Composable () -> Unit) {
    // Colors glide to the new theme instead of jumping.
    val palette = target.copy(
        bgTop = ac(target.bgTop), bgBottom = ac(target.bgBottom),
        card = ac(target.card), cardBorder = ac(target.cardBorder), track = ac(target.track),
        accent = ac(target.accent), accent2 = ac(target.accent2),
        danger = ac(target.danger), warn = ac(target.warn), ok = ac(target.ok),
        text = ac(target.text), muted = ac(target.muted), onAccent = ac(target.onAccent)
    )
    val scheme = if (palette.light) {
        lightColorScheme(
            primary = palette.accent, onPrimary = palette.onAccent,
            background = palette.bgBottom, onBackground = palette.text,
            surface = palette.card, onSurface = palette.text
        )
    } else {
        darkColorScheme(
            primary = palette.accent, onPrimary = palette.onAccent,
            background = palette.bgBottom, onBackground = palette.text,
            surface = palette.card, onSurface = palette.text
        )
    }
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalPalette provides palette) {
            content()
        }
    }
}

@Composable
private fun ac(v: Color): Color = animateColorAsState(v, tween(500), label = "palette").value

/** Transparent system bars with icons that stay readable on light or dark themes. */
fun ComponentActivity.applySystemBars(light: Boolean) {
    val t = android.graphics.Color.TRANSPARENT
    val style = if (light) SystemBarStyle.light(t, t) else SystemBarStyle.dark(t)
    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
}
