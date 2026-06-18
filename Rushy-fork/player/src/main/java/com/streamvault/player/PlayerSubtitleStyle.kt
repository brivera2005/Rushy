package com.streamvault.player

import androidx.media3.ui.CaptionStyleCompat

data class PlayerSubtitleStyle(
    val textScale: Float = 1f,
    val foregroundColorArgb: Int = 0xFFFFFFFF.toInt(),
    val backgroundColorArgb: Int = 0x80000000.toInt(),
    val edgeType: Int = CaptionStyleCompat.EDGE_TYPE_OUTLINE,
    val bottomPaddingFraction: Float = 0.08f,
    val useEmbeddedStyles: Boolean = true
)
