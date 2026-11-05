package com.kativa.reader.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kativa.reader.core.auth.ActiveServerStore
import com.kativa.reader.core.network.KativaError
import com.kativa.reader.core.network.Outcome
import com.kativa.reader.data.repository.LibraryRepository
import com.kativa.reader.data.repository.ProgressRepository
import com.kativa.reader.domain.model.ReaderKind
import com.kativa.reader.domain.model.ReaderSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Reasons the reader can refuse to render. */
enum class ReaderBlockReason { UnsupportedFormat, NoNetwork }

data class ReaderUiState(
    val loading: Boolean = true,
    val session: ReaderSession? = null,
    /** Pre-built page URLs (index == page index). Empty when the session is not signed in. */
    val pageUrls: List<String> = emptyList(),
    /** 0-based page index, matching the `page` query parameter. */
    val pageIndex: Int = 0,
    val controlsVisible: Boolean = false,
    val bookmarkedPages: Set<Int> = emptySet(),
    val error: KativaError? = null,
    val blockReason: ReaderBlockReason? = null,
    val paused: Boolean = false,
    /** Set when the reader resolved a next/previous chapter; the screen consumes it once. */
    val pendingChapterNavigation: Int? = null,
) {
    val totalPages: Int get() = session?.totalPages ?: 0

    val displayPage: Int get() = pageIndex + 1

    val progress: Float
        get() = if (totalPages <= 0) 0f else (displayPage.toFloat() / totalPages).coerceIn(0f, 1f)

    val isLastPage: Boolean get() = totalPages > 0 && pageIndex >= totalPages - 1

    val isFirstPage: Boolean get() = pageIndex <= 0
}

/**
 * Manga reader state machine (AGENTS.md §5).
 *
 * Owns the two rules that are easy to get wrong:
 *  - the UI works in 0-based indexes, the API takes 1-based `pageNum`;
 *  - progress is written on a debounce, and again immediately on stop/close.
 */
@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val progressRepository: ProgressRepository,
    private val activeServerStore: ActiveServerStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val chapterId: Int = savedStateHandle.get<String>("chapterId")?.toIntOrNull() ?: 0

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private var saveJob: Job? = null

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null, blockReason = null) }
        viewModelScope.launch {
            when (val outcome = libraryRepository.openChapter(chapterId)) {
                is Outcome.Success -> {
                    val session = outcome.value
                    if (session.format.reader != ReaderKind.Manga) {
                        // EPUB/PDF readers land in M3; do not pretend to render them.
                        _state.update {
                            it.copy(
                                loading = false,
                                session = session,
                                blockReason = ReaderBlockReason.UnsupportedFormat,
                            )
                        }
                        return@launch
                    }
                    if (session.totalPages <= 0) {
                        _state.update {
                            it.copy(
                                loading = false,
                                session = session,
                                error = KativaError.Parse,
                            )
                        }
                        return@launch
                    }
                    _state.update {
                        it.copy(
                            loading = false,
                            session = session,
                            pageUrls = buildPageUrls(session),
                            pageIndex = session.startPageIndex,
                        )
                    }
                    loadBookmarks()
                }

                is Outcome.Failure -> _state.update {
                    it.copy(
                        loading = false,
                        error = outcome.failure.error,
                        blockReason = if (outcome.failure.error == KativaError.Network) {
                            ReaderBlockReason.NoNetwork
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }

    private fun buildPageUrls(session: ReaderSession): List<String> {
        val server = activeServerStore.active.value ?: return emptyList()
        val key = activeServerStore.session.value?.imageKey ?: return emptyList()
        val base = server.baseUrl.trimEnd('/')
        val encoded = java.net.URLEncoder.encode(key, "UTF-8")
        return List(session.totalPages) { index ->
            "$base/Reader/image?chapterId=${session.chapterId}&page=$index&apiKey=$encoded"
        }
    }

    private fun loadBookmarks() {
        viewModelScope.launch {
            val pages = libraryRepository.bookmarks(chapterId)
            // API bookmarks are 1-based page numbers; the UI keeps 0-based indexes.
            _state.update { it.copy(bookmarkedPages = pages.map { p -> p - 1 }.toSet()) }
        }
    }

    fun toggleControls() = _state.update { it.copy(controlsVisible = !it.controlsVisible) }

    fun hideControls() = _state.update { it.copy(controlsVisible = false) }

    /** Called by the pager; ignores no-op changes so it does not spam the API. */
    fun onPageChanged(index: Int) {
        val current = _state.value
        if (index == current.pageIndex) return
        _state.update { it.copy(pageIndex = index) }
        scheduleSave()
    }

    fun goToPage(index: Int) {
        val total = _state.value.totalPages
        if (total <= 0) return
        onPageChanged(index.coerceIn(0, total - 1))
    }

    /**
     * Debounced progress write. 800 ms matches the backend's tolerance and keeps the number of
     * requests sane while swiping (AGENTS.md §5).
     */
    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            persistCurrentPage()
        }
    }

    /** Immediate write, used on stop/close and before leaving the screen. */
    fun flushProgress() {
        saveJob?.cancel()
        viewModelScope.launch { persistCurrentPage() }
    }

    private suspend fun persistCurrentPage() {
        val s = _state.value.session ?: return
        val pageNum = (_state.value.pageIndex + 1).coerceAtMost(s.totalPages + 1)
        progressRepository.save(
            chapterId = s.chapterId,
            seriesId = s.seriesId,
            volumeId = s.volumeId,
            libraryId = s.libraryId,
            pageNum = pageNum,
        )
    }

    /** Marks the chapter finished with `pages + 1`, the value Kavita expects (§4.6 item 2). */
    fun markFinished() {
        val s = _state.value.session ?: return
        viewModelScope.launch {
            progressRepository.save(
                chapterId = s.chapterId,
                seriesId = s.seriesId,
                volumeId = s.volumeId,
                libraryId = s.libraryId,
                pageNum = s.totalPages + 1,
            )
        }
    }

    fun toggleBookmark() {
        val s = _state.value.session ?: return
        val index = _state.value.pageIndex
        val isBookmarked = index in _state.value.bookmarkedPages
        viewModelScope.launch {
            val outcome = if (isBookmarked) {
                libraryRepository.removeBookmark(s.chapterId, s.seriesId, s.volumeId, index + 1)
            } else {
                libraryRepository.addBookmark(s.chapterId, s.seriesId, s.volumeId, index + 1)
            }
            if (outcome is Outcome.Success) {
                _state.update {
                    val next = it.bookmarkedPages.toMutableSet()
                    if (isBookmarked) next.remove(index) else next.add(index)
                    it.copy(bookmarkedPages = next)
                }
            }
        }
    }

    /** Navigation helpers resolve through state so this VM stays free of runBlocking. */
    fun requestNextChapter() {
        val session = _state.value.session ?: return
        viewModelScope.launch {
            val next = libraryRepository
                .nextChapter(session.seriesId, session.volumeId, session.chapterId)
                .takeIf { it > 0 }
            next?.let { target -> _state.update { it.copy(pendingChapterNavigation = target) } }
        }
    }

    fun requestPrevChapter() {
        val session = _state.value.session ?: return
        viewModelScope.launch {
            val prev = libraryRepository
                .prevChapter(session.seriesId, session.volumeId, session.chapterId)
                .takeIf { it > 0 }
            prev?.let { target -> _state.update { it.copy(pendingChapterNavigation = target) } }
        }
    }

    /** Called by the screen after it navigates, so the same target cannot fire twice. */
    fun consumeNavigationTarget() {
        _state.update { it.copy(pendingChapterNavigation = null) }
    }

    fun onAppPaused() {
        _state.update { it.copy(paused = true) }
        flushProgress()
    }

    fun onAppResumed() {
        _state.update { it.copy(paused = false) }
        viewModelScope.launch { progressRepository.flush() }
    }

    override fun onCleared() {
        super.onCleared()
        // Best-effort final flush; the queue row is already on disk either way.
        flushProgress()
    }

    private companion object {
        const val SAVE_DEBOUNCE_MS = 800L
    }
}
