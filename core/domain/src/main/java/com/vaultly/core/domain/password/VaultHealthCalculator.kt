package com.vaultly.core.domain.password

import com.vaultly.core.domain.model.PasswordEntry
import com.vaultly.core.domain.model.PasswordStrength
import com.vaultly.core.domain.model.VaultHealth
import com.vaultly.core.domain.model.VaultRecommendation

object VaultHealthCalculator {
    private const val OLD_THRESHOLD_MS = 180L * 24 * 60 * 60 * 1000

    fun calculate(passwords: List<PasswordEntry>, nowMs: Long = System.currentTimeMillis()): VaultHealth {
        if (passwords.isEmpty()) {
            return VaultHealth(
                score = 100,
                rating = "Excellent",
                totalPasswords = 0,
                strongCount = 0,
                mediumCount = 0,
                weakCount = 0,
                reusedCount = 0,
                oldCount = 0,
                recommendations = emptyList()
            )
        }

        val passwordValues = passwords.map { it.password }
        val reports = passwords.associateWith { entry ->
            evaluateAgainstOthers(entry.password, passwordValues)
        }

        val strong = reports.values.count {
            it.strength == PasswordStrength.STRONG || it.strength == PasswordStrength.VERY_STRONG
        }
        val medium = reports.values.count { it.strength == PasswordStrength.MEDIUM }
        val weak = reports.values.count {
            it.strength == PasswordStrength.WEAK || it.strength == PasswordStrength.VERY_WEAK
        }

        val byPassword = passwords.groupBy { it.password }
        val reusedIds = byPassword.filter { it.value.size > 1 }.values.flatten().map { it.id }
        val reusedCount = reusedIds.distinct().size

        val oldEntries = passwords.filter { nowMs - it.updatedAtEpochMs > OLD_THRESHOLD_MS }
        val recommendations = buildList {
            if (weak > 0) {
                add(
                    VaultRecommendation(
                        id = "weak",
                        severity = VaultRecommendation.Severity.HIGH,
                        message = "$weak weak password${if (weak == 1) "" else "s"}",
                        itemIds = reports.filter {
                            it.value.strength == PasswordStrength.WEAK ||
                                it.value.strength == PasswordStrength.VERY_WEAK
                        }.keys.map { it.id }
                    )
                )
            }
            if (reusedCount > 0) {
                add(
                    VaultRecommendation(
                        id = "reused",
                        severity = VaultRecommendation.Severity.MEDIUM,
                        message = "$reusedCount reused password${if (reusedCount == 1) "" else "s"}",
                        itemIds = reusedIds.distinct()
                    )
                )
            }
            if (oldEntries.isNotEmpty()) {
                add(
                    VaultRecommendation(
                        id = "old",
                        severity = VaultRecommendation.Severity.LOW,
                        message = "${oldEntries.size} outdated password${if (oldEntries.size == 1) "" else "s"}",
                        itemIds = oldEntries.map { it.id }
                    )
                )
            }
        }

        val score = ((strong * 100 + medium * 60 + (passwords.size - strong - medium - weak).coerceAtLeast(0) * 40) /
            passwords.size.coerceAtLeast(1))
            .coerceIn(0, 100)
            .let { base ->
                var s = base
                if (reusedCount > 0) s -= (reusedCount * 5).coerceAtMost(20)
                if (weak > 0) s -= (weak * 8).coerceAtMost(30)
                s.coerceIn(0, 100)
            }

        val rating = when {
            score >= 90 -> "Excellent"
            score >= 75 -> "Good"
            score >= 50 -> "Fair"
            else -> "Needs attention"
        }

        return VaultHealth(
            score = score,
            rating = rating,
            totalPasswords = passwords.size,
            strongCount = strong,
            mediumCount = medium,
            weakCount = weak,
            reusedCount = reusedCount,
            oldCount = oldEntries.size,
            recommendations = recommendations
        )
    }

    private fun evaluateAgainstOthers(password: String, all: List<String>) =
        PasswordStrengthEvaluator.evaluate(
            password = password,
            otherPasswords = all.filter { it == password }.drop(1)
        )
}
