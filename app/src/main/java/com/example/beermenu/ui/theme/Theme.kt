package com.example.beermenu.ui.theme

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

private val BeerDarkScheme = darkColorScheme(
    primary = AmberPrimary,
    onPrimary = Color.White,
    primaryContainer = AmberDark,
    onPrimaryContainer = CreamWhite,
    secondary = TealLight,
    onSecondary = Color.White,
    secondaryContainer = TealMedium,
    onSecondaryContainer = CreamWhite,
    tertiary = AmberLight,
    onTertiary = DarkCharcoal,
    background = TealDark,
    onBackground = CreamWhite,
    surface = TealMedium,
    onSurface = CreamWhite,
    surfaceVariant = TealLight,
    onSurfaceVariant = Color(0xFFCCC5B9),
    outline = WarmGrey,
    error = BeerRed,
    onError = Color.White,
)

private val BeerLightScheme = lightColorScheme(
    primary = AmberPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE0B2),
    onPrimaryContainer = AmberDark,
    secondary = TealMedium,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB2DFDB),
    onSecondaryContainer = TealDark,
    tertiary = AmberLight,
    onTertiary = DarkCharcoal,
    background = CreamWhite,
    onBackground = DarkCharcoal,
    surface = Color.White,
    onSurface = DarkCharcoal,
    surfaceVariant = Color(0xFFEDE8E0),
    onSurfaceVariant = Color(0xFF5C5650),
    outline = WarmGrey,
    error = BeerRed,
    onError = Color.White,
)

@Composable
fun BeerMenuTheme(
    darkTheme: Boolean = true, // Beer menu looks best in dark mode
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) BeerDarkScheme else BeerLightScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = TealDark.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
