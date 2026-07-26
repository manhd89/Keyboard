package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = BrandPrimaryDark,
    onPrimary = Color(0xFF09090B),
    primaryContainer = NeutralDarkSurfaceElevated,
    onPrimaryContainer = NeutralDarkTextPrimary,
    secondary = NeutralDarkTextSecondary,
    onSecondary = Color.White,
    background = NeutralDarkBackground,
    onBackground = NeutralDarkTextPrimary,
    surface = NeutralDarkSurface,
    onSurface = NeutralDarkTextPrimary,
    surfaceVariant = NeutralDarkSurfaceElevated,
    onSurfaceVariant = NeutralDarkTextSecondary,
    outline = NeutralDarkBorder,
    outlineVariant = Color(0xFF333740)
)

private val LightColorScheme = lightColorScheme(
    primary = BrandAccentLight,
    onPrimary = Color.White,
    primaryContainer = NeutralLightSurfaceElevated,
    onPrimaryContainer = NeutralLightTextPrimary,
    secondary = NeutralLightTextSecondary,
    onSecondary = Color.White,
    background = NeutralLightBackground,
    onBackground = NeutralLightTextPrimary,
    surface = NeutralLightSurface,
    onSurface = NeutralLightTextPrimary,
    surfaceVariant = NeutralLightSurfaceElevated,
    onSurfaceVariant = NeutralLightTextSecondary,
    outline = NeutralLightBorder,
    outlineVariant = Color(0xFFD4D4D8)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
