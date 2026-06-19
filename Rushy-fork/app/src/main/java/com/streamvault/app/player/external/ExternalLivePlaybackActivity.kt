package com.streamvault.app.player.external

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.streamvault.app.navigation.PlayerNavigationRequest

/**
 * Launches a resolved live channel URL in TiviMate/VLC.
 * Stream resolution happens before this activity starts so Hilt is not required here.
 */
class ExternalLivePlaybackActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val playerRequest = readPlayerRequest()
        if (playerRequest == null) {
            finish()
            return
        }
        launchExternal(playerRequest)
        finish()
    }

    private fun launchExternal(request: PlayerNavigationRequest) {
        val playbackUrl = request.streamUrl.trim()
        val streamId = request.streamId.takeIf { it > 0L }
            ?: request.internalId.takeIf { it > 0L }
            ?: 0L
        if (playbackUrl.isBlank() && streamId <= 0L) {
            Toast.makeText(
                applicationContext,
                "Could not resolve stream URL.",
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        when (
            ExternalPlayerRouter.playLiveChannel(
                context = this,
                url = playbackUrl,
                title = request.title,
                streamId = streamId,
            )
        ) {
            is ExternalPlayerRouter.PlayResult.Success -> Unit
            ExternalPlayerRouter.PlayResult.OpenedPlayStore -> {
                Toast.makeText(
                    applicationContext,
                    "Install VLC from the Play Store, then try the channel again.",
                    Toast.LENGTH_LONG,
                ).show()
            }
            ExternalPlayerRouter.PlayResult.AllFailed -> {
                Toast.makeText(
                    applicationContext,
                    "Could not open an external player for this channel.",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun readPlayerRequest(): PlayerNavigationRequest? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(EXTRA_PLAYER_REQUEST, PlayerNavigationRequest::class.java)
        } else {
            intent.getSerializableExtra(EXTRA_PLAYER_REQUEST) as? PlayerNavigationRequest
        }
    }

    companion object {
        private const val EXTRA_PLAYER_REQUEST = "external_live_player_request"

        fun createIntent(context: Context, request: PlayerNavigationRequest): Intent =
            Intent(context, ExternalLivePlaybackActivity::class.java).apply {
                putExtra(EXTRA_PLAYER_REQUEST, request)
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
    }
}
