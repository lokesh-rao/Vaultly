package com.vaultly.core.data.auth

import com.vaultly.core.data.preferences.VaultPreferences
import com.vaultly.core.domain.auth.AuthError
import com.vaultly.core.domain.auth.AutoLockTimeout
import com.vaultly.core.domain.auth.SecuritySettings
import com.vaultly.core.domain.auth.ThemePreference
import com.vaultly.core.domain.repository.AuthRepository
import com.vaultly.core.domain.util.EmptyResult
import com.vaultly.core.domain.util.Result
import com.vaultly.core.security.biometric.BiometricKeyManager
import com.vaultly.core.security.crypto.CryptoEngine
import com.vaultly.core.security.crypto.SessionKeyStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.crypto.spec.SecretKeySpec

class LocalAuthRepository(
    private val preferences: VaultPreferences,
    private val sessionKeyStore: SessionKeyStore,
    private val biometricKeyManager: BiometricKeyManager
) : AuthRepository {

    private val _isUnlocked = MutableStateFlow(false)
    override val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()
    override val vaultExists: Flow<Boolean> = preferences.vaultExists
    override val themePreference: Flow<ThemePreference> = preferences.themePreference
    override val securitySettings: Flow<SecuritySettings> = preferences.securitySettings

    @Volatile
    private var lastActivityMs: Long = System.currentTimeMillis()

    override suspend fun createVault(masterPassword: String, confirmPassword: String): EmptyResult<AuthError> {
        if (masterPassword.length < 8) return Result.Error(AuthError.PASSWORD_TOO_SHORT)
        if (masterPassword != confirmPassword) return Result.Error(AuthError.PASSWORDS_DO_NOT_MATCH)
        return try {
            val salt = CryptoEngine.generateSalt()
            val key = CryptoEngine.deriveKey(masterPassword.toCharArray(), salt)
            val verification = CryptoEngine.createVerificationBlob(key)
            preferences.saveVaultSecrets(CryptoEngine.toBase64(salt), verification)
            sessionKeyStore.set(key)
            _isUnlocked.value = true
            recordActivity()
            Result.Success(Unit)
        } catch (_: Exception) {
            Result.Error(AuthError.UNKNOWN)
        }
    }

    override suspend fun unlock(masterPassword: String): EmptyResult<AuthError> {
        val snap = preferences.snapshot()
        if (!snap.vaultCreated || snap.salt == null || snap.verification == null) {
            return Result.Error(AuthError.VAULT_NOT_CREATED)
        }
        return try {
            val salt = CryptoEngine.fromBase64(snap.salt)
            val chars = masterPassword.toCharArray()
            if (!CryptoEngine.verifyPassword(chars, salt, snap.verification)) {
                return Result.Error(AuthError.INVALID_MASTER_PASSWORD)
            }
            val key = CryptoEngine.deriveKey(chars, salt)
            sessionKeyStore.set(key)
            _isUnlocked.value = true
            recordActivity()
            Result.Success(Unit)
        } catch (_: Exception) {
            Result.Error(AuthError.UNKNOWN)
        }
    }

    override suspend fun unlockWithBiometric(): EmptyResult<AuthError> {
        if (!biometricKeyManager.isAvailable() || !biometricKeyManager.hasWrappedKey()) {
            return Result.Error(AuthError.BIOMETRIC_UNAVAILABLE)
        }
        // Actual biometric cipher prompt is handled in presentation; this path is for
        // after successful biometric auth unwrapped the DEK bytes into session via setSessionFromDek.
        return if (sessionKeyStore.isUnlocked()) {
            _isUnlocked.value = true
            recordActivity()
            Result.Success(Unit)
        } else {
            Result.Error(AuthError.BIOMETRIC_FAILED)
        }
    }

    fun setSessionFromDek(dekBytes: ByteArray) {
        sessionKeyStore.set(SecretKeySpec(dekBytes, "AES"))
        _isUnlocked.value = true
        recordActivity()
    }

    fun currentDekBytes(): ByteArray? = sessionKeyStore.get()?.encoded

    override fun lock() {
        sessionKeyStore.clear()
        _isUnlocked.value = false
    }

    override suspend fun enableBiometric(enabled: Boolean): EmptyResult<AuthError> {
        if (enabled && !biometricKeyManager.isAvailable()) {
            return Result.Error(AuthError.BIOMETRIC_UNAVAILABLE)
        }
        if (!enabled) biometricKeyManager.clear()
        preferences.setBiometricEnabled(enabled)
        return Result.Success(Unit)
    }

    override suspend fun updateAutoLockTimeout(timeout: AutoLockTimeout): EmptyResult<AuthError> {
        preferences.setAutoLock(timeout)
        return Result.Success(Unit)
    }

    override suspend fun updateClipboardTimeout(seconds: Int): EmptyResult<AuthError> {
        preferences.setClipboardTimeout(seconds.coerceIn(10, 300))
        return Result.Success(Unit)
    }

    override suspend fun updateScreenshotProtection(enabled: Boolean): EmptyResult<AuthError> {
        preferences.setScreenshotProtection(enabled)
        return Result.Success(Unit)
    }

    override suspend fun updateTheme(theme: ThemePreference): EmptyResult<AuthError> {
        preferences.setTheme(theme)
        return Result.Success(Unit)
    }

    override fun isBiometricAvailable(): Boolean =
        biometricKeyManager.isAvailable() && biometricKeyManager.hasWrappedKey()

    override fun recordActivity() {
        lastActivityMs = System.currentTimeMillis()
    }

    override fun checkAutoLock() {
        if (!_isUnlocked.value) return
        val settings = runCatching {
            // Best-effort: callers should also observe settings. Use last known via blocking first is avoided;
            // presentation layer invokes with known timeout.
        }
        // Presentation AutoLockController drives this with settings.
    }

    fun shouldAutoLock(timeout: AutoLockTimeout): Boolean {
        if (!_isUnlocked.value) return false
        if (timeout == AutoLockTimeout.IMMEDIATE) return true
        val elapsed = System.currentTimeMillis() - lastActivityMs
        return elapsed >= timeout.minutes * 60_000L
    }
}
