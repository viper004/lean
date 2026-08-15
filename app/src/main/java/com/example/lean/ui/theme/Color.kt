package com.example.lean.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

// Dark Palette (Preserved existing LEAN identity)
val DarkBackground = Color(0xFF0F1116)
val DarkSurface = Color(0xFF1B1F2A)
val DarkSurfaceVariant = Color(0xFF272C3D)
val DarkCardBorder = Color(0xFF2D3446)
val DarkTextPrimary = Color(0xFFEEF2F6)
val DarkTextSecondary = Color(0xFF90A4AE)
val DarkTextMuted = Color(0xFF607D8B)

// Light Palette (Optimized for Outdoor Sunlight Readability)
val LightBackground = Color(0xFFFFFFFF)
val LightSurface = Color(0xFFF4F6F8)
val LightSurfaceVariant = Color(0xFFE5E7EB)
val LightCardBorder = Color(0xFFD1D5DB)
val LightTextPrimary = Color(0xFF111111) // High contrast near-black
val LightTextSecondary = Color(0xFF333333) // Deep dark gray
val LightTextMuted = Color(0xFF555555) // Dark legible gray

// Brand & Metric Accents
val PrimaryCyan = Color(0xFF00E5FF)
val LightPrimaryCyan = Color(0xFF007799)

val PrimaryLime = Color(0xFF00E676)
val LightPrimaryLime = Color(0xFF008037)

val AccentOrange = Color(0xFFFF7043)
val LightAccentOrange = Color(0xFFD9381E)

val WarningAmber = Color(0xFFFFC107)
val LightWarningAmber = Color(0xFFD97706)

val ErrorRed = Color(0xFFFF5252)
val LightErrorRed = Color(0xFFC62828)

// ColorScheme Extension Properties for High Sunlight Contrast
val ColorScheme.isDark: Boolean
    get() = this.background == DarkBackground

val ColorScheme.textMuted: Color
    get() = if (isDark) DarkTextMuted else LightTextMuted

val ColorScheme.primaryCyan: Color
    get() = if (isDark) PrimaryCyan else LightPrimaryCyan

val ColorScheme.primaryLime: Color
    get() = if (isDark) PrimaryLime else LightPrimaryLime

val ColorScheme.accentOrange: Color
    get() = if (isDark) AccentOrange else LightAccentOrange

val ColorScheme.warningAmber: Color
    get() = if (isDark) WarningAmber else LightWarningAmber

val ColorScheme.errorRed: Color
    get() = if (isDark) ErrorRed else LightErrorRed
