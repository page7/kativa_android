package com.kavita.reader.ui.me

import androidx.lifecycle.ViewModel
import com.kavita.reader.core.auth.ActiveServerStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class MeUiState(
    val username: String = "",
    val serverName: String = "",
    val isAdmin: Boolean = false,
    val isReadOnly: Boolean = false,
)

@HiltViewModel
class MeViewModel @Inject constructor(
    activeServerStore: ActiveServerStore,
) : ViewModel() {

    private val _state = MutableStateFlow(MeUiState())
    val state: StateFlow<MeUiState> = _state.asStateFlow()

    init {
        val session = activeServerStore.session.value
        val server = activeServerStore.active.value
        _state.update {
            it.copy(
                username = session?.username.orEmpty(),
                serverName = server?.displayName.orEmpty(),
                isAdmin = session?.isAdmin == true,
                isReadOnly = session?.isReadOnly == true,
            )
        }
    }
}
