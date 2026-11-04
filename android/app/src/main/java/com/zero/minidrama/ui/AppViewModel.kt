package com.zero.minidrama.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zero.minidrama.data.BookDetailResponse
import com.zero.minidrama.data.BookResult
import com.zero.minidrama.data.CategoryItem
import com.zero.minidrama.data.HistoryItem
import com.zero.minidrama.data.PlayPayload
import com.zero.minidrama.data.PreferencesStore
import com.zero.minidrama.data.ZeroRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppUiState(
    val platform: String = "reelshort",
    val lang: String = "tr",
    val query: String = "",
    val categoryId: String = "",
    val categories: List<CategoryItem> = emptyList(),
    val results: List<BookResult> = emptyList(),
    val totalPages: Int = 1,
    val currentPage: Int = 1,
    val isSearching: Boolean = false,
    val isLoadingBook: Boolean = false,
    val bookDetail: BookDetailResponse? = null,
    val selectedBookPic: String = "",
    val history: List<HistoryItem> = emptyList(),
    val playPayload: PlayPayload? = null,
    val playUrl: String? = null,
    val isLoadingPlay: Boolean = false,
    val error: String? = null
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = PreferencesStore(app)
    private val repo = ZeroRepository(app)
    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = combine(
        _state,
        prefs.platform,
        prefs.lang,
        prefs.history
    ) { ui, platform, lang, history ->
        ui.copy(platform = platform, lang = lang, history = history)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppUiState())

    init {
        viewModelScope.launch {
            prefs.restoreFromBackupIfEmpty()
            browseCatalog()
        }
    }

    fun setQuery(q: String) {
        _state.value = _state.value.copy(query = q)
    }

    fun setPlatform(platform: String) {
        viewModelScope.launch {
            prefs.setPlatform(platform)
            _state.value = _state.value.copy(
                query = "",
                categoryId = "",
                categories = emptyList(),
                results = emptyList(),
                currentPage = 1,
                error = null
            )
            browseCatalog(platformOverride = platform)
        }
    }

    fun setLang(lang: String) {
        viewModelScope.launch {
            prefs.setLang(lang)
            _state.value = _state.value.copy(categoryId = "")
            browseCatalog(langOverride = lang)
        }
    }

    fun setCategory(categoryId: String) {
        _state.value = _state.value.copy(categoryId = categoryId, query = "")
        browseCatalog(categoryOverride = categoryId)
    }

    private fun browseCatalog(
        platformOverride: String? = null,
        langOverride: String? = null,
        categoryOverride: String? = null
    ) {
        viewModelScope.launch {
            val s = state.value
            val platform = platformOverride ?: s.platform
            val lang = langOverride ?: s.lang
            val categoryId = categoryOverride ?: s.categoryId
            _state.value = s.copy(
                isSearching = true,
                error = null,
                currentPage = 1,
                query = "",
                categoryId = categoryId
            )
            try {
                val res = repo.search("", 1, lang, platform, categoryId)
                _state.value = _state.value.copy(
                    results = res.results,
                    totalPages = res.totalPages.coerceAtLeast(1),
                    currentPage = 1,
                    categories = res.categories.ifEmpty { _state.value.categories },
                    isSearching = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isSearching = false, error = e.message ?: "Katalog yuklenemedi")
            }
        }
    }

    fun search(page: Int = 1) {
        val s = state.value
        val q = s.query.trim()
        viewModelScope.launch {
            _state.value = s.copy(isSearching = true, error = null, currentPage = page)
            try {
                val res = repo.search(q, page, s.lang, s.platform, if (q.isBlank()) s.categoryId else "")
                _state.value = _state.value.copy(
                    results = res.results,
                    totalPages = res.totalPages.coerceAtLeast(1),
                    currentPage = page,
                    categories = res.categories.ifEmpty { _state.value.categories },
                    isSearching = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isSearching = false, error = e.message ?: "Arama hatasi")
            }
        }
    }

    fun openBook(book: BookResult) {
        viewModelScope.launch {
            val s = state.value
            _state.value = s.copy(isLoadingBook = true, error = null, bookDetail = null, selectedBookPic = book.pic)
            try {
                val firstEpId = book.firstEpId.ifBlank { book.firstChapterId }
                val detail = repo.bookDetail(book.bookId, s.lang, s.platform, firstEpId.ifBlank { null })
                _state.value = _state.value.copy(isLoadingBook = false, bookDetail = detail, selectedBookPic = book.pic)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoadingBook = false, error = e.message ?: "Kitap yuklenemedi")
            }
        }
    }

    fun resumeHistory(item: HistoryItem) {
        viewModelScope.launch {
            prefs.setPlatform(item.platform)
            prefs.setLang(item.lang)
            _state.value = state.value.copy(
                isLoadingBook = true,
                error = null,
                bookDetail = null,
                selectedBookPic = item.pic
            )
            try {
                val detail = repo.bookDetail(
                    item.bookId,
                    item.lang,
                    item.platform,
                    item.firstEpId.ifBlank { null }
                )
                _state.value = _state.value.copy(
                    isLoadingBook = false,
                    bookDetail = detail,
                    selectedBookPic = item.pic
                )
                val ep = detail.episodes.find { it.serial == item.serial }
                    ?: detail.episodes.firstOrNull()
                    ?: return@launch
                playEpisode(
                    detail.bookId,
                    detail.bookName,
                    item.pic,
                    ep.serial,
                    ep.chapterId,
                    ep.cover,
                    ep.isLock
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoadingBook = false,
                    error = e.message ?: "Gecmis yuklenemedi"
                )
            }
        }
    }

    fun playEpisode(
        bookId: String,
        bookName: String,
        pic: String,
        serial: Int,
        chapterId: String,
        cover: String?,
        isLock: Boolean
    ) {
        viewModelScope.launch {
            val s = state.value
            val payload = PlayPayload(bookId, bookName, pic, s.platform, s.lang, serial, chapterId, cover, isLock)
            _state.value = s.copy(playPayload = payload, playUrl = null, isLoadingPlay = true, error = null)
            prefs.addHistory(
                HistoryItem(
                    bookId = bookId,
                    title = bookName,
                    pic = pic,
                    platform = s.platform,
                    lang = s.lang,
                    serial = serial,
                    firstEpId = chapterId
                )
            )
            try {
                val url = repo.resolvePlayUrl(payload)
                _state.value = _state.value.copy(playUrl = url, isLoadingPlay = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoadingPlay = false,
                    playUrl = null,
                    error = e.message ?: "Video acilamadi"
                )
            }
        }
    }

    fun clearPlay() {
        _state.value = _state.value.copy(playPayload = null, playUrl = null, isLoadingPlay = false)
    }

    fun clearBookDetail() {
        _state.value = _state.value.copy(bookDetail = null)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun reportPlayError(message: String) {
        _state.value = _state.value.copy(error = message, isLoadingPlay = false)
    }

    fun clearHistory() {
        viewModelScope.launch { prefs.clearHistory() }
    }

    fun restoreHistoryFromBackup(force: Boolean = true, onResult: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val count = prefs.restoreFromBackup(force)
            onResult(count)
        }
    }
}
