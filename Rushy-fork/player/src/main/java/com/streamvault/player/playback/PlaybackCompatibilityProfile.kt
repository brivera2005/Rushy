package com.streamvault.player.playback

import android.os.Build
import java.util.Locale

interface PlaybackCompatibilityProfile {
    fun shouldDisableDecoderReuseWorkaround(): Boolean
}

object DefaultPlaybackCompatibilityProfile : PlaybackCompatibilityProfile {
    override fun shouldDisableDecoderReuseWorkaround(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase(Locale.ROOT)
        val hardware = Build.HARDWARE.lowercase(Locale.ROOT)
        val model = Build.MODEL.lowercase(Locale.ROOT)
        return fingerprint.contains("generic") ||
            fingerprint.contains("emulator") ||
            hardware.contains("goldfish") ||
            hardware.contains("ranchu") ||
            model.contains("android sdk built for")
    }
}

