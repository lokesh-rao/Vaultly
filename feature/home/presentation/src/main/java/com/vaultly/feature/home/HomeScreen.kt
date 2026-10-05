package com.vaultly.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaultly.core.designsystem.components.VaultlyCard
import com.vaultly.core.presentation.ObserveAsEvents
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomeRoot(
    onOpenVault: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenHealth: () -> Unit,
    onOpenAi: () -> Unit,
    viewModel: HomeViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) {
        when (it) {
            HomeEvent.OpenVault, is HomeEvent.OpenSearch -> onOpenVault()
            HomeEvent.OpenNotes -> onOpenNotes()
            HomeEvent.OpenHealth -> onOpenHealth()
            HomeEvent.OpenAi -> onOpenAi()
        }
    }
    HomeScreen(state = state, onAction = viewModel::onAction)
}

@Composable
fun HomeScreen(state: HomeState, onAction: (HomeAction) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("${state.greeting}", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { onAction(HomeAction.OnSearchChange(it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search your vault") },
            singleLine = true
        )
        VaultlyCard(modifier = Modifier.clickable { onAction(HomeAction.OnPasswordsClick) }) {
            Text("Passwords", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text("${state.passwordCount} items", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        VaultlyCard(modifier = Modifier.clickable { onAction(HomeAction.OnNotesClick) }) {
            Text("Secure Notes", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text("${state.noteCount} notes", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        VaultlyCard(modifier = Modifier.clickable { onAction(HomeAction.OnHealthClick) }) {
            Text("Vault Health", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text("${state.healthScore}% — ${state.healthRating}")
        }
        VaultlyCard(modifier = Modifier.clickable { onAction(HomeAction.OnAskAiClick) }) {
            Text("Ask Vaultly", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text("What do you need?", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
