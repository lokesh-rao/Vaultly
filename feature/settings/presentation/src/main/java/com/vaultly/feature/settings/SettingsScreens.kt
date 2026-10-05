package com.vaultly.feature.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.vaultly.core.domain.auth.AutoLockTimeout
import com.vaultly.core.domain.auth.SecuritySettings
import com.vaultly.core.domain.auth.ThemePreference
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

@Serializable data object SettingsRoute

data class SettingsState(
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val security: SecuritySettings = SecuritySettings(),
    val message: String? = null
)

class SettingsViewModel(
    private val authRepository: AuthRepository,
    private val vaultRepository: VaultRepository
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.themePreference.collect { theme ->
                _state.update { it.copy(theme = theme) }
            }
        }
        viewModelScope.launch {
            authRepository.securitySettings.collect { security ->
                _state.update { it.copy(security = security) }
            }
        }
    }

    fun setTheme(theme: ThemePreference) = viewModelScope.launch {
        authRepository.updateTheme(theme)
    }

    fun setAutoLock(timeout: AutoLockTimeout) = viewModelScope.launch {
        authRepository.updateAutoLockTimeout(timeout)
    }

    fun setClipboard(seconds: Int) = viewModelScope.launch {
        authRepository.updateClipboardTimeout(seconds)
    }

    fun setScreenshot(enabled: Boolean) = viewModelScope.launch {
        authRepository.updateScreenshotProtection(enabled)
    }

    fun setBiometric(enabled: Boolean) = viewModelScope.launch {
        authRepository.enableBiometric(enabled)
    }

    fun lock() = authRepository.lock()

    fun exportBackup(onBytes: (ByteArray) -> Unit) = viewModelScope.launch {
        when (val result = vaultRepository.exportEncryptedBackup()) {
            is Result.Success -> {
                onBytes(result.data)
                _state.update { it.copy(message = "Encrypted backup ready.") }
            }
            is Result.Error -> _state.update { it.copy(message = "Export failed. Your data is safe.") }
        }
    }

    fun importBackup(bytes: ByteArray, masterPassword: String) = viewModelScope.launch {
        when (vaultRepository.importEncryptedBackup(bytes, masterPassword)) {
            is Result.Success -> _state.update { it.copy(message = "Import complete.") }
            is Result.Error -> _state.update { it.copy(message = "Import failed. Backup may be corrupt or password incorrect.") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoot(
    onLocked: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        viewModel.exportBackup { bytes ->
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
        // For MVP, import uses empty master password prompt via re-unlock expectation; require unlock session.
        viewModel.importBackup(bytes, "")
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            Text(
                "Monochrome theme — black, white, and grey only.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ThemePreference.entries.forEach { theme ->
                RowSelectable(
                    selected = state.theme == theme,
                    label = when (theme) {
                        ThemePreference.LIGHT -> "Light (white / grey)"
                        ThemePreference.DARK -> "Dark (black / grey)"
                        ThemePreference.SYSTEM -> "System default"
                    },
                    onClick = { viewModel.setTheme(theme) }
                )
            }

            Text("Security", style = MaterialTheme.typography.titleMedium)
            Text("Auto-lock", style = MaterialTheme.typography.labelLarge)
            AutoLockTimeout.entries.forEach { timeout ->
                RowSelectable(
                    selected = state.security.autoLockTimeout == timeout,
                    label = when (timeout) {
                        AutoLockTimeout.IMMEDIATE -> "Immediately"
                        AutoLockTimeout.ONE_MINUTE -> "1 minute"
                        AutoLockTimeout.FIVE_MINUTES -> "5 minutes"
                        AutoLockTimeout.FIFTEEN_MINUTES -> "15 minutes"
                    },
                    onClick = { viewModel.setAutoLock(timeout) }
                )
            }
            Text("Clipboard timeout: ${state.security.clipboardTimeoutSeconds}s")
            TextButton(onClick = { viewModel.setClipboard(30) }) { Text("30 seconds (default)") }
            TextButton(onClick = { viewModel.setClipboard(60) }) { Text("60 seconds") }
            RowSwitch("Screenshot protection", state.security.screenshotProtection, viewModel::setScreenshot)
            RowSwitch("Biometric unlock", state.security.biometricEnabled, viewModel::setBiometric)

            Text("Vault", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { exportLauncher.launch("vaultly-backup.vault") }) {
                Text("Export encrypted backup")
            }
            TextButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                Text("Import encrypted backup")
            }
            TextButton(onClick = {
                viewModel.lock()
                onLocked()
            }) { Text("Lock vault now") }

            Text("About", style = MaterialTheme.typography.titleMedium)
            Text("Vaultly — AI-powered privacy vault")
            Text("Version 1.0.0", color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
private fun RowSelectable(selected: Boolean, label: String, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, modifier = Modifier.padding(start = 8.dp, top = 12.dp))
    }
}

@Composable
private fun RowSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

fun NavGraphBuilder.settingsGraph(onLocked: () -> Unit) {
    composable<SettingsRoute> { SettingsRoot(onLocked = onLocked) }
}

val settingsPresentationModule = module {
    viewModelOf(::SettingsViewModel)
}
