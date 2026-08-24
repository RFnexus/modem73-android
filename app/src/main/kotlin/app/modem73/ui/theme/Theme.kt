package app.modem73.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFA78BFA),
    onPrimary = Color(0xFF16121F),
    secondary = Color(0xFF22D3EE),
    onSecondary = Color(0xFF071A1D),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF000000),
    onSurfaceVariant = Color(0xFFA8ACB3),
    outline = Color(0xFF3C3F46),
    outlineVariant = Color(0xFF26282E),
    error = Color(0xFFF87171),
    onError = Color(0xFF000000)
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF6D3FD9),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF0E7C86),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF111111),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFF5B606A),
    outline = Color(0xFFCDD1D8),
    outlineVariant = Color(0xFFE6E8EC),
    error = Color(0xFFC62828),
    onError = Color(0xFFFFFFFF)
)

@Composable
fun Modem73Theme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    val palette = if (darkTheme) DarkPalette else LightPalette
    CompositionLocalProvider(LocalModem73Palette provides palette) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
