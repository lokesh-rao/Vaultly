package com.vaultly.core.domain.auth

enum class AutoLockTimeout(val minutes: Int) {
    IMMEDIATE(0),
    ONE_MINUTE(1),
    FIVE_MINUTES(5),
    FIFTEEN_MINUTES(15)
}

enum class ThemePreference {
    LIGHT,
    DARK,
    SYSTEM
}

data class SecuritySettings(
    val autoLockTimeout: AutoLockTimeout = AutoLockTimeout.ONE_MINUTE,
    val clipboardTimeoutSeconds: Int = 30,
    val biometricEnabled: Boolean = false,
    val screenshotProtection: Boolean = true
)
