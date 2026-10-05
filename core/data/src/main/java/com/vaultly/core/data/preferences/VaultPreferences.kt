package com.vaultly.core.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vaultly.core.domain.auth.AutoLockTimeout
import com.vaultly.core.domain.auth.SecuritySettings
import com.vaultly.core.domain.auth.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("vaultly_prefs")

class VaultPreferences(private val context: Context) {
    private val saltKey = stringPreferencesKey("salt")
    private val verificationKey = stringPreferencesKey("verification")
    private val vaultCreatedKey = booleanPreferencesKey("vault_created")
    private val themeKey = stringPreferencesKey("theme")
    private val autoLockKey = stringPreferencesKey("auto_lock")
    private val clipboardKey = intPreferencesKey("clipboard_timeout")
    private val biometricKey = booleanPreferencesKey("biometric_enabled")
    private val screenshotKey = booleanPreferencesKey("screenshot_protection")

    val vaultExists: Flow<Boolean> = context.dataStore.data.map { it[vaultCreatedKey] == true }

    val themePreference: Flow<ThemePreference> = context.dataStore.data.map {
        runCatching { ThemePreference.valueOf(it[themeKey] ?: ThemePreference.SYSTEM.name) }
            .getOrDefault(ThemePreference.SYSTEM)
    }

    val securitySettings: Flow<SecuritySettings> = context.dataStore.data.map { prefs ->
        SecuritySettings(
            autoLockTimeout = runCatching {
                AutoLockTimeout.valueOf(prefs[autoLockKey] ?: AutoLockTimeout.ONE_MINUTE.name)
            }.getOrDefault(AutoLockTimeout.ONE_MINUTE),
            clipboardTimeoutSeconds = prefs[clipboardKey] ?: 30,
            biometricEnabled = prefs[biometricKey] == true,
            screenshotProtection = prefs[screenshotKey] != false
        )
    }

    suspend fun saveVaultSecrets(saltBase64: String, verification: String) {
        context.dataStore.edit {
            it[saltKey] = saltBase64
            it[verificationKey] = verification
            it[vaultCreatedKey] = true
        }
    }

    suspend fun setTheme(theme: ThemePreference) {
        context.dataStore.edit { it[themeKey] = theme.name }
    }

    suspend fun setAutoLock(timeout: AutoLockTimeout) {
        context.dataStore.edit { it[autoLockKey] = timeout.name }
    }

    suspend fun setClipboardTimeout(seconds: Int) {
        context.dataStore.edit { it[clipboardKey] = seconds }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { it[biometricKey] = enabled }
    }

    suspend fun setScreenshotProtection(enabled: Boolean) {
        context.dataStore.edit { it[screenshotKey] = enabled }
    }

    suspend fun snapshot(): PrefsSnapshot {
        val prefs = context.dataStore.data.first()
        return PrefsSnapshot(
            salt = prefs[saltKey],
            verification = prefs[verificationKey],
            vaultCreated = prefs[vaultCreatedKey] == true,
            theme = prefs[themeKey] ?: ThemePreference.SYSTEM.name,
            autoLock = prefs[autoLockKey] ?: AutoLockTimeout.ONE_MINUTE.name,
            clipboardTimeout = prefs[clipboardKey] ?: 30,
            biometricEnabled = prefs[biometricKey] == true,
            screenshotProtection = prefs[screenshotKey] != false
        )
    }

    data class PrefsSnapshot(
        val salt: String? = null,
        val verification: String? = null,
        val vaultCreated: Boolean = false,
        val theme: String = ThemePreference.SYSTEM.name,
        val autoLock: String = AutoLockTimeout.ONE_MINUTE.name,
        val clipboardTimeout: Int = 30,
        val biometricEnabled: Boolean = false,
        val screenshotProtection: Boolean = true
    )
}
