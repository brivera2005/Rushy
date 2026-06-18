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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
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
    Column(
        modifier = modifier
            .width(ThemeColors.SidebarWidth)
            .fillMaxHeight()
            .background(ThemeColors.SidebarBackground)
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            RushyLogo(size = RushyLogoSize.Header, showTagline = false)
        }

        AppScreen.entries.forEach { screen ->
            val selected = screen == current
            val badge = screen == AppScreen.SETTINGS && settingsHasUpdate
            var focused by remember { mutableStateOf(false) }
            val shape = RoundedCornerShape(ThemeColors.CornerRadius)

            Button(
                onClick = { onNavigate(screen) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 8.dp)
                    .onFocusChanged { focused = it.isFocused },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .then(
                            when {
                                selected -> Modifier.background(ThemeColors.FocusBackground)
                                focused -> Modifier
                                    .background(ThemeColors.SurfaceElevated)
                                    .tvFocusHighlight(shape = shape, focused = true)
                                else -> Modifier.background(ThemeColors.SidebarBackground)
                            },
                        )
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = screen.icon,
                        color = if (selected) ThemeColors.FocusText else ThemeColors.TextPrimary,
                    )
                    Text(
                        text = if (badge) "${screen.label} •" else screen.label,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        ),
                        color = when {
                            selected -> ThemeColors.FocusText
                            badge -> ThemeColors.Success
                            focused -> ThemeColors.TextPrimary
                            else -> ThemeColors.TextSecondary
                        },
                    )
                }
            }
        }
    }
}
