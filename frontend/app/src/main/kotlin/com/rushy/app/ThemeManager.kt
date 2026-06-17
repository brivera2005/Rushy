package com.rushy.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Plex-inspired TV palette — dark charcoal base, amber accent. */
object ThemeColors {
    val DarkBackground = Color(0xFF1A1A2E)
    val SidebarBackground = Color(0xFF12121F)
    val SurfaceDark = Color(0xFF282A2D)
    val SurfaceElevated = Color(0xFF32353A)
    val SurfaceGlass = Color(0xCC282A2D)

    val TextPrimary = Color(0xFFEAEAEA)
    val TextSecondary = Color(0xFFADADAD)
    val TextMuted = Color(0xFF6E6E6E)

    val AccentPrimary = Color(0xFFE5A00D)
    val AccentSecondary = Color(0xFFCC7B19)
    val AccentTeal = Color(0xFF00D4AA)
    val AccentGlow = Color(0x66E5A00D)

    val Success = Color(0xFF34D399)
    val Warning = Color(0xFFFBBF24)
    val Error = Color(0xFFEF4444)
    val LiveIndicator = Color(0xFFE53935)

    val CrimsonAccent = Error
    val CobaltAccent = AccentTeal
    val EmeraldAccent = Success

    val CornerRadius = 6.dp
    val CardRadius = 8.dp
    val FocusRingWidth = 2.dp
    val GridSpacing = 8.dp
    val MinFocusTarget = 48.dp
    val SidebarWidth = 200.dp
}

@Stable
class RushyThemeState(initialAccent: Color = ThemeColors.AccentPrimary) {
    var currentAccentColor by mutableStateOf(initialAccent)
}

val LocalRushyTheme = staticCompositionLocalOf { RushyThemeState() }
