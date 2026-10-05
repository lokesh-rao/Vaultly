package com.vaultly.core.domain.model

data class VaultHealth(
    val score: Int,
    val rating: String,
    val totalPasswords: Int,
    val strongCount: Int,
    val mediumCount: Int,
    val weakCount: Int,
    val reusedCount: Int,
    val oldCount: Int,
    val recommendations: List<VaultRecommendation>
)

data class VaultRecommendation(
    val id: String,
    val severity: Severity,
    val message: String,
    val itemIds: List<String>
) {
    enum class Severity { HIGH, MEDIUM, LOW }
}

sealed interface VaultSearchResult {
    data class Password(val entry: PasswordEntry) : VaultSearchResult
    data class Note(val note: SecureNote) : VaultSearchResult
}
