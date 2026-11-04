package com.zero.minidrama.core

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import java.io.File

@UnstableApi
object VideoPlayback {
    private const val UA =
        "Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    fun httpHeaders(platform: String): Map<String, String> {
        val base = mapOf("User-Agent" to UA, "Accept" to "*/*")
        return when (platform) {
            "reelshort" -> base + mapOf(
                "Referer" to "https://www.reelshort.com/",
                "Origin" to "https://www.reelshort.com"
            )
            "dramabox" -> base + mapOf(
                "Referer" to "https://www.dramabox.com/",
                "Origin" to "https://www.dramabox.com"
            )
            "netshort" -> base + mapOf(
                "Referer" to "https://www.netshort.com/",
                "Origin" to "https://www.netshort.com"
            )
            "dramawave" -> base + mapOf(
                "Referer" to "https://www.mydramawave.com/",
                "Origin" to "https://www.mydramawave.com"
            )
            else -> base
        }
    }

    fun createPlayer(context: Context, platform: String): ExoPlayer {
        val headers = httpHeaders(platform)
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(headers["User-Agent"] ?: UA)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(120_000)
            .setDefaultRequestProperties(headers)
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply { playWhenReady = true }
    }

    fun mediaItemForUrl(playUrl: String): MediaItem {
        val uri = normalizeUri(playUrl)
        val builder = MediaItem.Builder().setUri(uri)
        val path = playUrl.lowercase()
        if (path.contains(".m3u8") || path.startsWith("file:")) {
            builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        } else if (path.contains(".mp4")) {
            builder.setMimeType(MimeTypes.VIDEO_MP4)
        }
        return builder.build()
    }

    private fun normalizeUri(playUrl: String): Uri {
        if (playUrl.startsWith("file://")) {
            val path = Uri.parse(playUrl).path
            if (!path.isNullOrBlank()) {
                return Uri.fromFile(File(path))
            }
        }
        return Uri.parse(playUrl)
    }
}
