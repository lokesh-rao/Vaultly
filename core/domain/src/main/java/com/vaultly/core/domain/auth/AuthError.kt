package com.vaultly.core.domain.auth

enum class AuthError : com.vaultly.core.domain.util.Error {
    INVALID_MASTER_PASSWORD,
    PASSWORDS_DO_NOT_MATCH,
    PASSWORD_TOO_SHORT,
    VAULT_LOCKED,
    VAULT_NOT_CREATED,
    BIOMETRIC_UNAVAILABLE,
    BIOMETRIC_FAILED,
    UNKNOWN
}
