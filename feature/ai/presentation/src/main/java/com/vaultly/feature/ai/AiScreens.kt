package com.vaultly.feature.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.vaultly.core.data.ai.LocalAiOrchestrator
import com.vaultly.core.designsystem.components.VaultlyCard
import com.vaultly.core.domain.ai.AiToolExecutor
import com.vaultly.core.domain.ai.AiToolResult
import com.vaultly.core.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

@Serializable data object AiRoute

data class ChatMessage(val id: String, val fromUser: Boolean, val text: String)

data class AiState(
    val input: String = "",
    val messages: List<ChatMessage> = listOf(
        ChatMessage(
            "welcome",
            false,
            "Ask Vaultly to search, generate passwords, check health, or create notes. You can also dictate notes from the Notes tab mic."
        )
    ),
    val thinking: Boolean = false,
    val pending: AiToolResult.PendingConfirmation? = null
)

class AiViewModel(
    private val orchestrator: LocalAiOrchestrator,
    private val toolExecutor: AiToolExecutor,
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _state = MutableStateFlow(AiState())
    val state = _state.asStateFlow()

    fun onInput(value: String) = _state.update { it.copy(input = value) }

    fun send() {
        val text = _state.value.input.trim()
        if (text.isBlank() || _state.value.thinking) return
        viewModelScope.launch {
            authRepository.recordActivity()
            _state.update {
                it.copy(
                    input = "",
                    thinking = true,
                    messages = it.messages + ChatMessage(System.currentTimeMillis().toString(), true, text)
                )
            }
            val result = orchestrator.handle(text)
            when (result) {
                is AiToolResult.PendingConfirmation -> _state.update {
                    it.copy(thinking = false, pending = result)
                }
                else -> _state.update {
                    it.copy(
                        thinking = false,
                        messages = it.messages + ChatMessage(
                            (System.currentTimeMillis() + 1).toString(),
                            false,
                            result.toDisplay()
                        )
                    )
                }
            }
        }
    }

    fun confirmPending() {
        val pending = _state.value.pending ?: return
        viewModelScope.launch {
            val result = toolExecutor.confirm(pending.toolName, pending.payload)
            _state.update {
                it.copy(
                    pending = null,
                    messages = it.messages + ChatMessage(
                        System.currentTimeMillis().toString(),
                        false,
                        result.toDisplay()
                    )
                )
            }
        }
    }

    fun cancelPending() = _state.update { it.copy(pending = null) }
}

private fun AiToolResult.toDisplay(): String = when (this) {
    is AiToolResult.Text -> message
    is AiToolResult.PasswordFound ->
        "I found your ${title} account.\n\nUsername:\n$username\n\nPassword:\n$maskedPassword"
    is AiToolResult.GeneratedPassword ->
        "I've generated a strong password:\n\n$password" +
            if (suggestedTitle.isNullOrBlank()) "\n\nWould you like me to save it? Ask: create password for ${suggestedTitle ?: "this account"}."
            else "\n\nSuggested for: $suggestedTitle"
    is AiToolResult.HealthSummary -> message
    is AiToolResult.PendingConfirmation -> summary
    is AiToolResult.Error -> message
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiRoot(viewModel: AiViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("AI Assistant") }) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.messages, key = { it.id }) { msg ->
                    VaultlyCard {
                        Text(
                            if (msg.fromUser) "You" else "Vaultly",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(msg.text)
                    }
                }
                if (state.thinking) {
                    item {
                        Text("🤖 Vaultly is thinking...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.input,
                    onValueChange = viewModel::onInput,
                    modifier = Modifier.weight(1f),
                    label = { Text("What do you need?") },
                    singleLine = true
                )
                Button(onClick = viewModel::send) { Text("Ask") }
            }
        }
    }
    state.pending?.let { pending ->
        AlertDialog(
            onDismissRequest = viewModel::cancelPending,
            title = { Text(if (pending.toolName.startsWith("delete")) "Delete Item?" else "Confirm Action") },
            text = { Text(pending.summary) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmPending) {
                    Text(if (pending.toolName.startsWith("delete")) "Delete" else "Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelPending) { Text("Cancel") }
            }
        )
    }
}

fun NavGraphBuilder.aiGraph() {
    composable<AiRoute> { AiRoot() }
}

val aiPresentationModule = module {
    viewModelOf(::AiViewModel)
}
