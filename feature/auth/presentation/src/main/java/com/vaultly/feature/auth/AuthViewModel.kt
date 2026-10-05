package com.vaultly.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultly.core.data.auth.LocalAuthRepository
import com.vaultly.core.domain.auth.AuthError
import com.vaultly.core.domain.repository.AuthRepository
import com.vaultly.core.domain.util.onFailure
import com.vaultly.core.domain.util.onSuccess
import com.vaultly.core.security.biometric.BiometricKeyManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthState(
    val vaultExists: Boolean = false,
    val masterPassword: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val biometricAvailable: Boolean = false
)

sealed interface AuthAction {
    data class OnMasterPasswordChange(val value: String) : AuthAction
    data class OnConfirmPasswordChange(val value: String) : AuthAction
    data object OnCreateVault : AuthAction
    data object OnUnlock : AuthAction
    data object OnBiometricUnlockReady : AuthAction
    data object ClearError : AuthAction
}

sealed interface AuthEvent {
    data object NavigatedToHome : AuthEvent
    data object RequestBiometric : AuthEvent
}

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val biometricKeyManager: BiometricKeyManager
) : ViewModel() {
    private val _state = MutableStateFlow(AuthState())
    val state = _state.asStateFlow()
    private val _events = Channel<AuthEvent>()
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            authRepository.vaultExists.collect { exists ->
                _state.update {
                    it.copy(
                        vaultExists = exists,
                        biometricAvailable = exists && authRepository.isBiometricAvailable()
                    )
                }
            }
        }
    }

    fun onAction(action: AuthAction) {
        when (action) {
            is AuthAction.OnMasterPasswordChange -> _state.update { it.copy(masterPassword = action.value, error = null) }
            is AuthAction.OnConfirmPasswordChange -> _state.update { it.copy(confirmPassword = action.value, error = null) }
            AuthAction.OnCreateVault -> createVault()
            AuthAction.OnUnlock -> unlock()
            AuthAction.OnBiometricUnlockReady -> viewModelScope.launch { _events.send(AuthEvent.RequestBiometric) }
            AuthAction.ClearError -> _state.update { it.copy(error = null) }
        }
    }

    private fun createVault() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            authRepository.createVault(_state.value.masterPassword, _state.value.confirmPassword)
                .onSuccess {
                    _state.update { it.copy(isLoading = false) }
                    _events.send(AuthEvent.NavigatedToHome)
                }
                .onFailure { error ->
                    _state.update { it.copy(isLoading = false, error = error.toMessage()) }
                }
        }
    }

    private fun unlock() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            authRepository.unlock(_state.value.masterPassword)
                .onSuccess {
                    _state.update { it.copy(isLoading = false) }
                    _events.send(AuthEvent.NavigatedToHome)
                }
                .onFailure { error ->
                    _state.update { it.copy(isLoading = false, error = error.toMessage()) }
                }
        }
    }

    fun onBiometricUnlocked(dekBytes: ByteArray) {
        val local = authRepository as? LocalAuthRepository ?: return
        local.setSessionFromDek(dekBytes)
        viewModelScope.launch { _events.send(AuthEvent.NavigatedToHome) }
    }

    fun wrapDekWithCipher(cipher: javax.crypto.Cipher) {
        val local = authRepository as? LocalAuthRepository ?: return
        val dek = local.currentDekBytes() ?: return
        biometricKeyManager.wrapKey(cipher, dek)
    }
}

private fun AuthError.toMessage(): String = when (this) {
    AuthError.INVALID_MASTER_PASSWORD -> "Incorrect master password."
    AuthError.PASSWORDS_DO_NOT_MATCH -> "Passwords do not match."
    AuthError.PASSWORD_TOO_SHORT -> "Master password must be at least 8 characters."
    AuthError.VAULT_LOCKED -> "Vault is locked."
    AuthError.VAULT_NOT_CREATED -> "Create a vault first."
    AuthError.BIOMETRIC_UNAVAILABLE -> "Biometrics unavailable."
    AuthError.BIOMETRIC_FAILED -> "Biometric authentication failed."
    AuthError.UNKNOWN -> "Something went wrong. Your data is safe."
}
