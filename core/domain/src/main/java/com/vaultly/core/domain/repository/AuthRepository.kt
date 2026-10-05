package com.vaultly.core.domain.repository

import com.vaultly.core.domain.auth.AuthError
import com.vaultly.core.domain.auth.AutoLockTimeout
import com.vaultly.core.domain.auth.SecuritySettings
import com.vaultly.core.domain.auth.ThemePreference
import com.vaultly.core.domain.util.EmptyResult
import com.vaultly.core.domain.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val isUnlocked: StateFlow<Boolean>
    val vaultExists: Flow<Boolean>
    val themePreference: Flow<ThemePreference>
    val securitySettings: Flow<SecuritySettings>

    suspend fun createVault(masterPassword: String, confirmPassword: String): EmptyResult<AuthError>
    suspend fun unlock(masterPassword: String): EmptyResult<AuthError>
    suspend fun unlockWithBiometric(): EmptyResult<AuthError>
    fun lock()
    suspend fun enableBiometric(enabled: Boolean): EmptyResult<AuthError>
    suspend fun updateAutoLockTimeout(timeout: AutoLockTimeout): EmptyResult<AuthError>
    suspend fun updateClipboardTimeout(seconds: Int): EmptyResult<AuthError>
    suspend fun updateScreenshotProtection(enabled: Boolean): EmptyResult<AuthError>
    suspend fun updateTheme(theme: ThemePreference): EmptyResult<AuthError>
    fun isBiometricAvailable(): Boolean
    fun recordActivity()
    fun checkAutoLock()
}
