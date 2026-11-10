package com.kativa.reader.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kativa.reader.core.auth.ActiveServerStore
import com.kativa.reader.core.network.KativaError
import com.kativa.reader.core.network.Outcome
import com.kativa.reader.core.settings.ReaderSettingsStore
import com.kativa.reader.data.repository.LibraryRepository
import com.kativa.reader.data.repository.ProgressRepository
import com.kativa.reader.domain.model.ReaderKind
import com.kativa.reader.domain.model.ReaderSession
import com.kativa.reader.domain.model.PageSlot
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    val settingsSheetVisible: Boolean = false,
    val bookmarkedPages: Set<Int> = emptySet(),
    val error: KativaError? = null,
    val blockReason: ReaderBlockReason? = null,
    val paused: Boolean = false,
    /** Set when the reader resolved a next/previous chapter; the screen consumes it once. */
    val pendingChapterNavigation: Int? = null,
    val settings: ReaderSettings = ReaderSettings.Default,
    /** Double-page slots, recomputed whenever the chapter or layout changes. */
    val slots: List<com.kativa.reader.domain.model.PageSlot> = emptyList(),
    /**
     * False for accounts without the `Bookmark`/`Admin` role: the server rejects the endpoint, so
     * the affordance is hidden rather than offered and then failed (docs/FEATURES.md F-17).
     */
    val canBookmark: Boolean = true,
) {
    val totalPages: Int get() = session?.totalPages ?: 0

    val displayPage: Int get() = pageIndex + 1

    val progress: Float
        get() = if (totalPages <= 0) 0f else (displayPage.toFloat() / totalPages).coerceIn(0f, 1f)

    val isLastPage: Boolean get() = totalPages > 0 && pageIndex >= totalPages - 1

    val isFirstPage: Boolean get() = pageIndex <= 0

    /** How many slots the pager renders; single-page mode is one slot per page. */
    val slotCount: Int
        get() = if (settings.isDoublePage) slots.size.coerceAtLeast(1) else totalPages

    /** Slot index that currently contains the reading position. */
    val currentSlot: Int
        get() = if (settings.isDoublePage) {
            com.kativa.reader.domain.model.PagePairing.slotIndexOf(slots, displayPage) ?: 0
        } else {
            pageIndex
        }

    fun slotPages(slotIndex: Int): List<Int> {
        if (!settings.isDoublePage) {
            return listOf(slotIndex + 1)
        }
        val slot = slots.getOrNull(slotIndex) ?: return emptyList()
        return listOfNotNull(slot.first, slot.second)
    }
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
    private val readerSettingsStore: ReaderSettingsStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val chapterId: Int = savedStateHandle.get<String>("chapterId")?.toIntOrNull() ?: 0

    /**
     * Explicit 0-based page to open at (set when arriving from a bookmark).
     *
     * `null` means "resume from server progress". It only applies to the first load: paging away
     * must not be undone by it, so it is consumed alongside the initial session.
     */
    private var explicitStartPage: Int? =
        savedStateHandle.get<Int>("page")?.takeIf { it >= 0 }

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private var saveJob: Job? = null

    init {
        _state.update { it.copy(canBookmark = activeServerStore.session.value?.canBookmark == true) }
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
                    // Resolve the persisted settings *before* publishing the session so the first
                    // render already uses the right paging mode: publishing defaults first and
                    // correcting afterwards made the chapter flash from page 1 to the real spread
                    // and, in double-page mode, briefly remap the reading position.
                    val restored = ReaderSettingsPersistence.toRenderModel(
                        stored = readerSettingsStore
                            .settingsFor(activeServerStore.activeId, session.seriesId)
                            .first(),
                        current = _state.value.settings,
                    )
                    _state.update {
                        it.copy(
                            loading = false,
                            session = session,
                            pageUrls = buildPageUrls(session),
                            pageIndex = resolveStartPage(session),
                            settings = restored,
                            slots = buildSlots(session, restored),
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

    /**
     * Where the first render should start.
     *
     * A bookmark link carries an explicit **0-based** page index — the same space as
     * `Reader/image?page=` and `BookmarkDto.page` — which wins over saved progress, because the
     * user asked for *that* page. [ReaderStartPage] clamps it and the value is then discarded, so
     * later swipes are not snapped back to it.
     */
    private fun resolveStartPage(session: ReaderSession): Int {
        val explicit = explicitStartPage
        explicitStartPage = null
        return com.kativa.reader.domain.model.ReaderStartPage.resolve(
            explicitPageIndex = explicit,
            totalPages = session.totalPages,
            progressPageIndex = session.startPageIndex,
        )
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

    /** Wide pages come from `chapter-info.doublePairs`/`pageDimensions`; 1-based. */
    private fun buildSlots(session: ReaderSession, settings: ReaderSettings): List<PageSlot> {
        if (!settings.isDoublePage) return emptyList()
        val wide = session.pageDimensions.filter { it.isWide }.map { it.pageNumber }.toSet()
        return com.kativa.reader.domain.model.PagePairing.doubleSlots(
            totalPages = session.totalPages,
            widePages = wide,
            startOffset = if (settings.coverOffset) 0 else 1,
        )
    }

    // --- settings -------------------------------------------------------

    fun openSettings() = _state.update { it.copy(settingsSheetVisible = true, controlsVisible = true) }

    fun closeSettings() = _state.update { it.copy(settingsSheetVisible = false) }

    fun setReadingDirection(direction: com.kativa.reader.domain.model.ReadingDirection) =
        updateSettings { it.copy(readingDirection = direction) }

    fun setReaderMode(mode: com.kativa.reader.domain.model.ReaderMode) =
        updateSettings { it.copy(readerMode = mode) }

    fun setPageLayout(mode: com.kativa.reader.domain.model.PageLayout) =
        updateSettings { it.copy(layoutMode = mode) }

    fun setScaling(option: com.kativa.reader.domain.model.ScalingOption) =
        updateSettings { it.copy(scalingOption = option) }

    fun setCoverOffset(enabled: Boolean) = updateSettings { it.copy(coverOffset = enabled) }

    fun setBrightness(value: Float) =
        updateVisualSetting { it.copy(brightness = value.coerceIn(0.2f, 1f)) }

    fun setShowPageNumber(enabled: Boolean) =
        updateSettings { it.copy(showPageNumber = enabled) }

    fun setForceLandscape(enabled: Boolean) =
        updateSettings { it.copy(forceLandscape = enabled) }

    /**
     * "Restore this series' defaults": drops the per-series override so the title falls back to the
     * global defaults the user configured on the settings screen (AGENTS.md §6 空态必须有下一步;
     * same idea here — the button must leave the user somewhere sensible, not at factory settings).
     */
    fun resetSeriesSettings() {
        val seriesId = _state.value.session?.seriesId ?: return
        viewModelScope.launch {
            readerSettingsStore.clearSeries(activeServerStore.activeId, seriesId)
            val restored = ReaderSettingsPersistence.toRenderModel(
                stored = readerSettingsStore
                    .settingsFor(activeServerStore.activeId, seriesId)
                    .first(),
                current = _state.value.settings,
            )
            applySettings { restored }
        }
    }

    /**
     * Volume-key paging (AGENTS.md §5: "音量键翻页（可关）").
     *
     * Off by default so the hardware keys keep their normal meaning until the user opts in, and
     * a toggle in the reader sheet explains why.
     */
    fun setVolumeKeyPaging(enabled: Boolean) = updateSettings { it.copy(volumeKeyPaging = enabled) }

    /**
     * Settings that change the page→slot mapping must rebuild slots *and* remap the reading
     * position so the user stays on the page they were looking at.
     *
     * Visual-only settings (brightness, page-number marker) use [updateVisualSetting] instead:
     * rebuilding the slot list on every brightness frame used to remap the reading position
     * continuously while the user dragged the slider.
     *
     * Every persisted setting is written to [ReaderSettingsStore] for the current series, so
     * closing the reader or opening the next chapter keeps the choice. Brightness goes through
     * [updateVisualSetting] and is therefore never stored (it depends on the room, not the title).
     */
    private fun updateSettings(transform: (ReaderSettings) -> ReaderSettings) {
        val next = applySettings(transform)
        persistSettings(next)
    }

    /**
     * Applies a transformation to the settings, remapping the reading position when needed, and
     * returns the value that was actually committed.
     *
     * The transform runs inside `update` (not on a snapshot taken before it) so concurrent changes
     * cannot be lost; the returned value is what was written by the last successful attempt.
     */
    private fun applySettings(transform: (ReaderSettings) -> ReaderSettings): ReaderSettings {
        var committed = _state.value.settings
        _state.update { current ->
            val next = transform(current.settings)
            committed = next
            val session = current.session
            if (session == null) {
                current.copy(settings = next)
            } else {
                val slots = buildSlots(session, next)
                val leading = if (next.isDoublePage && slots.isNotEmpty()) {
                    com.kativa.reader.domain.model.PagePairing.leadingPageOf(slots, current.displayPage)
                } else {
                    current.displayPage
                }
                current.copy(
                    settings = next,
                    slots = slots,
                    pageIndex = (leading - 1).coerceIn(0, (session.totalPages - 1).coerceAtLeast(0)),
                )
            }
        }
        return committed
    }

    /**
     * Stores the per-series override. Fire-and-forget on purpose: a failed DataStore write must not
     * break reading, and the in-memory value already reflects the user's choice.
     */
    private fun persistSettings(settings: ReaderSettings) {
        val seriesId = _state.value.session?.seriesId ?: return
        viewModelScope.launch {
            readerSettingsStore.setSeries(
                serverId = activeServerStore.activeId,
                seriesId = seriesId,
                value = ReaderSettingsPersistence.toStored(settings),
            )
        }
    }

    /** Updates a setting that does not affect slot construction or the reading position. */
    private fun updateVisualSetting(transform: (ReaderSettings) -> ReaderSettings) {
        _state.update { it.copy(settings = transform(it.settings)) }
    }

    private fun loadBookmarks() {
        viewModelScope.launch {
            val pages = libraryRepository.bookmarks(chapterId)
            // `BookmarkDto.page` is a 0-based image index (same as the reader's `pageIndex` and
            // `Reader/image?page=`); no conversion is needed or allowed here.
            _state.update { it.copy(bookmarkedPages = pages.toSet()) }
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
     * Volume-key paging handler. Returns true when the event was consumed so it does not also
     * change the media volume; false lets the system have it.
     */
    fun handleVolumeKey(event: android.view.KeyEvent): Boolean {
        if (!_state.value.settings.volumeKeyPaging) return false
        if (event.action != android.view.KeyEvent.ACTION_DOWN) return true
        val delta = VolumeKeyPaging.pageDelta(event.keyCode) ?: return false
        goToPage(_state.value.pageIndex + delta)
        return true
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

    /**
     * Toggles a bookmark on the current page.
     *
     * `BookmarkDto.page` is a **0-based** image index, the same space as `Reader/image?page=` and
     * `Image/bookmark?pageNum=` (the Kavita web reader posts its 0-based `pageNum`). Sending
     * `index + 1` stored a row one page past what the user tapped, so the bookmark list opened
     * the wrong page and `Image/bookmark` 400'd on the last page of a chapter.
     */
    fun toggleBookmark() {
        val s = _state.value.session ?: return
        val index = _state.value.pageIndex
        val isBookmarked = index in _state.value.bookmarkedPages
        viewModelScope.launch {
            val outcome = if (isBookmarked) {
                libraryRepository.removeBookmark(s.chapterId, s.seriesId, s.volumeId, index)
            } else {
                libraryRepository.addBookmark(s.chapterId, s.seriesId, s.volumeId, index)
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
