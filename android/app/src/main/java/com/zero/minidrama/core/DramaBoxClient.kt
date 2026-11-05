package com.zero.minidrama.core

import android.content.Context
import android.net.Uri
import android.util.Log
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder

class DramaBoxClient {
    private val chapterFromUrl = Regex("/(\\d+)_Episode-\\d+")
    private val mobileClients = mutableMapOf<Context, DramaBoxMobileClient>()

    private fun mobileClient(context: Context) =
        mobileClients.getOrPut(context.applicationContext) { DramaBoxMobileClient(context.applicationContext) }

    fun search(q: String, page: Int, lang: String, categoryId: String = "0"): Pair<List<JSONObject>, Int> {
        val prefix = HtmlUtils.langPrefix(lang)
        val url = if (q.isBlank()) {
            val typeId = categoryId.ifBlank { "0" }
            "https://www.dramabox.com$prefix/browse/$typeId/$page"
        } else {
            "https://www.dramabox.com$prefix/search/$page?searchValue=${URLEncoder.encode(q, "UTF-8")}"
        }
        val data = fetchPageProps(url, lang)
        val books = data.optJSONArray("bookList") ?: org.json.JSONArray()
        val totalPages = data.optInt("pages", data.optInt("totalPage", 1)).coerceAtLeast(1)
        val list = buildList {
            for (i in 0 until books.length()) add(books.getJSONObject(i))
        }
        return list to totalPages
    }

    fun categories(lang: String): List<Pair<String, String>> {
        val prefix = HtmlUtils.langPrefix(lang)
        val url = "https://www.dramabox.com$prefix/browse/0/1"
        val data = fetchPageProps(url, lang)
        val types = data.optJSONArray("types") ?: return emptyList()
        return buildList {
            for (i in 0 until types.length()) {
                val t = types.getJSONObject(i)
                val id = t.opt("id")?.toString() ?: continue
                val name = if (id == "0") "Tümü" else t.optString("name", id)
                add(id to name)
            }
        }
    }

    fun resolveFirstChapterId(bookId: String, lang: String): String {
        val prefix = HtmlUtils.langPrefix(lang)
        val url = "https://www.dramabox.com$prefix/video/$bookId"
        val finalUrl = fetchFinalUrl(url, lang)
        parseChapterIdFromVideoUrl(finalUrl)?.let { return it }
        val pageProps = fetchPageProps(url, lang)
        val list = pageProps.optJSONArray("chapterList")
        if (list != null && list.length() > 0) {
            val id = list.getJSONObject(0).optString("id")
            if (id.isNotBlank()) return id
        }
        throw IllegalStateException("DramaBox ilk bolum bulunamadi")
    }

    fun bookDetail(bookId: String, firstEpId: String, lang: String): JSONObject {
        val prefix = HtmlUtils.langPrefix(lang)
        val url = "https://www.dramabox.com$prefix/video/$bookId/$firstEpId"
        val pageProps = fetchPageProps(url, lang)
        val bookInfo = pageProps.optJSONObject("bookInfo") ?: JSONObject()
        val chapterList = pageProps.optJSONArray("chapterList") ?: org.json.JSONArray()
        val episodes = buildList {
            for (i in 0 until chapterList.length()) {
                val ch = chapterList.getJSONObject(i)
                if (ch.has("index") && !ch.isNull("index")) {
                    add(JSONObject().apply {
                        put("serial", ch.optInt("index") + 1)
                        put("chapter_id", ch.optString("id"))
                        put("cover", ch.optString("cover"))
                    })
                }
            }
        }.sortedBy { it.optInt("serial") }
        return JSONObject().apply {
            put("bookId", bookId)
            put("bookName", bookInfo.optString("bookName"))
            put("alias", bookInfo.optString("bookNameEn"))
            put("description", bookInfo.optString("introduction"))
            put("readCount", bookInfo.optLong("readCount"))
            put("collectCount", bookInfo.optLong("collectCount"))
            put("tags", bookInfo.optJSONArray("tags") ?: org.json.JSONArray())
            put("episodes", org.json.JSONArray(episodes))
        }
    }

    fun generateM3u8(cover: String): String {
        val baseUrl = getBaseUrlFromCover(cover) ?: throw IllegalStateException("Cover gecersiz")
        val total = findTotalSegments(baseUrl)
        if (total == 0) throw IllegalStateException("Segment bulunamadi")
        val sb = StringBuilder()
        sb.append("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-ALLOW-CACHE:YES\n#EXT-X-TARGETDURATION:8\n#EXT-X-MEDIA-SEQUENCE:0\n")
        for (i in 1..total) {
            sb.append("#EXTINF:6.000000,\n${baseUrl}${i.toString().padStart(5, '0')}.ts\n")
        }
        sb.append("#EXT-X-ENDLIST\n")
        return sb.toString()
    }

    /** Fresh signed MP4 from the episode page (preferred over cover-derived HLS). */
    fun playUrl(context: Context, bookId: String, chapterId: String, lang: String): String {
        val chapter = loadChapterEntry(bookId, chapterId, lang)
        chapter.optString("mp4").takeIf { it.isNotBlank() }?.let { return it }

        val episodeIndex = (chapter.optInt("index", -1) + 1).coerceAtLeast(1)
        val locked = !chapter.optBoolean("unlock", true)
        val mobileUrl = runCatching {
            mobileClient(context).playUrl(bookId, chapterId, episodeIndex, lang)
        }.onFailure { e ->
            Log.w(TAG, "DramaBox mobile: ${e.message}")
        }.getOrNull()
        if (!mobileUrl.isNullOrBlank()) return mobileUrl
        if (locked) {
            val detail = DramaBoxMobileClient.lastDiagnostic.ifBlank { "video URL alinamadi" }
            throw IllegalStateException("DramaBox bolumu kilitli ($detail)")
        }

        val cover = chapter.optString("cover")
        if (cover.isBlank()) throw IllegalStateException("DramaBox video bilgisi yok")
        if (chapter.optBoolean("m3u8Flag")) {
            val m3u8 = generateM3u8(cover)
            val file = File(context.cacheDir, "dramabox_${bookId}_${chapterId}.m3u8")
            file.writeText(m3u8)
            return Uri.fromFile(file).toString()
        }
        throw IllegalStateException("DramaBox video URL bulunamadi (mp4 bos)")
    }

    companion object {
        private const val TAG = "DramaBoxClient"
    }

    private fun loadChapterEntry(bookId: String, chapterId: String, lang: String): JSONObject {
        val prefix = HtmlUtils.langPrefix(lang)
        val url = "https://www.dramabox.com$prefix/video/$bookId/$chapterId"
        val pageProps = fetchPageProps(url, lang)
        val list = pageProps.optJSONArray("chapterList")
            ?: throw IllegalStateException("DramaBox bolum listesi bos")
        for (i in 0 until list.length()) {
            val ch = list.getJSONObject(i)
            if (ch.optString("id") == chapterId) return ch
        }
        throw IllegalStateException("DramaBox bolum bulunamadi")
    }

    private fun fetchPageProps(url: String, lang: String): JSONObject {
        val html = fetchHtml(url, lang)
        return HtmlUtils.parseNextDataJson(html).getJSONObject("props").getJSONObject("pageProps")
    }

    private fun fetchFinalUrl(url: String, lang: String): String {
        val req = dramaBoxRequest(url, lang).build()
        HttpClient.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("DramaBox HTTP ${resp.code}")
            }
            return resp.request.url.toString()
        }
    }

    private fun fetchHtml(url: String, lang: String): String {
        val req = dramaBoxRequest(url, lang).build()
        HttpClient.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("DramaBox HTTP ${resp.code}")
            }
            return resp.body?.string() ?: throw IllegalStateException("DramaBox bos yanit")
        }
    }

    private fun dramaBoxRequest(url: String, lang: String): Request.Builder {
        val prefix = HtmlUtils.langPrefix(lang)
        return Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", dramaBoxUserAgent())
            .header(
                "Accept-Language",
                if (lang == "en") "en-US,en;q=0.9" else "tr-TR,tr;q=0.9,en;q=0.8"
            )
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Referer", "https://www.dramabox.com$prefix/")
    }

    private fun getBaseUrlFromCover(coverUrl: String): String? {
        var url = coverUrl
            .replace("thwztvideo.dramaboxdb.com", "hwzthls.dramaboxdb.com")
            .replace("thwztchapter.dramaboxdb.com", "hwzthls.dramaboxdb.com")
        url = url.split("@")[0]
        val lastSlash = url.lastIndexOf('/')
        if (lastSlash < 0) return null
        val path = url.substring(0, lastSlash)
        val filename = url.substring(lastSlash + 1)
        val videoId = when {
            filename.endsWith(".mp4.jpg") -> filename.removeSuffix(".mp4.jpg")
            filename.endsWith(".jpg") -> filename.removeSuffix(".jpg")
            else -> filename.substringBeforeLast('.')
        }
        if (videoId.isBlank()) return null
        return "$path/m3u8/$videoId.720p-"
    }

    private fun findTotalSegments(baseUrl: String): Int {
        if (!segmentExists("${baseUrl}00001.ts")) return 0
        var high = 1
        while (true) {
            val next = high * 2
            if (next > 9999) break
            if (segmentExists("${baseUrl}${next.toString().padStart(5, '0')}.ts")) high = next else break
        }
        var lo = 1
        var hi = minOf(high, 9999)
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (segmentExists("${baseUrl}${mid.toString().padStart(5, '0')}.ts")) lo = mid + 1 else hi = mid - 1
        }
        return lo - 1
    }

    private fun segmentExists(url: String): Boolean {
        val req = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", dramaBoxUserAgent())
            .header("Referer", "https://www.dramabox.com/")
            .header("Range", "bytes=0-0")
            .build()
        return runCatching {
            HttpClient.client.newCall(req).execute().use { it.code in 200..206 }
        }.getOrDefault(false)
    }

    private fun parseChapterIdFromVideoUrl(url: String): String? {
        val path = url.substringBefore('?').substringBefore('#')
        return chapterFromUrl.find(path)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
    }

    private fun dramaBoxUserAgent(): String =
        "Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
}
