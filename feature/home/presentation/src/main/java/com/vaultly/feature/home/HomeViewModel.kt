package com.vaultly.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultly.core.domain.repository.AuthRepository
import com.vaultly.core.domain.repository.VaultRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

data class HomeState(
    val greeting: String = "Hello",
    val passwordCount: Int = 0,
    val noteCount: Int = 0,
    val healthScore: Int = 100,
    val healthRating: String = "Excellent",
    val searchQuery: String = ""
)

sealed interface HomeAction {
    data class OnSearchChange(val value: String) : HomeAction
    data object OnPasswordsClick : HomeAction
    data object OnNotesClick : HomeAction
    data object OnHealthClick : HomeAction
    data object OnAskAiClick : HomeAction
}

sealed interface HomeEvent {
    data object OpenVault : HomeEvent
    data object OpenNotes : HomeEvent
    data object OpenHealth : HomeEvent
    data object OpenAi : HomeEvent
    data class OpenSearch(val query: String) : HomeEvent
}

class HomeViewModel(
    private val vaultRepository: VaultRepository,
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _state = MutableStateFlow(HomeState(greeting = greeting()))
    val state = _state.asStateFlow()
    private val _events = Channel<HomeEvent>()
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            combine(
                vaultRepository.observePasswords(),
                vaultRepository.observeNotes(),
                vaultRepository.observeVaultHealth()
            ) { passwords, notes, health ->
                Triple(passwords.size, notes.size, health)
            }.collect { (pw, notes, health) ->
                authRepository.recordActivity()
                _state.update {
                    it.copy(
                        passwordCount = pw,
                        noteCount = notes,
                        healthScore = health.score,
                        healthRating = health.rating
                    )
                }
            }
        }
    }

    fun onAction(action: HomeAction) {
        when (action) {
            is HomeAction.OnSearchChange -> _state.update { it.copy(searchQuery = action.value) }
            HomeAction.OnPasswordsClick -> send(HomeEvent.OpenVault)
            HomeAction.OnNotesClick -> send(HomeEvent.OpenNotes)
            HomeAction.OnHealthClick -> send(HomeEvent.OpenHealth)
            HomeAction.OnAskAiClick -> send(HomeEvent.OpenAi)
        }
    }

    private fun send(event: HomeEvent) = viewModelScope.launch { _events.send(event) }

    private fun greeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }
}
