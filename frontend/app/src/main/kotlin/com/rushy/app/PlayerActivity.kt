package com.rushy.app

import android.os.Bundle
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.concurrent.TimeUnit

class PlayerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val streamUrl = intent.getStringExtra(EXTRA_STREAM_URL).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val isLive = intent.getBooleanExtra(EXTRA_IS_LIVE, false)

        setContent {
            val context = LocalContext.current
            var showControls by remember { mutableStateOf(true) }
            var isPlaying by remember { mutableStateOf(true) }
            var positionMs by remember { mutableLongStateOf(0L) }
            var durationMs by remember { mutableLongStateOf(0L) }
            var isLiveStream by remember { mutableStateOf(isLive) }

            val player = remember {
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(ExoMediaItem.fromUri(streamUrl))
                    prepare()
                    playWhenReady = true
                }
            }

            DisposableEffect(player) {
                val listener = object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) {
                            durationMs = player.duration.coerceAtLeast(0L)
                            isLiveStream = isLive || !player.isCurrentMediaItemSeekable
                        }
                    }
                }
                player.addListener(listener)
                onDispose {
                    player.removeListener(listener)
                    player.release()
                }
            }

            LaunchedEffect(showControls) {
                if (showControls) {
                    delay(CONTROLS_TIMEOUT_MS)
                    showControls = false
                }
            }

            LaunchedEffect(Unit) {
                while (true) {
                    positionMs = player.currentPosition.coerceAtLeast(0L)
                    if (!isLiveStream) {
                        durationMs = player.duration.coerceAtLeast(0L)
                    }
                    delay(500)
                }
            }

            BackHandler { finish() }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionCenter, Key.Enter, Key.MediaPlayPause -> {
                                showControls = !showControls
                                true
                            }
                            else -> false
                        }
                    },
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            this.player = player
                            useController = false
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x88000000)),
                    ) {
                        if (title.isNotBlank()) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                color = ThemeColors.TextPrimary,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(24.dp),
                            )
                        }

                        if (isLiveStream) {
                            Text(
                                text = "● LIVE",
                                style = MaterialTheme.typography.labelLarge,
                                color = ThemeColors.LiveIndicator,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(24.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xCC1A1A2E))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }

                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(Color(0xCC1A1A2E))
                                .padding(horizontal = 32.dp, vertical = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (!isLiveStream && durationMs > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = formatTime(positionMs),
                                        color = ThemeColors.TextSecondary,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                    Text(
                                        text = formatTime(durationMs),
                                        color = ThemeColors.TextSecondary,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(ThemeColors.SurfaceDark)
                                        .padding(vertical = 2.dp),
                                ) {
                                    val fraction = (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(fraction)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(ThemeColors.AccentPrimary)
                                            .padding(vertical = 3.dp),
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Button(
                                    onClick = {
                                        if (!isLiveStream) {
                                            player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0))
                                        }
                                        showControls = true
                                    },
                                ) {
                                    Text("⏪ 10s", modifier = Modifier.padding(horizontal = 12.dp))
                                }

                                Button(
                                    onClick = {
                                        if (isPlaying) player.pause() else player.play()
                                        showControls = true
                                    },
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                ) {
                                    Text(
                                        text = if (isPlaying) "⏸ Pause" else "▶ Play",
                                        modifier = Modifier.padding(horizontal = 20.dp),
                                    )
                                }

                                Button(
                                    onClick = {
                                        if (!isLiveStream && durationMs > 0) {
                                            player.seekTo(
                                                (player.currentPosition + 30_000).coerceAtMost(durationMs),
                                            )
                                        }
                                        showControls = true
                                    },
                                ) {
                                    Text("30s ⏩", modifier = Modifier.padding(horizontal = 12.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        const val EXTRA_STREAM_URL = "stream_url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_IS_LIVE = "is_live"
        private const val CONTROLS_TIMEOUT_MS = 5000L

        private fun formatTime(ms: Long): String {
            val totalSec = TimeUnit.MILLISECONDS.toSeconds(ms)
            val hours = totalSec / 3600
            val minutes = (totalSec % 3600) / 60
            val seconds = totalSec % 60
            return if (hours > 0) {
                String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.US, "%d:%02d", minutes, seconds)
            }
        }
    }
}
