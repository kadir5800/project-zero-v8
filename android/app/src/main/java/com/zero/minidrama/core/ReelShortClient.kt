package com.zero.minidrama.core

import android.content.Context
import android.net.Uri
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

    data class CatalogPage(
        val books: List<JSONObject>,
        val totalPages: Int,
        val categories: List<Pair<String, String>> = emptyList()
    )

    fun search(q: String, page: Int, lang: String, categoryId: String = ""): CatalogPage {
        val prefix = HtmlUtils.langPrefix(lang)
        if (q.isBlank()) return browse(lang, page, categoryId)
        val url = "https://www.reelshort.com$prefix/search?keywords=${URLEncoder.encode(q, "UTF-8")}&page=$page"
        val data = HtmlUtils.fetchNextData(url).getJSONObject("props").getJSONObject("pageProps")
        val books = data.optJSONArray("books") ?: org.json.JSONArray()
        val total = data.optInt("total", 0)
        val pageSize = data.optInt("pageSize", 10).coerceAtLeast(1)
        val totalPages = ((total + pageSize - 1) / pageSize).coerceAtLeast(1)
        val list = buildList {
            for (i in 0 until books.length()) add(books.getJSONObject(i))
        }
        return CatalogPage(list, totalPages)
    }

    fun browse(lang: String, page: Int, categoryId: String = ""): CatalogPage {
        val prefix = HtmlUtils.langPrefix(lang)
        val homeUrl = "https://www.reelshort.com${prefix.ifBlank { "/" }}"
        val pageProps = HtmlUtils.fetchNextData(homeUrl).getJSONObject("props").getJSONObject("pageProps")
        val hall = pageProps.optJSONObject("fallback")
            ?.optJSONObject("/api/ms/hall/webInfo")
            ?: JSONObject()
        val shelves = hall.optJSONArray("bookShelfList") ?: org.json.JSONArray()
        val categories = buildList {
            add("" to if (lang == "tr") "Tümü" else "All")
            for (i in 0 until shelves.length()) {
                val shelf = shelves.getJSONObject(i)
                val books = shelf.optJSONArray("books") ?: continue
                if (books.length() == 0) continue
                val id = shelf.optString("bs_id").ifBlank {
                    shelf.optString("tab_id").ifBlank { shelf.optString("bookshelf_name") }
                }
                val name = shelf.optString("bookshelf_name").ifBlank { id }
                if (id.isNotBlank()) add(id to name)
            }
        }
        val merged = LinkedHashMap<String, JSONObject>()
        for (i in 0 until shelves.length()) {
            val shelf = shelves.getJSONObject(i)
            val shelfId = shelf.optString("bs_id").ifBlank {
                shelf.optString("tab_id").ifBlank { shelf.optString("bookshelf_name") }
            }
            if (categoryId.isNotBlank() && shelfId != categoryId) continue
            val books = shelf.optJSONArray("books") ?: continue
            for (j in 0 until books.length()) {
                val book = books.getJSONObject(j)
                val id = book.optString("book_id")
                if (id.isNotBlank()) merged.putIfAbsent(id, book)
            }
        }
        val all = merged.values.toList()
        val pageSize = 20
        val start = ((page - 1).coerceAtLeast(0)) * pageSize
        val slice = all.drop(start).take(pageSize)
        val totalPages = ((all.size + pageSize - 1) / pageSize).coerceAtLeast(1)
        return CatalogPage(slice, totalPages, categories)
    }

    suspend fun bookDetail(bookId: String, lang: String): JSONObject = withContext(Dispatchers.IO) {
        val prefix = HtmlUtils.langPrefix(lang)
        val firstUrl = "https://www.reelshort.com$prefix/full-episodes/$bookId/1"
        val data1 = HtmlUtils.fetchNextData(firstUrl).getJSONObject("props").getJSONObject("pageProps").getJSONObject("data")
        val totalChapters = data1.optInt("chapter_count")
        val bookName = data1.optString("book_title")
        val slug = HtmlUtils.createSlug(bookName)
        slugCache["${bookId}_$lang"] = slug
        val totalPages = ((totalChapters + 9) / 10).coerceAtLeast(1)
        val jobs = (1..totalPages).map { p ->
            async {
                val pageUrl = "https://www.reelshort.com$prefix/full-episodes/$bookId/$p"
                runCatching {
                    HtmlUtils.fetchNextData(pageUrl).getJSONObject("props").getJSONObject("pageProps")
                        .getJSONObject("data").optJSONArray("chapter_list") ?: org.json.JSONArray()
                }.getOrDefault(org.json.JSONArray())
            }
        }
        val chapters = jobs.awaitAll().flatMap { arr ->
            buildList {
                for (i in 0 until arr.length()) {
                    val ch = arr.getJSONObject(i)
                    val serial = ch.opt("serial_number")
                    if (serial == 0 || serial == "Trailer") continue
                    add(JSONObject().apply {
                        put("serial", serial)
                        put("chapter_id", ch.optString("chapter_id"))
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
        val prefix = HtmlUtils.langPrefix(lang)
        val slug = slugCache["${bookId}_$lang"] ?: run {
            val firstUrl = "https://www.reelshort.com$prefix/full-episodes/$bookId/1"
            val bookName = HtmlUtils.fetchNextData(firstUrl).getJSONObject("props").getJSONObject("pageProps")
                .getJSONObject("data").optString("book_title")
            HtmlUtils.createSlug(bookName).also { slugCache["${bookId}_$lang"] = it }
        }
        val episodeUrl = "https://www.reelshort.com$prefix/episodes/episode-$serial-${URLEncoder.encode(slug, "UTF-8")}-$bookId-$chapterId"
        val epData = HtmlUtils.fetchNextData(episodeUrl).getJSONObject("props").getJSONObject("pageProps").getJSONObject("data")
        val videoUrl = epData.optString("video_url")
        if (videoUrl.isBlank()) throw IllegalStateException("Video URL bulunamadi")

        val manifestUrl = transformVideoUrl(videoUrl)
        if (manifestUrl.endsWith(".m3u8")) {
            val m3u8 = fetchAndRewriteManifest(manifestUrl)
            val file = File(context.cacheDir, "reelshort_${bookId}_${serial}.m3u8")
            file.writeText(m3u8)
            return Uri.fromFile(file).toString()
        }
        return manifestUrl
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
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
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
