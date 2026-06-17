package com.rushy.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

enum class RushyLogoSize(
    val markSize: Dp,
    val wordmarkSp: Int,
    val taglineSp: Int,
    val spacing: Dp,
    val underlineHeight: Dp,
) {
    Splash(markSize = 56.dp, wordmarkSp = 56, taglineSp = 18, spacing = 16.dp, underlineHeight = 4.dp),
    Large(markSize = 40.dp, wordmarkSp = 42, taglineSp = 16, spacing = 12.dp, underlineHeight = 3.dp),
    Header(markSize = 28.dp, wordmarkSp = 32, taglineSp = 14, spacing = 8.dp, underlineHeight = 2.dp),
}

@Composable
fun RushyLogo(
    modifier: Modifier = Modifier,
    size: RushyLogoSize = RushyLogoSize.Header,
    showTagline: Boolean = false,
    accentColor: Color = LocalRushyTheme.current.currentAccentColor,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(size.spacing / 2),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(size.spacing),
        ) {
            PlayMark(
                modifier = Modifier.size(size.markSize),
                color = accentColor,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "RUSHY",
                    fontSize = size.wordmarkSp.sp,
                    fontWeight = FontWeight.Bold,
                    color = ThemeColors.TextPrimary,
                    letterSpacing = 4.sp,
                )
                Box(
                    modifier = Modifier
                        .width((size.wordmarkSp * 2.8).dp)
                        .height(size.underlineHeight)
                        .padding(top = 2.dp),
                ) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        drawRect(color = accentColor)
                    }
                }
            }
        }
        if (showTagline) {
            Text(
                text = stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.bodyMedium,
                fontSize = size.taglineSp.sp,
                color = ThemeColors.TextPrimary.copy(alpha = 0.65f),
                modifier = Modifier.padding(start = size.markSize + size.spacing),
            )
        }
    }
}

@Composable
private fun PlayMark(
    modifier: Modifier = Modifier,
    color: Color,
) {
    Canvas(modifier = modifier) {
        val triangle = Path().apply {
            moveTo(size.width * 0.18f, size.height * 0.12f)
            lineTo(size.width * 0.18f, size.height * 0.88f)
            lineTo(size.width * 0.88f, size.height * 0.5f)
            close()
        }
        drawPath(triangle, color)
        drawLine(
            color = color.copy(alpha = 0.5f),
            start = Offset(size.width * 0.1f, size.height * 0.95f),
            end = Offset(size.width * 0.95f, size.height * 0.95f),
            strokeWidth = size.height * 0.04f,
        )
    }
}

@Composable
fun RushyLoadingView(
    modifier: Modifier = Modifier,
    message: String = "Loading...",
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            RushyLogo(
                size = RushyLogoSize.Splash,
                showTagline = true,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = ThemeColors.CobaltAccent,
            )
        }
    }
}
