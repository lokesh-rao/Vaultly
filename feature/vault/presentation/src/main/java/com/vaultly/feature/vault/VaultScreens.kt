package com.vaultly.feature.vault

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.vaultly.core.designsystem.components.EmptyState
import com.vaultly.core.designsystem.components.PasswordStrengthBar
import com.vaultly.core.designsystem.components.VaultlyCard
import com.vaultly.core.domain.model.PasswordCategory
import com.vaultly.core.domain.model.PasswordEntry
import com.vaultly.core.domain.model.PasswordGeneratorConfig
import com.vaultly.core.domain.model.PasswordStrength
import com.vaultly.core.domain.password.PasswordGenerator
import com.vaultly.core.domain.password.PasswordStrengthEvaluator
import com.vaultly.core.domain.repository.AuthRepository
import com.vaultly.core.domain.repository.VaultRepository
import com.vaultly.core.presentation.ClipboardHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import java.util.UUID

@Serializable data object VaultListRoute
@Serializable data class PasswordEditorRoute(val id: String = "")
@Serializable data object VaultHealthRoute

data class VaultListState(
    val items: List<PasswordEntry> = emptyList(),
    val query: String = "",
    val showFavoritesOnly: Boolean = false
)

class VaultListViewModel(
    private val vaultRepository: VaultRepository,
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _state = MutableStateFlow(VaultListState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            vaultRepository.observePasswords().collect { list ->
                authRepository.recordActivity()
                _state.update { it.copy(items = list) }
            }
        }
    }

    fun onQuery(query: String) = _state.update { it.copy(query = query) }
    fun toggleFavorites() = _state.update { it.copy(showFavoritesOnly = !it.showFavoritesOnly) }
    fun filtered(): List<PasswordEntry> {
        val q = _state.value.query.trim().lowercase()
        return _state.value.items.filter {
            (! _state.value.showFavoritesOnly || it.favorite) &&
                (q.isBlank() || it.title.lowercase().contains(q) || it.username.lowercase().contains(q) ||
                    it.url.lowercase().contains(q) || it.tags.any { t -> t.lowercase().contains(q) })
        }
    }
}

data class PasswordEditorState(
    val id: String = "",
    val title: String = "",
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val notes: String = "",
    val category: PasswordCategory = PasswordCategory.OTHER,
    val tags: String = "",
    val favorite: Boolean = false,
    val revealed: Boolean = false,
    val strengthLabel: String = "Very Weak",
    val strengthProgress: Float = 0f,
    val message: String? = null
)

class PasswordEditorViewModel(
    private val vaultRepository: VaultRepository,
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _state = MutableStateFlow(PasswordEditorState())
    val state = _state.asStateFlow()

    fun load(id: String) {
        if (id.isBlank()) return
        viewModelScope.launch {
            vaultRepository.getPassword(id).let { result ->
                val entry = (result as? com.vaultly.core.domain.util.Result.Success)?.data ?: return@launch
                _state.update {
                    it.copy(
                        id = entry.id,
                        title = entry.title,
                        username = entry.username,
                        password = entry.password,
                        url = entry.url,
                        notes = entry.notes,
                        category = entry.category,
                        tags = entry.tags.joinToString(", "),
                        favorite = entry.favorite
                    )
                }
                updateStrength(entry.password)
            }
        }
    }

    fun update(transform: (PasswordEditorState) -> PasswordEditorState) {
        _state.update(transform)
        updateStrength(_state.value.password)
    }

    fun generate() {
        val password = PasswordGenerator.generate(PasswordGeneratorConfig(length = 20))
        _state.update { it.copy(password = password, revealed = true) }
        updateStrength(password)
    }

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            authRepository.recordActivity()
            val s = _state.value
            val now = System.currentTimeMillis()
            val entry = PasswordEntry(
                id = s.id.ifBlank { UUID.randomUUID().toString() },
                title = s.title.ifBlank { "Untitled" },
                username = s.username,
                password = s.password,
                url = s.url,
                notes = s.notes,
                category = s.category,
                tags = s.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                favorite = s.favorite,
                createdAtEpochMs = now,
                updatedAtEpochMs = now
            )
            vaultRepository.upsertPassword(entry)
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            if (_state.value.id.isNotBlank()) vaultRepository.deletePassword(_state.value.id)
            onDone()
        }
    }

    private fun updateStrength(password: String) {
        val report = PasswordStrengthEvaluator.evaluate(password)
        val progress = when (report.strength) {
            PasswordStrength.VERY_WEAK -> 0.15f
            PasswordStrength.WEAK -> 0.35f
            PasswordStrength.MEDIUM -> 0.55f
            PasswordStrength.STRONG -> 0.75f
            PasswordStrength.VERY_STRONG -> 1f
        }
        _state.update {
            it.copy(
                strengthLabel = report.strength.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase),
                strengthProgress = progress
            )
        }
    }
}

data class HealthUiState(
    val score: Int = 100,
    val rating: String = "Excellent",
    val total: Int = 0,
    val strong: Int = 0,
    val medium: Int = 0,
    val weak: Int = 0,
    val reused: Int = 0,
    val recommendations: List<String> = emptyList()
)

class VaultHealthViewModel(vaultRepository: VaultRepository) : ViewModel() {
    private val _state = MutableStateFlow(HealthUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            vaultRepository.observeVaultHealth().collect { h ->
                _state.value = HealthUiState(
                    score = h.score,
                    rating = h.rating,
                    total = h.totalPasswords,
                    strong = h.strongCount,
                    medium = h.mediumCount,
                    weak = h.weakCount,
                    reused = h.reusedCount,
                    recommendations = h.recommendations.map { it.message }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultListRoot(navController: NavController, viewModel: VaultListViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val items = viewModel.filtered()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vault") },
                actions = {
                    TextButton(onClick = viewModel::toggleFavorites) {
                        Text(if (state.showFavoritesOnly) "All" else "Favorites")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(PasswordEditorRoute()) }) {
                Icon(Icons.Default.Add, contentDescription = "Add password")
            }
        }
    ) { padding ->
        Column {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQuery,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search") },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (items.isEmpty()) {
                EmptyState(
                    title = "No passwords yet",
                    message = "Add your first password to start building your secure vault.",
                    actionLabel = "Add Password",
                    onAction = { navController.navigate(PasswordEditorRoute()) }
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items, key = { it.id }) { entry ->
                        VaultlyCard {
                            Text(entry.title, style = MaterialTheme.typography.titleMedium)
                            Text(entry.username.ifBlank { entry.url }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordEditorScreen(
    id: String,
    onDone: () -> Unit,
    viewModel: PasswordEditorViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(id) { viewModel.load(id) }

    Scaffold(topBar = { TopAppBar(title = { Text(if (id.isBlank()) "New Password" else state.title.ifBlank { "Edit Password" }) }) }) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(state.title, { v -> viewModel.update { it.copy(title = v) } }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(state.username, { v -> viewModel.update { it.copy(username = v) } }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = state.password,
                onValueChange = { v -> viewModel.update { it.copy(password = v) } },
                label = { Text("Password") },
                visualTransformation = if (state.revealed) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { viewModel.update { it.copy(revealed = !it.revealed) } }) {
                        Icon(
                            if (state.revealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (state.revealed) "Hide password" else "Reveal password"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            PasswordStrengthBar(state.strengthLabel, state.strengthProgress)
            TextButton(onClick = viewModel::generate) { Text("Generate Password") }
            OutlinedTextField(state.url, { v -> viewModel.update { it.copy(url = v) } }, label = { Text("Website URL") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(state.notes, { v -> viewModel.update { it.copy(notes = v) } }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(state.tags, { v -> viewModel.update { it.copy(tags = v) } }, label = { Text("Tags (comma separated)") }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.update { it.copy(favorite = !it.favorite) } }) {
                    Icon(
                        if (state.favorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Favorite"
                    )
                }
                Text(if (state.favorite) "Favorited" else "Mark favorite")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    ClipboardHelper.copyWithTimeout(context, "username", state.username, 30, scope)
                }) { Text("Copy username") }
                TextButton(onClick = {
                    ClipboardHelper.copyWithTimeout(context, "password", state.password, 30, scope)
                }) { Text("Copy password") }
                if (state.url.isNotBlank()) {
                    TextButton(onClick = {
                        val uri = if (state.url.startsWith("http")) state.url else "https://${state.url}"
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
                    }) { Text("Open") }
                }
            }
            androidx.compose.material3.Button(onClick = { viewModel.save(onDone) }, modifier = Modifier.fillMaxWidth()) {
                Text("Save")
            }
            if (id.isNotBlank()) {
                TextButton(onClick = { viewModel.delete(onDone) }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultHealthRoot(viewModel: VaultHealthViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("Vault Health") }) }) { padding ->
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${state.score}%", style = MaterialTheme.typography.displaySmall)
            Text(state.rating, style = MaterialTheme.typography.titleLarge)
            Text("${state.total} Passwords")
            Text("${state.strong} Strong · ${state.medium} Medium · ${state.weak} Weak · ${state.reused} Reused")
            Text("Recommendations", style = MaterialTheme.typography.titleMedium)
            if (state.recommendations.isEmpty()) {
                Text("You're in good shape.")
            } else {
                state.recommendations.forEach { Text("• $it") }
            }
        }
    }
}

fun NavGraphBuilder.vaultGraph(navController: NavController) {
    composable<VaultListRoute> { VaultListRoot(navController) }
    composable<PasswordEditorRoute> { entry ->
        val route = entry.toRoute<PasswordEditorRoute>()
        PasswordEditorScreen(id = route.id, onDone = { navController.popBackStack() })
    }
    composable<VaultHealthRoute> { VaultHealthRoot() }
}

val vaultPresentationModule = module {
    viewModelOf(::VaultListViewModel)
    viewModelOf(::PasswordEditorViewModel)
    viewModelOf(::VaultHealthViewModel)
}
