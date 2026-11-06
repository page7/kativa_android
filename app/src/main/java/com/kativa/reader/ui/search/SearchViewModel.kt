package com.kativa.reader.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kativa.reader.core.auth.ActiveServerStore
import com.kativa.reader.core.network.KativaError
import com.kativa.reader.core.network.Outcome
import com.kativa.reader.data.repository.SearchHit
import com.kativa.reader.data.repository.SearchRepository
import com.kativa.reader.data.repository.SearchResults
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    val results: SearchResults? = null,
    val error: KativaError? = null,
    /** True once a query of sufficient length has actually been executed. */
    val hasSearched: Boolean = false,
) {
    val isEmptyResult: Boolean
        get() = hasSearched && !loading && error == null && results?.isEmpty == true

    val belowMinLength: Boolean
        get() = query.isNotBlank() && query.trim().length < SearchRepository.MIN_QUERY_LENGTH
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val activeServerStore: ActiveServerStore,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    /** Recent queries are per-session; persisting them is a later concern. */
    private val _history = MutableStateFlow<List<String>>(emptyList())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value, error = null) }
        searchJob?.cancel()

        // A blank query is the only thing Kavita's search rejects; never send one.
        if (value.isBlank() || value.trim().length < SearchRepository.MIN_QUERY_LENGTH) {
            _state.update { it.copy(results = null, loading = false, hasSearched = false) }
            return
        }

        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            execute(value)
        }
    }

    fun submit() {
        searchJob?.cancel()
        val query = _state.value.query
        if (query.isBlank() || query.trim().length < SearchRepository.MIN_QUERY_LENGTH) return
        searchJob = viewModelScope.launch { execute(query) }
    }

    fun selectHistory(entry: String) {
        _state.update { it.copy(query = entry) }
        submit()
    }

    fun removeHistory(entry: String) {
        _history.update { it.filterNot { h -> h == entry } }
    }

    fun clearHistory() {
        _history.value = emptyList()
    }

    fun retry() = submit()

    private suspend fun execute(rawQuery: String) {
        val query = rawQuery.trim()
        _state.update { it.copy(loading = true, error = null) }

        when (val outcome = searchRepository.search(query)) {
            is Outcome.Success -> {
                // Drop stale responses: a slower earlier request must never overwrite the results
                // of a newer query, and `searchJob.cancel()` alone cannot stop one that has
                // already returned from the server.
                if (_state.value.query.trim() != query) return
                _state.update {
                    it.copy(
                        loading = false,
                        results = outcome.value,
                        hasSearched = true,
                        error = null,
                    )
                }
                rememberQuery(query)
            }

            is Outcome.Failure -> {
                if (_state.value.query.trim() != query) return
                _state.update {
                    it.copy(loading = false, error = outcome.failure.error, hasSearched = true)
                }
            }
        }
    }

    private fun rememberQuery(query: String) {
        _history.update { current ->
            (listOf(query) + current.filterNot { it.equals(query, ignoreCase = true) }).take(MAX_HISTORY)
        }
    }

    /** Cover URLs are already built server-side; privacy mode is applied by the UI layer. */
    fun coverFor(hit: SearchHit): String? = hit.coverUrl

    fun imageKey(): String? = activeServerStore.session.value?.imageKey

    private companion object {
        const val DEBOUNCE_MS = 300L
        const val MAX_HISTORY = 10
    }
}
