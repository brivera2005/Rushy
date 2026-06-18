package com.streamvault.player.ui

import android.graphics.Color
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.streamvault.player.PlayerSubtitleStyle

class SubtitleStyleController {
    private var style: PlayerSubtitleStyle = PlayerSubtitleStyle()

    fun updateStyle(style: PlayerSubtitleStyle) {
        this.style = style.copy(
            textScale = style.textScale.coerceIn(0.75f, 1.75f),
            bottomPaddingFraction = style.bottomPaddingFraction.coerceIn(0f, 0.25f)
        )
    }

    fun apply(playerView: PlayerView?) {
        val subtitleView = playerView?.subtitleView ?: return
        subtitleView.setApplyEmbeddedStyles(style.useEmbeddedStyles)
        subtitleView.setApplyEmbeddedFontSizes(style.useEmbeddedStyles)
        subtitleView.setStyle(
            CaptionStyleCompat(
                style.foregroundColorArgb,
                style.backgroundColorArgb,
                Color.TRANSPARENT,
                style.edgeType,
                Color.BLACK,
                null
            )
        )
        subtitleView.setFractionalTextSize(
            SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * style.textScale
        )
        subtitleView.setBottomPaddingFraction(style.bottomPaddingFraction)
    }
}

