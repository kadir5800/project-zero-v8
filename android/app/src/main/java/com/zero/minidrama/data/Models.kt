package com.zero.minidrama.data

import com.google.gson.annotations.SerializedName

data class CategoryItem(
    val id: String,
    val name: String
)

data class SearchResponse(
    val results: List<BookResult> = emptyList(),
    @SerializedName("totalPages") val totalPages: Int = 1,
    @SerializedName("currentPage") val currentPage: Int = 1,
    val categories: List<CategoryItem> = emptyList()
)

data class BookResult(
    @SerializedName("book_id") val bookId: String,
    val title: String = "",
    val pic: String = "",
    val desc: String = "",
    @SerializedName("chapter_count") val chapterCount: Int = 0,
    @SerializedName("first_chapter_id") val firstChapterId: String = "",
    @SerializedName("firstEpId") val firstEpId: String = "",
    @SerializedName("bookNameEn") val bookNameEn: String = ""
)

data class BookDetailResponse(
    @SerializedName("bookId") val bookId: String = "",
    @SerializedName("bookName") val bookName: String = "",
    val alias: String = "",
    val description: String = "",
    val tags: List<String> = emptyList(),
    @SerializedName("readCount") val readCount: Long = 0,
    @SerializedName("collectCount") val collectCount: Long = 0,
    val episodes: List<EpisodeDto> = emptyList()
)

data class EpisodeDto(
    val serial: Int,
    @SerializedName("chapter_id") val chapterId: String,
    val cover: String? = null,
    @SerializedName("isLock") val isLock: Boolean = false
)

data class ReelShortPlayResponse(
    val success: Boolean = false,
    @SerializedName("playUrl") val playUrl: String? = null,
    @SerializedName("subtitleUrl") val subtitleUrl: String? = null,
    val title: String = "",
    val serial: Int = 0,
    val error: String? = null
)

data class NetShortLoginResponse(
    @SerializedName("sessionId") val sessionId: String? = null,
    val success: Boolean = false
)

data class NetShortEpisodeRequest(
    @SerializedName("sessionId") val sessionId: String,
    @SerializedName("dramaId") val dramaId: String,
    @SerializedName("episodeId") val episodeId: String,
    @SerializedName("episodeNo") val episodeNo: Int,
    val lang: String,
    @SerializedName("isLock") val isLock: Boolean = false
)

data class NetShortEpisodeResponse(
    val success: Boolean = false,
    val episode: NetShortEpisode? = null,
    val error: String? = null
)

data class NetShortEpisode(
    @SerializedName("videoUrl") val videoUrl: String? = null,
    @SerializedName("episodeNo") val episodeNo: Int = 0,
    val subtitles: List<NetShortSubtitle> = emptyList()
)

data class NetShortSubtitle(
    val language: String = "",
    val url: String = ""
)

data class DramaBoxM3u8Request(val cover: String)
data class DramaBoxM3u8Response(
    val success: Boolean = false,
    val m3u8: String? = null,
    val error: String? = null
)

data class HistoryItem(
    val bookId: String,
    val title: String,
    val pic: String,
    val platform: String,
    val lang: String,
    val serial: Int,
    val firstEpId: String = "",
    val ts: Long = System.currentTimeMillis()
)

data class PlayPayload(
    val bookId: String,
    val bookName: String,
    val pic: String,
    val platform: String,
    val lang: String,
    val serial: Int,
    val chapterId: String,
    val cover: String? = null,
    val isLock: Boolean = false
)
