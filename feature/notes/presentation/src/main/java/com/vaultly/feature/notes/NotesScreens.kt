package com.vaultly.feature.notes

import android.Manifest
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.vaultly.core.designsystem.components.EmptyState
import com.vaultly.core.designsystem.components.VaultlyCard
import com.vaultly.core.domain.model.NoteCategory
import com.vaultly.core.domain.model.SecureNote
import com.vaultly.core.domain.repository.AuthRepository
import com.vaultly.core.domain.repository.VaultRepository
import com.vaultly.core.domain.util.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import java.util.UUID

@Serializable data object NotesListRoute
@Serializable data class NoteEditorRoute(val id: String = "")

data class NotesListState(val notes: List<SecureNote> = emptyList(), val query: String = "")

class NotesListViewModel(
    vaultRepository: VaultRepository,
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _state = MutableStateFlow(NotesListState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            vaultRepository.observeNotes().collect { notes ->
                authRepository.recordActivity()
                _state.update { it.copy(notes = notes.filter { n -> !n.archived }) }
            }
        }
    }

    fun onQuery(q: String) = _state.update { it.copy(query = q) }
    fun filtered() = _state.value.notes.filter {
        val q = _state.value.query.trim().lowercase()
        q.isBlank() || it.title.lowercase().contains(q) || it.content.lowercase().contains(q)
    }
}

data class NoteEditorState(
    val id: String = "",
    val title: String = "",
    val content: String = "",
    val favorite: Boolean = false,
    val pinned: Boolean = false,
    val isListening: Boolean = false,
    val voiceStatus: String? = null
)

class NoteEditorViewModel(
    private val vaultRepository: VaultRepository,
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _state = MutableStateFlow(NoteEditorState())
    val state = _state.asStateFlow()

    fun load(id: String, voiceDraft: String = "") {
        if (voiceDraft.isNotBlank()) {
            _state.update {
                it.copy(
                    title = deriveTitle(voiceDraft),
                    content = voiceDraft,
                    voiceStatus = "Voice draft ready — edit and save."
                )
            }
        }
        if (id.isBlank()) return
        viewModelScope.launch {
            when (val result = vaultRepository.getNote(id)) {
                is Result.Success -> _state.value = NoteEditorState(
                    id = result.data.id,
                    title = result.data.title,
                    content = result.data.content,
                    favorite = result.data.favorite,
                    pinned = result.data.pinned
                )
                is Result.Error -> Unit
            }
        }
    }

    fun update(transform: (NoteEditorState) -> NoteEditorState) = _state.update(transform)

    fun appendVoiceTranscript(transcript: String) {
        _state.update { current ->
            val merged = if (current.content.isBlank()) transcript
            else "${current.content.trim()}\n$transcript"
            current.copy(
                content = merged,
                title = current.title.ifBlank { deriveTitle(transcript) },
                isListening = false,
                voiceStatus = "Added from voice."
            )
        }
    }

    fun setListening(listening: Boolean, status: String? = null) {
        _state.update { it.copy(isListening = listening, voiceStatus = status) }
    }

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        authRepository.recordActivity()
        val s = _state.value
        val now = System.currentTimeMillis()
        vaultRepository.upsertNote(
            SecureNote(
                id = s.id.ifBlank { UUID.randomUUID().toString() },
                title = s.title.ifBlank { "Untitled" },
                content = s.content,
                category = NoteCategory.PERSONAL,
                tags = emptyList(),
                favorite = s.favorite,
                pinned = s.pinned,
                archived = false,
                createdAtEpochMs = now,
                updatedAtEpochMs = now
            )
        )
        onDone()
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        if (_state.value.id.isNotBlank()) vaultRepository.deleteNote(_state.value.id)
        onDone()
    }

    private fun deriveTitle(text: String): String {
        val firstLine = text.lineSequence().firstOrNull()?.trim().orEmpty()
        return firstLine.take(48).ifBlank { "Voice note" }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListRoot(navController: NavController, viewModel: NotesListViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notes = viewModel.filtered()
    val context = LocalContext.current
    val activity = context as? Activity
    val voiceHelper = remember { VoiceNoteHelper(context) }
    var pendingVoiceCreate by remember { mutableStateOf(false) }
    var listVoiceError by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingVoiceCreate) {
            pendingVoiceCreate = false
            activity?.let { act ->
                voiceHelper.startListening(
                    activity = act,
                    onResult = { text ->
                        VoiceDraftStore.put(text)
                        navController.navigate(NoteEditorRoute())
                    },
                    onError = { listVoiceError = it },
                    onListeningChanged = {}
                )
            }
        } else if (!granted) {
            listVoiceError = "Microphone permission is required for voice notes."
            pendingVoiceCreate = false
        }
    }

    DisposableEffect(Unit) {
        onDispose { voiceHelper.stop() }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Notes") }) },
        floatingActionButton = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.End) {
                FloatingActionButton(
                    onClick = {
                        listVoiceError = null
                        if (!voiceHelper.hasAudioPermission()) {
                            pendingVoiceCreate = true
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            activity?.let { act ->
                                voiceHelper.startListening(
                                    activity = act,
                                    onResult = { text ->
                                        VoiceDraftStore.put(text)
                                        navController.navigate(NoteEditorRoute())
                                    },
                                    onError = { listVoiceError = it },
                                    onListeningChanged = {}
                                )
                            }
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "Create note with voice")
                }
                FloatingActionButton(onClick = { navController.navigate(NoteEditorRoute()) }) {
                    Icon(Icons.Default.Add, contentDescription = "Create note")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQuery,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search notes") },
                singleLine = true
            )
            listVoiceError?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(12.dp))
            if (notes.isEmpty()) {
                EmptyState(
                    title = "No secure notes yet",
                    message = "Type a note or tap the mic to dictate one with your voice.",
                    actionLabel = "Create Note",
                    onAction = { navController.navigate(NoteEditorRoute()) }
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(notes, key = { it.id }) { note ->
                        VaultlyCard {
                            Text(note.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                note.content.take(80),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorRoot(
    id: String,
    onDone: () -> Unit,
    viewModel: NoteEditorViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity
    val voiceHelper = remember { VoiceNoteHelper(context) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceCapture(activity, voiceHelper, viewModel)
        } else {
            viewModel.setListening(false, "Microphone permission is required for voice notes.")
        }
    }

    LaunchedEffect(id) {
        viewModel.load(id, VoiceDraftStore.consume().orEmpty())
    }
    DisposableEffect(Unit) {
        onDispose { voiceHelper.stop() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (id.isBlank()) "New Note" else "Edit Note") },
                actions = {
                    IconButton(
                        onClick = {
                            if (state.isListening) {
                                voiceHelper.stop()
                                viewModel.setListening(false, "Listening stopped.")
                            } else if (!voiceHelper.hasAudioPermission()) {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                startVoiceCapture(activity, voiceHelper, viewModel)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (state.isListening) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (state.isListening) "Stop voice input" else "Dictate note"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                state.title,
                { v -> viewModel.update { it.copy(title = v) } },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                state.content,
                { v -> viewModel.update { it.copy(content = v) } },
                label = { Text(if (state.isListening) "Listening…" else "Content") },
                modifier = Modifier.fillMaxWidth().height(220.dp),
                supportingText = {
                    Text(
                        when {
                            state.isListening -> "Speak clearly — text appears as you talk."
                            state.voiceStatus != null -> state.voiceStatus!!
                            else -> "Tap the mic to dictate. Voice stays in Vaultly."
                        }
                    )
                }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { viewModel.update { it.copy(favorite = !it.favorite) } }) {
                    Text(if (state.favorite) "Unfavorite" else "Favorite")
                }
                TextButton(onClick = { viewModel.update { it.copy(pinned = !it.pinned) } }) {
                    Text(if (state.pinned) "Unpin" else "Pin")
                }
                TextButton(
                    onClick = {
                        if (state.isListening) {
                            voiceHelper.stop()
                            viewModel.setListening(false, "Listening stopped.")
                        } else if (!voiceHelper.hasAudioPermission()) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            startVoiceCapture(activity, voiceHelper, viewModel)
                        }
                    }
                ) {
                    Text(if (state.isListening) "Stop voice" else "Voice assist")
                }
            }
            androidx.compose.material3.Button(
                onClick = { viewModel.save(onDone) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save") }
            if (id.isNotBlank()) {
                TextButton(onClick = { viewModel.delete(onDone) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private fun startVoiceCapture(
    activity: Activity?,
    voiceHelper: VoiceNoteHelper,
    viewModel: NoteEditorViewModel
) {
    if (activity == null) {
        viewModel.setListening(false, "Voice notes unavailable.")
        return
    }
    viewModel.setListening(true, "Listening…")
    voiceHelper.startListening(
        activity = activity,
        onPartial = { partial ->
            viewModel.update { current ->
                current.copy(
                    content = if (current.content.isBlank()) partial
                    else {
                        val base = current.content.substringBeforeLast('\n').let { head ->
                            if (current.content.contains('\n')) "$head\n$partial" else partial
                        }
                        // Keep appending partials simply when empty; otherwise show live line.
                        if (current.content.isBlank()) partial else current.content
                    },
                    voiceStatus = "Hearing: $partial"
                )
            }
        },
        onResult = { text -> viewModel.appendVoiceTranscript(text) },
        onError = { err -> viewModel.setListening(false, err) },
        onListeningChanged = { listening ->
            if (!listening) viewModel.update { it.copy(isListening = false) }
            else viewModel.setListening(true, "Listening…")
        }
    )
}

fun NavGraphBuilder.notesGraph(navController: NavController) {
    composable<NotesListRoute> { NotesListRoot(navController) }
    composable<NoteEditorRoute> {
        val route = it.toRoute<NoteEditorRoute>()
        NoteEditorRoot(
            id = route.id,
            onDone = { navController.popBackStack() }
        )
    }
}

val notesPresentationModule = module {
    viewModelOf(::NotesListViewModel)
    viewModelOf(::NoteEditorViewModel)
}
