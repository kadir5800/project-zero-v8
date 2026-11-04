package com.zero.minidrama.core

import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

class DramaBoxClient {
    fun search(q: String, page: Int, lang: String, categoryId: String = "0"): Pair<List<JSONObject>, Int> {
        val prefix = HtmlUtils.langPrefix(lang)
        val url = if (q.isBlank()) {
            val typeId = categoryId.ifBlank { "0" }
            "https://www.dramabox.com$prefix/browse/$typeId/$page"
        } else {
            "https://www.dramabox.com$prefix/search/$page?searchValue=${URLEncoder.encode(q, "UTF-8")}"
        }
        val data = HtmlUtils.fetchNextData(url).getJSONObject("props").getJSONObject("pageProps")
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
        val data = HtmlUtils.fetchNextData(url).getJSONObject("props").getJSONObject("pageProps")
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

    fun bookDetail(bookId: String, firstEpId: String, lang: String): JSONObject {
        val prefix = HtmlUtils.langPrefix(lang)
        val url = "https://www.dramabox.com$prefix/video/$bookId/$firstEpId"
        val pageProps = HtmlUtils.fetchNextData(url).getJSONObject("props").getJSONObject("pageProps")
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

    private fun getBaseUrlFromCover(coverUrl: String): String? {
        var url = coverUrl.replace("thwztvideo.dramaboxdb.com", "hwzthls.dramaboxdb.com")
        url = url.split("@")[0]
        val lastSlash = url.lastIndexOf('/')
        if (lastSlash < 0) return null
        val path = url.substring(0, lastSlash)
        val filename = url.substring(lastSlash + 1)
        val videoId = filename.replace(".mp4.jpg", "")
        return "$path/m3u8/$videoId.720p-"
    }

    private fun findTotalSegments(baseUrl: String): Int {
        if (!headOk("${baseUrl}00001.ts")) return 0
        var high = 1
        while (true) {
            val next = high * 2
            if (next > 9999) break
            if (headOk("${baseUrl}${next.toString().padStart(5, '0')}.ts")) high = next else break
        }
        var lo = 1
        var hi = minOf(high, 9999)
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (headOk("${baseUrl}${mid.toString().padStart(5, '0')}.ts")) lo = mid + 1 else hi = mid - 1
        }
        return lo - 1
    }

    private fun headOk(url: String): Boolean {
        val req = Request.Builder().url(url).head().build()
        return runCatching {
            HttpClient.client.newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }
}
