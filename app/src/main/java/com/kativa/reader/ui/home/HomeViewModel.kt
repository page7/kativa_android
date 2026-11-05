package com.kativa.reader.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kativa.reader.core.auth.ActiveServerStore
import com.kativa.reader.core.image.CoverUrlBuilder
import com.kativa.reader.core.network.KativaError
import com.kativa.reader.core.network.Outcome
import com.kativa.reader.core.settings.SettingsRepository
import com.kativa.reader.data.remote.dto.DashboardStreamDto
import com.kativa.reader.data.remote.dto.GroupedSeriesDto
import com.kativa.reader.data.remote.dto.SeriesDto
import com.kativa.reader.data.repository.SeriesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One rendered row on the home screen. */
sealed interface HomeStream {
    val key: String
    val titleRes: Int

    data class ContinueReading(val series: SeriesDto) : HomeStream {
        override val key = "continue"
        override val titleRes = com.kativa.reader.R.string.home_continue_title
    }

    data class OnDeck(val items: List<SeriesDto>) : HomeStream {
        override val key = "on-deck"
        override val titleRes = com.kativa.reader.R.string.home_stream_on_deck
    }

    data class RecentlyUpdated(val items: List<GroupedSeriesDto>) : HomeStream {
        override val key = "recently-updated"
        override val titleRes = com.kativa.reader.R.string.home_stream_recently_updated
    }

    data class RecentlyAdded(val items: List<SeriesDto>) : HomeStream {
        override val key = "recently-added"
        override val titleRes = com.kativa.reader.R.string.home_stream_recently_added
    }
}

data class HomeItem(
    val seriesId: Int,
    val title: String,
    val coverUrl: String?,
    val subtitle: String? = null,
    val progress: Float? = null,
    val badge: String? = null,
)

data class HomeUiState(
    val loading: Boolean = true,
    val username: String = "",
    val privacyMode: Boolean = false,
    val streams: List<HomeStream> = emptyList(),
    val error: KativaError? = null,
    val onDeckCount: Int = 0,
    val totalSeries: Int = 0,
) {
    val isEmpty: Boolean get() = !loading && error == null && streams.isEmpty()
    val continueItem: HomeStream.ContinueReading?
        get() = streams.filterIsInstance<HomeStream.ContinueReading>().firstOrNull()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val seriesRepository: SeriesRepository,
    private val coverUrlBuilder: CoverUrlBuilder,
    private val activeServerStore: ActiveServerStore,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val serverId: String? get() = activeServerStore.activeId

    init {
        _state.update { it.copy(username = activeServerStore.session.value?.username.orEmpty()) }
        observePrivacy()
        load()
    }

    private fun observePrivacy() {
        viewModelScope.launch {
            val id = serverId
            if (id == null) {
                settingsRepository.isPrivacyMode(null).collect { enabled ->
                    _state.update { it.copy(privacyMode = enabled) }
                }
            } else {
                settingsRepository.isPrivacyMode(id).collect { enabled ->
                    _state.update { it.copy(privacyMode = enabled) }
                }
            }
        }
    }

    fun setPrivacyMode(enabled: Boolean) {
        val id = serverId ?: return
        _state.update { it.copy(privacyMode = enabled) }
        viewModelScope.launch { settingsRepository.setPrivacyMode(id, enabled) }
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }

        viewModelScope.launch {
            coroutineScope {
                // The three provided streams are fetched in parallel so one slow stream
                // cannot block the others (docs/FEATURES.md F-03).
                val onDeck = async { seriesRepository.onDeck(pageSize = 20) }
                val updated = async { seriesRepository.recentlyUpdated(pageSize = 20) }
                val added = async { seriesRepository.recentlyAdded(pageSize = 20) }

                val onDeckResult = onDeck.await()
                val updatedResult = updated.await()
                val addedResult = added.await()

                val streams = buildList {
                    (onDeckResult as? Outcome.Success)?.value?.firstOrNull()?.let {
                        add(HomeStream.ContinueReading(it))
                    }
                    (onDeckResult as? Outcome.Success)?.value?.takeIf { it.isNotEmpty() }?.let {
                        add(HomeStream.OnDeck(it))
                    }
                    (updatedResult as? Outcome.Success)?.value?.takeIf { it.isNotEmpty() }?.let {
                        add(HomeStream.RecentlyUpdated(it))
                    }
                    (addedResult as? Outcome.Success)?.value?.takeIf { it.isNotEmpty() }?.let {
                        add(HomeStream.RecentlyAdded(it))
                    }
                }

                val failures = listOf(onDeckResult, updatedResult, addedResult)
                    .filterIsInstance<Outcome.Failure>()

                _state.update {
                    it.copy(
                        loading = false,
                        streams = streams,
                        error = if (streams.isEmpty()) failures.firstOrNull()?.failure?.error else null,
                        onDeckCount = (onDeckResult as? Outcome.Success)?.value?.size ?: 0,
                    )
                }
            }
        }
    }

    fun seriesCover(seriesId: Int): String? =
        if (_state.value.privacyMode) null else coverUrlBuilder.series(seriesId)
}
