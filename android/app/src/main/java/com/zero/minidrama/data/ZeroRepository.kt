package com.zero.minidrama.data

import android.content.Context
import android.net.Uri
import com.zero.minidrama.core.DramaBoxClient
import com.zero.minidrama.core.DramaWaveClient
import com.zero.minidrama.core.NetShortClient
import com.zero.minidrama.core.ReelShortClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ZeroRepository(private val context: Context) {
    private val reelShort = ReelShortClient()
    private val dramaBox = DramaBoxClient()
    private val netShort = NetShortClient()
    private val dramaWave = DramaWaveClient(context)

    suspend fun search(
        q: String,
        page: Int,
        lang: String,
        platform: String,
        categoryId: String = ""
    ): SearchResponse =
        withContext(Dispatchers.IO) {
            when (platform) {
                "netshort" -> {
                    val data = withNetShort(lang) {
                        netShort.search(q, page, lang, categoryId).optJSONObject("data")
                    } ?: JSONObject()
                    val dramas = data.optJSONArray("searchCodeSearchResult") ?: JSONArray()
                    val total = data.optInt("total").takeIf { it > 0 }
                        ?: data.optInt("totalNum").takeIf { it > 0 }
                        ?: dramas.length()
                    val results = jsonArrayToBooks(dramas) { d ->
                        BookResult(
                            bookId = d.optString("shortPlayId"),
                            title = stripHl(d.optString("shortPlayName")),
                            pic = d.optString("shortPlayCover"),
                            desc = stripHl(d.optString("shotIntroduce", d.optString("shortPlayDescription"))),
                            chapterCount = d.optInt("shortPlayEpisodeCount"),
                            bookNameEn = stripHl(d.optString("shortPlayNameEn"))
                        )
                    }
                    SearchResponse(
                        results,
                        ((total + 19) / 20).coerceAtLeast(1),
                        page,
                        netShort.categoryChips(lang).map { CategoryItem(it.first, it.second) }
                    )
                }
                "dramabox" -> {
                    val cat = categoryId.ifBlank { "0" }
                    val (books, totalPages) = dramaBox.search(q, page, lang, cat)
                    val results = books.map { b ->
                        BookResult(
                            bookId = b.optString("bookId"),
                            title = b.optString("bookName"),
                            pic = (b.optString("coverWap", b.optString("cover"))).split("?")[0],
                            desc = b.optString("introduction", b.optString("intro")),
                            chapterCount = b.optInt("totalChapterNum", b.optInt("chapterNum")),
                            firstChapterId = b.optString("chapterId", b.optString("firstEpId")),
                            firstEpId = b.optString("chapterId", b.optString("firstEpId")),
                            bookNameEn = b.optString("bookNameEn")
                        )
                    }
                    val cats = if (q.isBlank()) {
                        runCatching { dramaBox.categories(lang) }.getOrDefault(emptyList())
                            .map { CategoryItem(it.first, it.second) }
                    } else emptyList()
                    SearchResponse(results, totalPages, page, cats)
                }
                "dramawave" -> {
                    val (books, totalPages) = dramaWave.search(q, page, lang)
                    val filtered = if (categoryId.isBlank()) books else books.filter { b ->
                        val tags = b.optJSONArray("content_tags")
                        if (tags != null) {
                            (0 until tags.length()).any { tags.optString(it).equals(categoryId, true) }
                        } else {
                            b.optString("title", b.optString("name")).contains(categoryId, true)
                        }
                    }
                    val tagSet = LinkedHashSet<String>()
                    books.forEach { b ->
                        val tags = b.optJSONArray("content_tags") ?: return@forEach
                        for (i in 0 until tags.length()) {
                            val t = tags.optString(i)
                            if (t.isNotBlank()) tagSet.add(t)
                        }
                    }
                    val results = filtered.map { b ->
                        val firstEp = b.optJSONObject("episode_info") ?: b.optJSONObject("episode")
                        BookResult(
                            bookId = b.optString("key", b.optString("id")),
                            title = b.optString("title", b.optString("name")),
                            pic = b.optString("cover").split("?")[0],
                            desc = b.optString("desc", b.optString("description")),
                            chapterCount = b.optInt("episode_count"),
                            firstChapterId = firstEp?.optString("id").orEmpty(),
                            firstEpId = firstEp?.optString("id").orEmpty(),
                            bookNameEn = b.optString("key")
                        )
                    }
                    val cats = listOf(CategoryItem("", if (lang == "tr") "Tümü" else "All")) +
                        tagSet.take(20).map { CategoryItem(it, it) }
                    SearchResponse(results, totalPages, page, cats)
                }
                else -> {
                    val pageData = reelShort.search(q, page, lang, categoryId)
                    val results = pageData.books.map { b ->
                        BookResult(
                            bookId = b.optString("book_id"),
                            title = b.optString("book_title"),
                            pic = b.optString("book_pic"),
                            desc = b.optString("special_desc", b.optString("share_text")),
                            chapterCount = b.optInt("chapter_count"),
                            firstChapterId = b.optString("first_chapter_id")
                        )
                    }
                    SearchResponse(
                        results,
                        pageData.totalPages,
                        page,
                        pageData.categories.map { CategoryItem(it.first, it.second) }
                    )
                }
            }
        }

    suspend fun bookDetail(bookId: String, lang: String, platform: String, firstEpId: String?): BookDetailResponse =
        withContext(Dispatchers.IO) {
            when (platform) {
                "netshort" -> {
                    val data = withNetShort(lang) {
                        netShort.bookDetail(bookId, lang).optJSONObject("data")
                    } ?: JSONObject()
                    val eps = data.optJSONArray("shortPlayEpisodeInfos") ?: JSONArray()
                    BookDetailResponse(
                        bookId = bookId,
                        bookName = stripHl(data.optString("shortPlayName")),
                        alias = stripHl(data.optString("shortPlayNameEn")),
                        description = stripHl(data.optString("shortPlayDescription")),
                        readCount = data.optLong("shortPlayViewCount"),
                        collectCount = data.optLong("shortPlayLikeCount"),
                        tags = listOfNotNull(data.optString("shortPlayCategoryName").takeIf { it.isNotBlank() }),
                        episodes = jsonArrayToEpisodes(eps) { e ->
                            EpisodeDto(
                                serial = e.optInt("episodeNo"),
                                chapterId = e.optString("episodeId"),
                                cover = e.optString("episodeCover", data.optString("shortPlayCover")),
                                isLock = e.optBoolean("isLock")
                            )
                        }.sortedBy { it.serial }
                    )
                }
                "dramabox" -> {
                    val epId = firstEpId ?: throw IllegalStateException("DramaBox icin firstEpId gerekli")
                    val data = dramaBox.bookDetail(bookId, epId, lang)
                    BookDetailResponse(
                        bookId = bookId,
                        bookName = data.optString("bookName"),
                        alias = data.optString("alias"),
                        description = data.optString("description"),
                        readCount = data.optLong("readCount"),
                        collectCount = data.optLong("collectCount"),
                        tags = jsonArrayToStringList(data.optJSONArray("tags")),
                        episodes = jsonArrayToEpisodes(data.optJSONArray("episodes")) { e ->
                            EpisodeDto(
                                serial = e.optInt("serial"),
                                chapterId = e.optString("chapter_id"),
                                cover = e.optString("cover")
                            )
                        }
                    )
                }
                "dramawave" -> {
                    val data = dramaWave.bookDetail(bookId, lang)
                    BookDetailResponse(
                        bookId = bookId,
                        bookName = data.optString("bookName"),
                        alias = data.optString("alias"),
                        description = data.optString("description"),
                        readCount = data.optLong("readCount"),
                        collectCount = data.optLong("collectCount"),
                        tags = jsonArrayToStringList(data.optJSONArray("tags")),
                        episodes = jsonArrayToEpisodes(data.optJSONArray("episodes")) { e ->
                            EpisodeDto(
                                serial = e.optInt("serial"),
                                chapterId = e.optString("chapter_id"),
                                cover = e.optString("cover")
                            )
                        }.sortedBy { it.serial }
                    )
                }
                else -> {
                    val data = reelShort.bookDetail(bookId, lang)
                    BookDetailResponse(
                        bookId = bookId,
                        bookName = data.optString("bookName"),
                        alias = data.optString("alias"),
                        description = data.optString("description"),
                        readCount = data.optLong("readCount"),
                        collectCount = data.optLong("collectCount"),
                        tags = jsonArrayToStringList(data.optJSONArray("tags")),
                        episodes = jsonArrayToEpisodes(data.optJSONArray("episodes")) { e ->
                            EpisodeDto(
                                serial = e.optInt("serial"),
                                chapterId = e.optString("chapter_id")
                            )
                        }
                    )
                }
            }
        }

    suspend fun resolvePlayUrl(payload: PlayPayload): String = withContext(Dispatchers.IO) {
        when (payload.platform) {
            "reelshort" -> reelShort.playUrl(context, payload.bookId, payload.serial, payload.chapterId, payload.lang)
            "netshort" -> withNetShort(payload.lang) {
                netShort.episodeVideo(
                    payload.bookId,
                    payload.chapterId,
                    payload.serial,
                    payload.lang,
                    payload.isLock
                )
            }
            "dramabox" -> {
                val cover = payload.cover ?: throw IllegalStateException("DramaBox cover eksik")
                val m3u8 = dramaBox.generateM3u8(cover)
                val file = File(context.cacheDir, "dramabox_${payload.bookId}_${payload.serial}.m3u8")
                file.writeText(m3u8)
                Uri.fromFile(file).toString()
            }
            "dramawave" -> dramaWave.episodeVideo(payload.bookId, payload.chapterId, payload.lang)
            else -> throw IllegalStateException("Platform desteklenmiyor")
        }
    }

    private inline fun <T> jsonArrayToBooks(arr: JSONArray, map: (JSONObject) -> T): List<T> =
        buildList { for (i in 0 until arr.length()) add(map(arr.getJSONObject(i))) }

    private inline fun jsonArrayToEpisodes(arr: JSONArray?, map: (JSONObject) -> EpisodeDto): List<EpisodeDto> {
        if (arr == null) return emptyList()
        return buildList { for (i in 0 until arr.length()) add(map(arr.getJSONObject(i))) }
    }

    private fun jsonArrayToStringList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val v = arr.optString(i)
                if (v.isNotBlank()) add(v)
            }
        }
    }

    private fun stripHl(text: String): String =
        text.replace(Regex("</?em>", RegexOption.IGNORE_CASE), "").trim()

    private suspend fun <T> withNetShort(lang: String, block: () -> T): T {
        netShort.ensureLogin(lang)
        return try {
            block()
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            if (msg.contains("bad base", ignoreCase = true) ||
                msg.contains("cozulemedi", ignoreCase = true) ||
                msg.contains("Encrypt-Key", ignoreCase = true) ||
                msg.contains("sifre", ignoreCase = true)
            ) {
                netShort.ensureLogin(lang, force = true)
                block()
            } else {
                throw e
            }
        }
    }
}
