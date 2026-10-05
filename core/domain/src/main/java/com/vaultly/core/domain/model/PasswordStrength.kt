package com.vaultly.core.domain.model

enum class PasswordStrength {
    VERY_WEAK,
    WEAK,
    MEDIUM,
    STRONG,
    VERY_STRONG
}

data class PasswordStrengthReport(
    val strength: PasswordStrength,
    val issues: List<String>
)

data class PasswordGeneratorConfig(
    val length: Int = 20,
    val uppercase: Boolean = true,
    val lowercase: Boolean = true,
    val numbers: Boolean = true,
    val symbols: Boolean = true,
    val excludeAmbiguous: Boolean = true
)
