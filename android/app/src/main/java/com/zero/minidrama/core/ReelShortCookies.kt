package com.zero.minidrama.core

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

object ReelShortCookies : CookieJar {
    private const val DOMAIN = "https://www.reelshort.com"
    private val store = ConcurrentHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (!url.host.contains("reelshort.com")) return
        val key = url.host
        val list = store.getOrPut(key) { mutableListOf() }
        for (c in cookies) {
            list.removeAll { it.name == c.name && it.path == c.path }
            list.add(c)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (!url.host.contains("reelshort.com")) return emptyList()
        return store[url.host].orEmpty()
    }

    fun syncFromWebView() {
        val manager = CookieManager.getInstance()
        val raw = manager.getCookie(DOMAIN) ?: return
        val list = mutableListOf<Cookie>()
        for (part in raw.split(";")) {
            val trimmed = part.trim()
            if (trimmed.isBlank() || !trimmed.contains("=")) continue
            val eq = trimmed.indexOf('=')
            val name = trimmed.substring(0, eq).trim()
            val value = trimmed.substring(eq + 1).trim()
            if (name.isBlank()) continue
            list.add(
                Cookie.Builder()
                    .name(name)
                    .value(value)
                    .domain("www.reelshort.com")
                    .path("/")
                    .build()
            )
        }
        if (list.isNotEmpty()) {
            store["www.reelshort.com"] = list
        }
    }

    fun flushToWebView() {
        CookieManager.getInstance().flush()
    }
}
