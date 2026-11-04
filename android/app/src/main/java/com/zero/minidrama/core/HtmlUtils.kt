package com.zero.minidrama.core

import okhttp3.Request
import org.json.JSONObject

object HtmlUtils {
    private val nextDataRegex = Regex("""<script id="__NEXT_DATA__" type="application/json">([\s\S]*?)</script>""")

    fun fetchNextData(url: String, lang: String = "tr"): JSONObject {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent())
            .header("Accept-Language", if (lang == "en") "en-US,en;q=0.9" else "tr-TR,tr;q=0.9,en;q=0.8")
            .header("Referer", reelshortReferer(lang))
            .header("Origin", "https://www.reelshort.com")
            .build()
        HttpClient.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            val html = resp.body?.string() ?: throw IllegalStateException("Bos yanit")
            val match = nextDataRegex.find(html) ?: throw IllegalStateException("NEXT_DATA bulunamadi")
            return JSONObject(match.groupValues[1])
        }
    }

    fun langPrefix(lang: String) = if (lang == "en") "" else "/tr"

    fun reelshortReferer(lang: String): String =
        "https://www.reelshort.com${langPrefix(lang).ifBlank { "/" }}"

    fun createSlug(bookName: String): String = bookName
        .lowercase()
        .replace("ı", "i").replace("ğ", "g").replace("ü", "u")
        .replace("ş", "s").replace("ö", "o").replace("ç", "c")
        .replace(Regex("[^a-z0-9\\s-]"), "")
        .trim()
        .replace(Regex("\\s+"), "-")
        .replace(Regex("-+"), "-")

    private fun userAgent(): String {
        val models = listOf("Pixel 6", "Pixel 7", "Galaxy S21", "Redmi Note 11")
        val model = models.random()
        val major = (85..99).random()
        val build = (1000..9000).random()
        return "Mozilla/5.0 (Linux; Android 12; $model Build/SP1A.210812.015; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/$major.0.$build.120 Mobile Safari/537.36"
    }
}
