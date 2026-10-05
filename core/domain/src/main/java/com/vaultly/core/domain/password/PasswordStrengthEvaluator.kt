package com.vaultly.core.domain.password

import com.vaultly.core.domain.model.PasswordStrength
import com.vaultly.core.domain.model.PasswordStrengthReport

object PasswordStrengthEvaluator {
    private val commonPatterns = listOf(
        "password", "123456", "qwerty", "admin", "welcome", "letmein", "abc123"
    )

    fun evaluate(password: String, otherPasswords: Collection<String> = emptyList()): PasswordStrengthReport {
        val issues = mutableListOf<String>()
        var score = 0

        when {
            password.length >= 20 -> score += 3
            password.length >= 14 -> score += 2
            password.length >= 10 -> score += 1
            else -> issues += "Short password"
        }

        val hasUpper = password.any { it.isUpperCase() }
        val hasLower = password.any { it.isLowerCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSymbol = password.any { !it.isLetterOrDigit() }
        val diversity = listOf(hasUpper, hasLower, hasDigit, hasSymbol).count { it }
        score += diversity
        if (diversity < 3) issues += "Insufficient character diversity"

        val lower = password.lowercase()
        if (commonPatterns.any { lower.contains(it) }) {
            score -= 2
            issues += "Common password pattern"
        }

        val reused = otherPasswords.count { it == password } > 0
        if (reused) {
            score -= 2
            issues += "Potential reuse"
        }

        val strength = when {
            score >= 7 -> PasswordStrength.VERY_STRONG
            score >= 5 -> PasswordStrength.STRONG
            score >= 3 -> PasswordStrength.MEDIUM
            score >= 1 -> PasswordStrength.WEAK
            else -> PasswordStrength.VERY_WEAK
        }
        return PasswordStrengthReport(strength = strength, issues = issues.distinct())
    }
}
