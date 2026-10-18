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
    primary = StaticBrandBlue,
    onPrimary = Color.White,
    primaryContainer = StaticBrandBlueDark,
    onPrimaryContainer = Color.White,
    secondary = StaticBrandBlue,
    onSecondary = Color.White,
    tertiary = WarningAmber,
    background = DarkBg,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkCard,
    onSurfaceVariant = DarkMuted,
    outline = DarkBorder,
    error = DangerRed,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = StaticBrandBlue,
    onPrimary = Color.White,
    primaryContainer = StaticBrandBlueSoft,
    onPrimaryContainer = StaticBrandBlueDark,
    secondary = StaticBrandBlue,
    onSecondary = Color.White,
    tertiary = WarningAmber,
    background = LightBg,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = LightCard,
    onSurfaceVariant = LightMuted,
    outline = LightBorder,
    error = DangerRed,
    onError = Color.White
)

@Composable
fun StockManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    customThemeColor: String = "#007AFF",
    content: @Composable () -> Unit
) {
    val baseColor = try {
        Color(android.graphics.Color.parseColor(customThemeColor))
    } catch (e: Exception) {
        Color(0xFF007AFF)
    }

    val isLightPrimary = (baseColor.red * 0.299f + baseColor.green * 0.587f + baseColor.blue * 0.114f) > 0.65f
    val onPrimaryColor = if (isLightPrimary) Color(0xFF0F172A) else Color.White

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme.copy(
            primary = baseColor,
            onPrimary = onPrimaryColor,
            secondary = baseColor,
            onSecondary = onPrimaryColor,
            primaryContainer = if (isLightPrimary) Color(0xFF334155) else baseColor.copy(alpha = 0.22f),
            onPrimaryContainer = if (isLightPrimary) Color.White else Color.White
        )
        else -> LightColorScheme.copy(
            primary = baseColor,
            onPrimary = onPrimaryColor,
            secondary = baseColor,
            onSecondary = onPrimaryColor,
            primaryContainer = if (isLightPrimary) Color(0xFFE2E8F0) else baseColor.copy(alpha = 0.15f),
            onPrimaryContainer = if (isLightPrimary) Color(0xFF0F172A) else StaticBrandBlueDark
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
