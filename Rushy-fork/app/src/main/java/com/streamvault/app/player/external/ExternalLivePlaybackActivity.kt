package com.streamvault.app.player.external

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.streamvault.app.navigation.PlayerNavigationRequest
import com.streamvault.domain.repository.ChannelRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Resolves a live channel stream URL and launches it in an external player.
 * Never falls back to the in-app ExoPlayer — opens Play Store for VLC when needed.
 */
@AndroidEntryPoint
class ExternalLivePlaybackActivity : ComponentActivity() {
    @Inject
    lateinit var channelRepository: ChannelRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val playerRequest = readPlayerRequest()
        if (playerRequest == null) {
            finish()
            return
        }
        lifecycleScope.launch {
            launchExternal(playerRequest)
            finish()
        }
    }

    private suspend fun launchExternal(request: PlayerNavigationRequest) {
        val resolvedUrl = resolveStreamUrl(request)
        if (resolvedUrl.isNullOrBlank()) {
            Toast.makeText(
                applicationContext,
                "Could not resolve stream URL.",
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        val streamId = request.streamId.takeIf { it > 0L }
            ?: request.internalId.takeIf { it > 0L }
            ?: 0L
        when (
            ExternalPlayerRouter.playLiveChannel(
                context = this,
                url = resolvedUrl,
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

    private suspend fun resolveStreamUrl(request: PlayerNavigationRequest): String? {
        if (request.internalId > 0L) {
            val channel = channelRepository.getChannel(request.internalId) ?: return null
            return channelRepository.getStreamInfo(channel).getOrNull()?.url
        }
        return request.streamUrl.takeIf { ExternalPlayerLauncher.isExternalPlayerLaunchUrl(it) }
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
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
    }
}
