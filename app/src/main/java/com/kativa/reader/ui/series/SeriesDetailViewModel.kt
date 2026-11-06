package com.kativa.reader.ui.series

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kativa.reader.core.network.KativaError
import com.kativa.reader.core.network.Outcome
import com.kativa.reader.data.repository.LibraryRepository
import com.kativa.reader.domain.model.Chapter
import com.kativa.reader.domain.model.SeriesDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SeriesUiState(
    val loading: Boolean = true,
    val detail: SeriesDetail? = null,
    val coverUrl: String? = null,
    val error: KativaError? = null,
    val expandedVolumeIds: Set<Int> = emptySet(),
    /** Chapter the primary button should open; null until resolved. */
    val resumeChapterId: Int? = null,
    val hasProgress: Boolean = false,
    val message: String? = null,
    val busy: Boolean = false,
    val inWantToRead: Boolean = false,
) {
    val primaryActionIsStart: Boolean get() = !hasProgress
    val primaryActionIsReread: Boolean get() = detail?.isComplete == true
}

@HiltViewModel
class SeriesDetailViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val shelfRepository: com.kativa.reader.data.repository.ShelfRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val seriesId: Int = savedStateHandle.get<String>("seriesId")?.toIntOrNull() ?: 0

    private val _state = MutableStateFlow(SeriesUiState())
    val state: StateFlow<SeriesUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val outcome = libraryRepository.seriesDetail(seriesId)) {
                is Outcome.Success -> {
                    val detail = outcome.value

                    // Progress/continue-point are enhancements. If either fails (e.g. a read-only
                    // or restricted account returns 400/404) the detail page must still render
                    // rather than showing a validation error.
                    val hasProgress = runCatching { libraryRepository.hasProgress(seriesId) }
                        .getOrDefault(false)
                    val resume = if (hasProgress) {
                        (libraryRepository.continuePoint(seriesId) as? Outcome.Success)?.value?.id
                    } else {
                        null
                    }
                    val inWantToRead = runCatching { shelfRepository.isInWantToRead(seriesId) }
                        .getOrDefault(false)

                    _state.update {
                        it.copy(
                            loading = false,
                            detail = detail,
                            coverUrl = libraryRepository.coverUrl(seriesId),
                            // Newest volume expanded by default; keeps long series scannable.
                            expandedVolumeIds = it.expandedVolumeIds.ifEmpty {
                                detail.volumes.takeLast(1).map { v -> v.id }.toSet()
                            },
                            hasProgress = hasProgress,
                            resumeChapterId = resume ?: detail.chapters.firstOrNull()?.id,
                            inWantToRead = inWantToRead,
                        )
                    }
                }

                is Outcome.Failure -> _state.update {
                    it.copy(loading = false, error = outcome.failure.error)
                }
            }
        }
    }

    fun toggleVolume(volumeId: Int) {
        _state.update {
            val next = it.expandedVolumeIds.toMutableSet()
            if (!next.add(volumeId)) next.remove(volumeId)
            it.copy(expandedVolumeIds = next)
        }
    }

    fun markChapterRead(chapter: Chapter, read: Boolean) {
        val detail = _state.value.detail ?: return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val outcome = if (read) {
                libraryRepository.markChapterRead(detail.seriesId, chapter.id)
            } else {
                libraryRepository.markChapterUnread(detail.seriesId, chapter.id)
            }
            _state.update { it.copy(busy = false) }
            if (outcome is Outcome.Failure) {
                _state.update { it.copy(message = outcome.failure.error.message) }
            } else {
                load()
            }
        }
    }

    fun markVolumeRead(volumeId: Int) {
        val detail = _state.value.detail ?: return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val outcome = libraryRepository.markVolumeRead(detail.seriesId, volumeId)
            _state.update { it.copy(busy = false) }
            if (outcome is Outcome.Success) load()
        }
    }

    fun markSeriesRead() {
        val detail = _state.value.detail ?: return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val outcome = libraryRepository.markSeriesRead(detail.seriesId)
            _state.update { it.copy(busy = false) }
            if (outcome is Outcome.Success) load()
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    /** Adds/removes the series from Want To Read and reflects the new state immediately. */
    fun toggleWantToRead() {
        val target = !_state.value.inWantToRead
        _state.update { it.copy(inWantToRead = target) }
        viewModelScope.launch {
            val outcome = shelfRepository.toggleWantToRead(seriesId, !target)
            if (outcome is Outcome.Failure) {
                // Roll back so the UI never claims a state the server rejected.
                _state.update { it.copy(inWantToRead = !target) }
            }
        }
    }
}
