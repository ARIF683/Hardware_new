package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Primary Brand Hardware Orange
val HardwareOrange = Color(0xFFEA580C)
val HardwareOrangeBright = Color(0xFFF97316)
val HardwareOrangeDark = Color(0xFFC2410C)
val HardwareOrangeSoft = Color(0xFFFFF7ED)

// Semantic Status & Category Accents
val SuccessGreen = Color(0xFF16A34A)
val SuccessGreenBright = Color(0xFF10B981)
val SuccessGreenLight = Color(0xFFDCFCE7)
val DangerRed = Color(0xFFEF4444)
val DangerRedLight = Color(0xFFFEE2E2)
val WarningAmber = Color(0xFFF59E0B)
val WarningAmberLight = Color(0xFFFEF3C7)
val BrandAmber = Color(0xFFF59E0B)
val InfoBlue = Color(0xFF0EA5E9)
val InfoBlueBright = Color(0xFF38BDF8)
val AccentPurple = Color(0xFFA855F7)

// Backward-compatible dynamic references mapped to MaterialTheme
val BrandBlue: Color
    @Composable
    get() = MaterialTheme.colorScheme.primary

val BrandBlueDark: Color
    @Composable
    get() = MaterialTheme.colorScheme.primaryContainer

val BrandBlueLight: Color
    @Composable
    get() = MaterialTheme.colorScheme.primary

val BrandBlueSoft: Color
    @Composable
    get() = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)

// Dark Mode Surface Palette (matched to dark mockup)
val DarkBg = Color(0xFF0E1014)
val DarkSurface = Color(0xFF16191F)
val DarkCard = Color(0xFF1E222B)
val DarkBorder = Color(0xFF282D38)
val DarkText = Color(0xFFF8FAFC)
val DarkMuted = Color(0xFF94A3B8)

// Light Mode Surface Palette (matched to light mockup)
val LightBg = Color(0xFFF6F7FA)
val LightSurface = Color(0xFFFFFFFF)
val LightCard = Color(0xFFF1F5F9)
val LightBorder = Color(0xFFE2E8F0)
val LightText = Color(0xFF0F172A)
val LightMuted = Color(0xFF64748B)

// Legacy aliases mapped to theme-safe values (kept for non-breaking references during transition)
val StaticBrandBlue = HardwareOrange
val StaticBrandBlueDark = HardwareOrangeDark
val StaticBrandBlueLight = HardwareOrangeBright
val StaticBrandBlueSoft = HardwareOrangeSoft
val TacticalGreen = HardwareOrangeBright
val TacticalGreenDark = HardwareOrangeDark
val TacticalDarkBg = DarkBg
val TacticalSurface = DarkSurface
val TacticalCard = DarkCard
val TacticalBorder = DarkBorder

// Warm Cream Opening Splash Palette
val WarmCreamSplashBg = Color(0xFFFAF7F2)
val WarmCreamSplashSurface = Color(0xFFFFFFFF)
val WarmSandSplashBorder = Color(0xFFE8E0D5)
val WarmTerracottaAccent = Color(0xFFD97757)
val WarmEspressoText = Color(0xFF292524)
val WarmTaupeSubtext = Color(0xFF78716C)
