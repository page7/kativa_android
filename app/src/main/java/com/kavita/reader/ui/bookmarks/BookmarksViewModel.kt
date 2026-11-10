package com.kavita.reader.ui.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavita.reader.core.image.CoverUrlBuilder
import com.kavita.reader.core.image.PrivacyModeState
import com.kavita.reader.core.network.KavitaError
import com.kavita.reader.core.network.Outcome
import com.kavita.reader.data.repository.LibraryRepository
import com.kavita.reader.domain.model.Bookmark
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One bookmark plus the resolved thumbnail URL. */
data class BookmarkItem(
    val bookmark: Bookmark,
    val thumbnailUrl: String?,
)

data class BookmarksUiState(
    val loading: Boolean = true,
    /** Pull-to-refresh in flight; the grid stays visible. */
    val refreshing: Boolean = false,
    val items: List<BookmarkItem> = emptyList(),
    val error: KavitaError? = null,
    val privacyMode: Boolean = false,
) {
    val isEmpty: Boolean get() = !loading && error == null && items.isEmpty()
}

/**
 * "My bookmarks" (docs/FLOWS.md F11 → 我的 → 书签).
 *
 * The server returns every bookmark in one shot (`POST Reader/all-bookmarks` ignores paging), so
 * this screen does no paging: it maps the list to cards and lets the grid scroll.
 */
@HiltViewModel
class BookmarksViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val coverUrlBuilder: CoverUrlBuilder,
    private val privacyModeState: PrivacyModeState,
) : ViewModel() {

    private val _state = MutableStateFlow(BookmarksUiState())
    val state: StateFlow<BookmarksUiState> = _state.asStateFlow()

    init {
        observePrivacy()
    }

    /**
     * Reloads whenever the screen is resumed.
     *
     * The reader is a separate destination: the user can add/remove bookmarks there and come
     * back here. `init` only ever ran once, so the list went stale (it even kept showing a
     * bookmark that had just been deleted). Resuming is the correct trigger — it also covers the
     * very first entry, which is why there is no separate initial load.
     *
     * The spinner is skipped when a list is already on screen: the cards stay put while the
     * refresh lands, instead of flashing an empty loading state on every return.
     */
    fun onResumed() = load(showSpinner = _state.value.items.isEmpty())

    private fun observePrivacy() {
        viewModelScope.launch {
            privacyModeState.enabled.collect { enabled ->
                _state.update { it.copy(privacyMode = enabled) }
            }
        }
    }

    fun setPrivacyMode(enabled: Boolean) {
        privacyModeState.toggleForActiveServer(enabled)
        _state.update { it.copy(privacyMode = enabled) }
    }

    fun load(showSpinner: Boolean = true) {
        _state.update { it.copy(loading = showSpinner, error = null) }
        viewModelScope.launch {
            when (val outcome = libraryRepository.allBookmarks()) {
                is Outcome.Success -> _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        items = outcome.value.map { bookmark ->
                            BookmarkItem(
                                bookmark = bookmark,
                                thumbnailUrl = coverUrlBuilder.bookmarkPage(
                                    chapterId = bookmark.chapterId,
                                    pageNum = bookmark.pageIndex,
                                    imageOffset = bookmark.imageOffset,
                                ),
                            )
                        },
                    )
                }

                is Outcome.Failure -> _state.update {
                    it.copy(loading = false, refreshing = false, error = outcome.failure.error)
                }
            }
        }
    }

    /** Pull-to-refresh: reloads in place so the grid does not flash an empty loading state. */
    fun refresh() {
        if (_state.value.refreshing || _state.value.loading) return
        _state.update { it.copy(refreshing = true, error = null) }
        load(showSpinner = false)
    }
}
