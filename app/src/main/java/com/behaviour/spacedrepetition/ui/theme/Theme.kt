package com.behaviour.spacedrepetition.ui.theme

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
    primary = PrimaryDark,
    onPrimary = Color.White,
    primaryContainer = DarkCard,
    onPrimaryContainer = PrimaryVariant,
    secondary = SecondaryDark,
    onSecondary = Color.White,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = SecondaryDark,
    tertiary = AccentGreen,
    onTertiary = Color.Black,
    background = DarkBackground,
    onBackground = TextWhite,
    surface = DarkSurface,
    onSurface = TextWhite,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextGray,
    error = ErrorRed,
    onError = Color.White,
    outline = TextGray.copy(alpha = 0.3f)
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = Color.White,
    primaryContainer = LightCard,
    onPrimaryContainer = PrimaryLight,
    secondary = SecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = LightSurfaceVariant,
    onSecondaryContainer = SecondaryLight,
    tertiary = AccentGreenDark,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = TextDarkPrimary,
    surface = LightSurface,
    onSurface = TextDarkPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextDarkSecondary,
    error = ErrorRed,
    onError = Color.White,
    outline = TextDarkSecondary.copy(alpha = 0.3f)
)

@Composable
fun BehaviourTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = BehaviourTypography,
        content = content
    )
}
