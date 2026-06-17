package com.rushy.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

object ThemeColors {
    val DarkBackground = Color(0xFF0A0A0A)
    val SurfaceDark = Color(0xFF121212)
    val TextPrimary = Color(0xFFFFFFFF)

    val CrimsonAccent = Color(0xFFD32F2F)
    val CobaltAccent = Color(0xFF1E88E5)
    val EmeraldAccent = Color(0xFF43A047)
}

@Stable
class RushyThemeState(initialAccent: Color = ThemeColors.CrimsonAccent) {
    var currentAccentColor by mutableStateOf(initialAccent)
}

val LocalRushyTheme = staticCompositionLocalOf { RushyThemeState() }
