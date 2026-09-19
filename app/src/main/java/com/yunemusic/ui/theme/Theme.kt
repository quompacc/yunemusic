package com.yunemusic.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val YuneDarkColorScheme = darkColorScheme(
    primary = SignalOrange,
    onPrimary = OnPrimary,
    primaryContainer = SignalContainer,
    onPrimaryContainer = OnSignalContainer,
    secondary = SagePrimary,
    onSecondary = OnSecondary,
    secondaryContainer = Color(0xFF303B26),
    onSecondaryContainer = SageLight,
    tertiary = CyanAccent,
    onTertiary = Black,
    tertiaryContainer = Color(0xFF3E3826),
    onTertiaryContainer = CyanAccent,
    background = DarkBackground,
    onBackground = OnBackground,
    surface = SurfaceDark,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondary,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    error = ErrorRed,
    onError = OnPrimary,
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFECACA),
    inverseSurface = TextPrimary,
    inverseOnSurface = DarkBackground,
    inversePrimary = SignalDark,
    surfaceTint = SignalOrange,
    scrim = Black
)

@Composable
fun YuneMusicTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = YuneDarkColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = DarkBackground.toArgb()
            window.navigationBarColor = DarkBackground.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = YuneTypography,
        content = content
    )
}
