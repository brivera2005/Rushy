package com.rushy.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object ThemeColors {
    // Premium cinematic base — deep charcoal/navy, not harsh red
    val DarkBackground = Color(0xFF0B0E14)
    val SurfaceDark = Color(0xFF141A24)
    val SurfaceElevated = Color(0xFF1C2433)
    val SurfaceGlass = Color(0xCC1C2433)

    val TextPrimary = Color(0xFFF4F6FA)
    val TextSecondary = Color(0xFFB0B8C8)
    val TextMuted = Color(0xFF6B7280)

    // Accent palette — electric teal + blue (streaming feel)
    val AccentPrimary = Color(0xFF00D4AA)
    val AccentSecondary = Color(0xFF3B82F6)
    val AccentGlow = Color(0x6600D4AA)

    // Semantic
    val Success = Color(0xFF34D399)
    val Warning = Color(0xFFFBBF24)
    val Error = Color(0xFFEF4444)
    val LiveIndicator = Color(0xFFEF4444)

    // Legacy aliases (gradual migration)
    val CrimsonAccent = Error
    val CobaltAccent = AccentSecondary
    val EmeraldAccent = Success

    // Layout
    val CornerRadius = 12.dp
    val FocusRingWidth = 3.dp
    val GridSpacing = 8.dp
    val MinFocusTarget = 48.dp
}

@Stable
class RushyThemeState(initialAccent: Color = ThemeColors.AccentPrimary) {
    var currentAccentColor by mutableStateOf(initialAccent)
}

val LocalRushyTheme = staticCompositionLocalOf { RushyThemeState() }
