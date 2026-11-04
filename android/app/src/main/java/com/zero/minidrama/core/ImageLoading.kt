package com.zero.minidrama.core

import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object ImageLoading {
    private const val UA =
        "Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private val headerInterceptor = Interceptor { chain ->
        val url = chain.request().url.toString()
        val builder = chain.request().newBuilder()
            .header("User-Agent", UA)
        if (needsMediaHeaders(url)) {
            builder.header("Referer", "https://www.reelshort.com/")
            builder.header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
        }
        chain.proceed(builder.build())
    }

    private fun needsMediaHeaders(url: String): Boolean =
        url.contains("crazymaplestudios.com", ignoreCase = true) ||
            url.contains("reelshort.com", ignoreCase = true) ||
            url.contains("dramabox", ignoreCase = true) ||
            url.contains("netshort", ignoreCase = true)

    fun createLoader(context: android.content.Context): ImageLoader {
        val okHttp = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(headerInterceptor)
            .build()
        return ImageLoader.Builder(context)
            .okHttpClient(okHttp)
            .crossfade(true)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.05)
                    .build()
            }
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.15)
                    .build()
            }
            .build()
    }

    fun normalizePosterUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""
        return when {
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("http://") -> "https://" + trimmed.removePrefix("http://")
            else -> trimmed
        }
    }
}
