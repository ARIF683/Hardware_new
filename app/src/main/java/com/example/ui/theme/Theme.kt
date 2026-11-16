package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Immutable
data class ExtendedColors(
    val isDark: Boolean = false,
    val positive: Color = SuccessGreen,
    val warning: Color = WarningAmber,
    val danger: Color = DangerRed,
    val info: Color = InfoBlue,
    val purple: Color = AccentPurple,
    val heroBannerStart: Color = Color(0xFF131B2B),
    val heroBannerEnd: Color = Color(0xFF1E2638)
)

val LocalExtendedColors = staticCompositionLocalOf { ExtendedColors() }

private val DarkColorScheme = darkColorScheme(
    primary = HardwareOrangeBright,
    onPrimary = Color.White,
    primaryContainer = HardwareOrangeBright.copy(alpha = 0.18f),
    onPrimaryContainer = HardwareOrangeBright,
    secondary = InfoBlueBright,
    onSecondary = Color.White,
    secondaryContainer = InfoBlueBright.copy(alpha = 0.16f),
    onSecondaryContainer = InfoBlueBright,
    tertiary = WarningAmber,
    onTertiary = Color.White,
    background = DarkBg,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkCard,
    onSurfaceVariant = DarkMuted,
    outline = DarkBorder,
    outlineVariant = DarkBorder,
    error = DangerRed,
    onError = Color.White,
    errorContainer = DangerRed.copy(alpha = 0.18f),
    onErrorContainer = DangerRed
)

private val LightColorScheme = lightColorScheme(
    primary = HardwareOrange,
    onPrimary = Color.White,
    primaryContainer = HardwareOrange.copy(alpha = 0.12f),
    onPrimaryContainer = HardwareOrange,
    secondary = InfoBlue,
    onSecondary = Color.White,
    secondaryContainer = InfoBlue.copy(alpha = 0.12f),
    onSecondaryContainer = InfoBlue,
    tertiary = WarningAmber,
    onTertiary = Color.White,
    background = LightBg,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = LightCard,
    onSurfaceVariant = LightMuted,
    outline = LightBorder,
    outlineVariant = LightBorder,
    error = DangerRed,
    onError = Color.White,
    errorContainer = DangerRed.copy(alpha = 0.12f),
    onErrorContainer = DangerRed
)

@Composable
fun StockManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    customThemeColor: String = "#EA580C",
    content: @Composable () -> Unit
) {
    val parsedColor = try {
        Color(android.graphics.Color.parseColor(customThemeColor))
    } catch (e: Exception) {
        if (darkTheme) HardwareOrangeBright else HardwareOrange
    }

    val baseColor = if (customThemeColor.equals("#EA580C", ignoreCase = true) && darkTheme) {
        HardwareOrangeBright
    } else {
        parsedColor
    }

    val isLightPrimary = (baseColor.red * 0.299f + baseColor.green * 0.587f + baseColor.blue * 0.114f) > 0.68f
    val onPrimaryColor = if (isLightPrimary) Color(0xFF0F172A) else Color.White

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme.copy(
            primary = baseColor,
            onPrimary = onPrimaryColor,
            primaryContainer = baseColor.copy(alpha = 0.18f),
            onPrimaryContainer = baseColor
        )
        else -> LightColorScheme.copy(
            primary = baseColor,
            onPrimary = onPrimaryColor,
            primaryContainer = baseColor.copy(alpha = 0.12f),
            onPrimaryContainer = baseColor
        )
    }

    val extendedColors = if (darkTheme) {
        ExtendedColors(
            isDark = true,
            positive = SuccessGreenBright,
            warning = WarningAmber,
            danger = DangerRed,
            info = InfoBlueBright,
            purple = AccentPurple
        )
    } else {
        ExtendedColors(
            isDark = false,
            positive = SuccessGreen,
            warning = WarningAmber,
            danger = DangerRed,
            info = InfoBlue,
            purple = AccentPurple
        )
    }

    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

