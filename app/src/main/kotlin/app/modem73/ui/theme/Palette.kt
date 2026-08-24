package app.modem73.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class Modem73Palette(
    val logoPurple: Color,
    val rx: Color,
    val tx: Color,
    val warn: Color,
    val info: Color,
    val special: Color,
    val ok: Color,
    val onAccent: Color,
    val waterfallMarker: Color,
    val waterfall: List<Color>
)

val WaterfallPalette31: List<Color> = listOf(
    0x000000, 0x00005F, 0x000087, 0x0000AF, 0x0000D7, 0x0000FF, 0x005FFF, 0x0087FF, 0x00AFFF, 0x00D7FF, 0x00FFFF,
    0x00FFD7, 0x00FFAF, 0x00FF87, 0x00FF5F, 0x00FF00, 0x5FFF00, 0x87FF00, 0xAFFF00, 0xD7FF00, 0xFFFF00,
    0xFFD700, 0xFFAF00, 0xFF8700, 0xFF5F00, 0xFF0000, 0xFF005F, 0xFF0087, 0xFF00AF, 0xFF00D7, 0xFF00FF
).map { Color(0xFF000000 or it.toLong()) }

val DarkPalette = Modem73Palette(
    logoPurple = Color(0xFFA78BFA),
    rx = Color(0xFF4ADE80),
    tx = Color(0xFFF87171),
    warn = Color(0xFFFACC15),
    info = Color(0xFF22D3EE),
    special = Color(0xFFE879F9),
    ok = Color(0xFF4ADE80),
    onAccent = Color(0xFF111114),
    waterfallMarker = Color(0xFFFFFFFF),
    waterfall = WaterfallPalette31
)

val LightPalette = Modem73Palette(
    logoPurple = Color(0xFF6D3FD9),
    rx = Color(0xFF1B8F3A),
    tx = Color(0xFFC62828),
    warn = Color(0xFFB7860B),
    info = Color(0xFF0E7C86),
    special = Color(0xFFA2189A),
    ok = Color(0xFF1B8F3A),
    onAccent = Color(0xFFFFFFFF),
    waterfallMarker = Color(0xFFFFFFFF),
    waterfall = WaterfallPalette31
)

val LocalModem73Palette = staticCompositionLocalOf { DarkPalette }

object Modem73Colors {
    val logoPurple: Color @Composable @ReadOnlyComposable get() = LocalModem73Palette.current.logoPurple
    val rx: Color @Composable @ReadOnlyComposable get() = LocalModem73Palette.current.rx
    val tx: Color @Composable @ReadOnlyComposable get() = LocalModem73Palette.current.tx
    val warn: Color @Composable @ReadOnlyComposable get() = LocalModem73Palette.current.warn
    val info: Color @Composable @ReadOnlyComposable get() = LocalModem73Palette.current.info
    val special: Color @Composable @ReadOnlyComposable get() = LocalModem73Palette.current.special
    val ok: Color @Composable @ReadOnlyComposable get() = LocalModem73Palette.current.ok
    val onAccent: Color @Composable @ReadOnlyComposable get() = LocalModem73Palette.current.onAccent
    val waterfallMarker: Color @Composable @ReadOnlyComposable get() = LocalModem73Palette.current.waterfallMarker
    val waterfall: List<Color> @Composable @ReadOnlyComposable get() = LocalModem73Palette.current.waterfall
}
