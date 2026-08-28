package com.example.kaskita.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = DarkPine,
    onPrimary = Color.Black,
    primaryContainer = DarkPineContainer,
    onPrimaryContainer = DarkPine,
    secondary = GoldAccent,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF382E18),
    onSecondaryContainer = GoldAccent,
    background = DarkBg,
    onBackground = DarkTextMain,
    surface = DarkCard,
    onSurface = DarkTextMain,
    surfaceVariant = Color(0xFF22312B),
    onSurfaceVariant = DarkTextMuted,
    outline = DarkBorder
)

private val LightColorScheme = lightColorScheme(
    primary = PineGreen,
    onPrimary = Color.White,
    primaryContainer = PineGreenLight,
    onPrimaryContainer = PineGreen,
    secondary = GoldAccent,
    onSecondary = Color.White,
    secondaryContainer = GoldContainer,
    onSecondaryContainer = Color(0xFF5D400A),
    background = NeutralBg,
    onBackground = TextMain,
    surface = CardBg,
    onSurface = TextMain,
    surfaceVariant = Color(0xFFEEF3F0),
    onSurfaceVariant = TextMuted,
    outline = BorderLight
)

@Composable
fun KasKitaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
