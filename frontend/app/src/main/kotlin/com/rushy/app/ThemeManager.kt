package com.rushy.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** High-contrast TV palette — black background, white text, clear focus. */
object ThemeColors {
    val DarkBackground = Color(0xFF000000)
    val SidebarBackground = Color(0xFF0A0A0A)
    val SurfaceDark = Color(0xFF141414)
    val SurfaceElevated = Color(0xFF1E1E1E)
    val SurfaceGlass = Color(0xE6141414)

    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFB3B3B3)
    val TextMuted = Color(0xFF707070)

    val AccentPrimary = Color(0xFFFFFFFF)
    val AccentSecondary = Color(0xFFE0E0E0)
    val AccentTeal = Color(0xFFFFFFFF)
    val AccentGlow = Color(0x33FFFFFF)

    val FocusBorder = Color(0xFFFFFFFF)
    val FocusBackground = Color(0xFFFFFFFF)
    val FocusText = Color(0xFF000000)

    val Success = Color(0xFF4ADE80)
    val Warning = Color(0xFFFACC15)
    val Error = Color(0xFFF87171)
    val LiveIndicator = Color(0xFFEF4444)

    val CrimsonAccent = Error
    val CobaltAccent = TextSecondary
    val EmeraldAccent = Success

    val CornerRadius = 4.dp
    val CardRadius = 4.dp
    val FocusRingWidth = 3.dp
    val GridSpacing = 6.dp
    val MinFocusTarget = 48.dp
    val SidebarWidth = 200.dp

    val LiveLogoWidth = 160.dp
    val LiveLogoHeight = 90.dp
    val PosterWidth = 120.dp
    val PosterHeight = 180.dp
}

@Stable
class RushyThemeState(initialAccent: Color = ThemeColors.AccentPrimary) {
    var currentAccentColor by mutableStateOf(initialAccent)
}

val LocalRushyTheme = staticCompositionLocalOf { RushyThemeState() }
