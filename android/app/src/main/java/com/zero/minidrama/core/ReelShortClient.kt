package com.zero.minidrama.core

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder

class ReelShortClient {
    private val slugCache = mutableMapOf<String, String>()

    companion object {
        private const val TAG = "ReelShortClient"
        private const val UA =
            "Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        private val BROWSE_SEEDS = mapOf(
            "tr" to listOf("aşk", "intikam", "ceo", "aile"),
            "en" to listOf("love", "revenge", "ceo", "billionaire")
        )
    }

    data class CatalogPage(
        val books: List<JSONObject>,
        val totalPages: Int,
        val categories: List<Pair<String, String>> = emptyList()
    )

    fun search(q: String, page: Int, lang: String, categoryId: String = ""): CatalogPage {
        val prefix = HtmlUtils.langPrefix(lang)
        val keyword = q.trim().ifBlank {
            val seeds = BROWSE_SEEDS[lang] ?: BROWSE_SEEDS.getValue("en")
            seeds.first()
        }
        val url = "https://www.reelshort.com$prefix/search?keywords=${URLEncoder.encode(keyword, "UTF-8")}&page=$page"
        val data = HtmlUtils.fetchNextData(url, lang).getJSONObject("props").getJSONObject("pageProps")
        val books = data.optJSONArray("books") ?: org.json.JSONArray()
        val total = data.optInt("total", 0)
        val pageSize = data.optInt("pageSize", 10).coerceAtLeast(1)
        val totalPages = data.optInt("totalPage").takeIf { it > 0 }
            ?: ((total + pageSize - 1) / pageSize).coerceAtLeast(1)
        val list = buildList {
            for (i in 0 until books.length()) add(books.getJSONObject(i))
        }
        val seeds = BROWSE_SEEDS[lang] ?: BROWSE_SEEDS.getValue("en")
        val categories = listOf("" to if (lang == "tr") "Tümü" else "All") +
            seeds.map { it to it.replaceFirstChar { c -> c.uppercase() } }
        val filtered = if (categoryId.isBlank()) list else list.filter { b ->
            val title = b.optString("book_title", b.optString("book_name"))
            title.contains(categoryId, ignoreCase = true)
        }
        return CatalogPage(filtered, totalPages, categories)
    }

    fun browse(lang: String, page: Int, categoryId: String = ""): CatalogPage {
        val seeds = BROWSE_SEEDS[lang] ?: BROWSE_SEEDS.getValue("en")
        val keyword = categoryId.ifBlank { seeds.first() }
        return search(keyword, page, lang, categoryId)
    }

    suspend fun bookDetail(bookId: String, lang: String): JSONObject = withContext(Dispatchers.IO) {
        val prefix = HtmlUtils.langPrefix(lang)
        val firstUrl = "https://www.reelshort.com$prefix/full-episodes/$bookId/1"
        val data1 = HtmlUtils.fetchNextData(firstUrl, lang).getJSONObject("props").getJSONObject("pageProps").getJSONObject("data")
        val totalChapters = data1.optInt("chapter_count")
        val bookName = data1.optString("book_title")
        val slug = HtmlUtils.createSlug(bookName)
        slugCache["${bookId}_$lang"] = slug
        val totalPages = ((totalChapters + 9) / 10).coerceAtLeast(1)
        val jobs = (1..totalPages).map { p ->
            async {
                val pageUrl = "https://www.reelshort.com$prefix/full-episodes/$bookId/$p"
                runCatching {
                    HtmlUtils.fetchNextData(pageUrl, lang).getJSONObject("props").getJSONObject("pageProps")
                        .getJSONObject("data").optJSONArray("chapter_list") ?: org.json.JSONArray()
                }.getOrDefault(org.json.JSONArray())
            }
        }
        val chapters = jobs.awaitAll().flatMap { arr ->
            buildList {
                for (i in 0 until arr.length()) {
                    val ch = arr.getJSONObject(i)
                    val serial = chapterSerial(ch)
                    if (serial <= 0 || ch.optString("serial_number") == "Trailer") continue
                    val cid = ch.optString("chapter_id")
                    if (cid.isBlank()) continue
                    add(JSONObject().apply {
                        put("serial", serial)
                        put("chapter_id", cid)
                    })
                }
            }
        }.distinctBy { it.optInt("serial") }.sortedBy { it.optInt("serial") }

        JSONObject().apply {
            put("bookId", bookId)
            put("bookName", bookName)
            put("alias", bookName)
            put("description", data1.optString("special_desc", data1.optString("share_text")))
            put("readCount", data1.optLong("read_count"))
            put("collectCount", data1.optLong("collect_count"))
            put("tags", data1.optJSONArray("tag") ?: org.json.JSONArray())
            put("episodes", org.json.JSONArray(chapters))
        }
    }

    fun playUrl(context: Context, bookId: String, serial: Int, chapterId: String, lang: String): String {
        val resolvedChapterId = resolveChapterId(bookId, serial, chapterId, lang)
        if (resolvedChapterId.isBlank()) {
            throw IllegalStateException("Bolum ID bulunamadi (bolum $serial)")
        }

        var lastApiError = "bilinmiyor"
        val videoUrl = fetchChapterVideoUrl(bookId, resolvedChapterId, lang) { lastApiError = it }
            ?: fetchEpisodePageVideoUrl(bookId, serial, resolvedChapterId, lang)
            ?: throw IllegalStateException("Video URL bulunamadi ($lastApiError)")

        val manifestUrl = transformVideoUrl(videoUrl)
        if (manifestUrl.startsWith("http")) {
            return manifestUrl
        }
        if (manifestUrl.endsWith(".m3u8")) {
            val m3u8 = fetchAndRewriteManifest(manifestUrl)
            val file = File(context.cacheDir, "reelshort_${bookId}_${serial}.m3u8")
            file.writeText(m3u8)
            return Uri.fromFile(file).toString()
        }
        return manifestUrl
    }

    private fun chapterSerial(ch: JSONObject): Int {
        when (val raw = ch.opt("serial_number")) {
            is Number -> return raw.toInt()
            is String -> return raw.toIntOrNull() ?: 0
        }
        return ch.optInt("serial_number", 0)
    }

    private fun resolveChapterId(bookId: String, serial: Int, chapterId: String, lang: String): String {
        if (chapterId.isNotBlank()) return chapterId
        val prefix = HtmlUtils.langPrefix(lang)
        val page = ((serial - 1).coerceAtLeast(0) / 10) + 1
        val data = HtmlUtils.fetchNextData(
            "https://www.reelshort.com$prefix/full-episodes/$bookId/$page",
            lang
        ).getJSONObject("props").getJSONObject("pageProps").getJSONObject("data")
        val list = data.optJSONArray("chapter_list") ?: return ""
        for (i in 0 until list.length()) {
            val ch = list.getJSONObject(i)
            if (chapterSerial(ch) == serial) {
                return ch.optString("chapter_id")
            }
        }
        return data.optJSONObject("first_chapter")?.optString("chapter_id").orEmpty()
    }

    private fun fetchChapterVideoUrl(
        bookId: String,
        chapterId: String,
        lang: String,
        onError: (String) -> Unit
    ): String? {
        val langCode = if (lang == "en") "en" else "tr"
        val urls = listOf(
            "https://www.reelshort.com/api/video/book/getChapterInfo?book_id=$bookId&chapter_id=$chapterId&lang=$langCode",
            "https://www.reelshort.com/api/video/book/getChapterInfo?book_id=$bookId&chapter_id=$chapterId"
        )
        for (apiUrl in urls) {
            val req = Request.Builder()
                .url(apiUrl)
                .get()
                .header("User-Agent", UA)
                .header("Referer", HtmlUtils.reelshortReferer(lang))
                .header("Origin", "https://www.reelshort.com")
                .header("Accept", "application/json")
                .header("lang", langCode)
                .build()
            val result = runCatching {
                HttpClient.client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        onError("HTTP ${resp.code}")
                        return@use null
                    }
                    val json = JSONObject(body)
                    val code = json.optInt("code", -1)
                    if (code != 0) {
                        onError(json.optString("msg", "code $code"))
                        return@use null
                    }
                    json.optJSONObject("data")?.optString("video_url")?.takeIf { it.isNotBlank() }
                }
            }.getOrNull()
            if (!result.isNullOrBlank()) {
                Log.i(TAG, "getChapterInfo OK b=$bookId c=$chapterId")
                return result
            }
        }
        return null
    }

    private fun fetchEpisodePageVideoUrl(bookId: String, serial: Int, chapterId: String, lang: String): String? {
        val prefix = HtmlUtils.langPrefix(lang)
        val slug = slugCache["${bookId}_$lang"] ?: run {
            val firstUrl = "https://www.reelshort.com$prefix/full-episodes/$bookId/1"
            val bookName = HtmlUtils.fetchNextData(firstUrl, lang).getJSONObject("props").getJSONObject("pageProps")
                .getJSONObject("data").optString("book_title")
            HtmlUtils.createSlug(bookName).also { slugCache["${bookId}_$lang"] = it }
        }
        val episodeUrl =
            "https://www.reelshort.com$prefix/episodes/episode-$serial-$slug-$bookId-$chapterId"
        val epData = runCatching {
            HtmlUtils.fetchNextData(episodeUrl, lang).getJSONObject("props").getJSONObject("pageProps")
                .optJSONObject("data")
        }.getOrNull() ?: return null
        return epData.optString("video_url").takeIf { it.isNotBlank() }
            ?: epData.optString("play_url").takeIf { it.isNotBlank() }
    }

    private fun transformVideoUrl(videoUrl: String): String {
        if (videoUrl.contains("/vtt-m3u8/") && videoUrl.contains("/h264/")) {
            val idx = videoUrl.indexOf("/h264/")
            return videoUrl.substring(0, idx + 5) + "/video/1080p/1080p.m3u8"
        }
        return videoUrl
    }

    private fun fetchAndRewriteManifest(videoUrl: String): String {
        val req = Request.Builder()
            .url(videoUrl)
            .header("User-Agent", UA)
            .header("Referer", "https://www.reelshort.com/")
            .header("Origin", "https://www.reelshort.com")
            .build()
        val text = HttpClient.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Manifest HTTP ${resp.code}")
            resp.body?.string() ?: throw IllegalStateException("Bos manifest")
        }
        val lines = text.lines()
        val dir = videoUrl.substring(0, videoUrl.lastIndexOf('/') + 1)
        val baseName = videoUrl.substring(videoUrl.lastIndexOf('/') + 1).removeSuffix(".m3u8")
        var targetDuration = 6
        val body = StringBuilder()
        for (line in lines) {
            when {
                line.startsWith("#EXTINF:") -> body.append(line).append('\n')
                line.startsWith("#EXT-X-TARGETDURATION:") -> {
                    targetDuration = line.substringAfter(":").trim().toIntOrNull() ?: 6
                }
                !line.startsWith("#") && line.isNotBlank() -> {
                    val segName = line.trim().substringAfterLast('/')
                    when {
                        segName.endsWith(".ts") -> {
                            val num = Regex("(\\d+)\\.ts$").find(segName)?.groupValues?.get(1)
                            body.append(if (num != null) "$dir$baseName-$num.ts" else resolveUrl(line.trim(), dir)).append('\n')
                        }
                        segName.endsWith(".m4s") || segName.endsWith(".mp4") -> {
                            body.append(if (line.startsWith("http")) line.trim() else dir + line.trim()).append('\n')
                        }
                    }
                }
            }
        }
        if (body.isEmpty()) throw IllegalStateException("Manifest segmentleri bos")
        return buildString {
            append("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-ALLOW-CACHE:YES\n")
            append("#EXT-X-TARGETDURATION:$targetDuration\n#EXT-X-MEDIA-SEQUENCE:0\n")
            append(body)
            append("#EXT-X-ENDLIST\n")
        }
    }

    private fun resolveUrl(line: String, dir: String): String =
        if (line.startsWith("http")) line else dir + line
}
