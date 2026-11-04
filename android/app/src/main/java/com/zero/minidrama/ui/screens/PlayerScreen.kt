package com.zero.minidrama.ui.screens

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.zero.minidrama.core.VideoPlayback
import com.zero.minidrama.data.PlayPayload

@UnstableApi
@Composable
fun PlayerScreen(
    payload: PlayPayload?,
    playUrl: String?,
    isLoading: Boolean,
    loadError: String?,
    onBack: () -> Unit,
    onEnded: () -> Unit,
    onError: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val platform = payload?.platform.orEmpty()
    val player = remember(platform) {
        VideoPlayback.createPlayer(context, platform)
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) onEnded()
            }

            override fun onPlayerError(error: PlaybackException) {
                val msg = error.cause?.message ?: error.message ?: "Oynatma hatasi"
                onError(msg)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(playUrl) {
        if (!playUrl.isNullOrBlank()) {
            player.stop()
            player.clearMediaItems()
            player.setMediaItem(VideoPlayback.mediaItemForUrl(playUrl))
            player.prepare()
            player.play()
        }
    }

    val failedToLoad = !isLoading && playUrl.isNullOrBlank()

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (!playUrl.isNullOrBlank()) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        this.player = player
                        useController = true
                        keepScreenOn = true
                    }
                },
                update = { view -> view.player = player },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (isLoading) {
            CircularProgressIndicator(
                Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (failedToLoad) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    loadError ?: "Video acilamadi",
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Button(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) {
                    Text("Geri")
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xCC000000), Color.Transparent)
                    )
                )
                .padding(top = 8.dp, bottom = 24.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = Color.White)
            }
            payload?.let {
                Column(
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 56.dp, end = 16.dp)
                ) {
                    Text(
                        it.bookName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "Bolum ${it.serial}",
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}
