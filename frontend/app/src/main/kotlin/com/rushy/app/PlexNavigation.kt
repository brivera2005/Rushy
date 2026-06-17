package com.rushy.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

enum class AppScreen(val label: String, val icon: String) {
    HOME("Home", "⌂"),
    LIVE_TV("Live TV", "▶"),
    MOVIES("Movies", "🎬"),
    TV_SHOWS("TV Shows", "📺"),
    GUIDE("Guide", "☰"),
    SETTINGS("Settings", "⚙"),
}

@Composable
fun PlexSidebar(
    current: AppScreen,
    onNavigate: (AppScreen) -> Unit,
    settingsHasUpdate: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val accent = LocalRushyTheme.current.currentAccentColor

    Column(
        modifier = modifier
            .width(ThemeColors.SidebarWidth)
            .fillMaxHeight()
            .background(ThemeColors.SidebarBackground)
            .padding(vertical = 20.dp, horizontal = 0.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 16.dp),
        ) {
            RushyLogo(size = RushyLogoSize.Header, showTagline = false)
        }

        AppScreen.entries.forEach { screen ->
            val selected = screen == current
            val badge = screen == AppScreen.SETTINGS && settingsHasUpdate

            Button(
                onClick = { onNavigate(screen) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (selected) ThemeColors.SurfaceElevated else ThemeColors.SidebarBackground,
                        )
                        .padding(start = if (selected) 0.dp else 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(36.dp)
                                .background(accent),
                        )
                    }
                    Text(
                        text = screen.icon,
                        modifier = Modifier.padding(start = 16.dp, end = 10.dp),
                        color = if (selected) accent else ThemeColors.TextMuted,
                    )
                    Text(
                        text = if (badge) "${screen.label} •" else screen.label,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                        color = when {
                            selected -> ThemeColors.TextPrimary
                            badge -> ThemeColors.EmeraldAccent
                            else -> ThemeColors.TextSecondary
                        },
                    )
                }
            }
        }
    }
}
