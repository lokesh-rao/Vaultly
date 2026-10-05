package com.vaultly.core.domain.password

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import com.vaultly.core.domain.model.PasswordCategory
import com.vaultly.core.domain.model.PasswordEntry
import org.junit.jupiter.api.Test

class VaultHealthCalculatorTest {
    @Test
    fun `empty vault scores excellent`() {
        val health = VaultHealthCalculator.calculate(emptyList())
        assertThat(health.score).isEqualTo(100)
        assertThat(health.rating).isEqualTo("Excellent")
    }

    @Test
    fun `detects reused passwords`() {
        val now = System.currentTimeMillis()
        val entries = listOf(
            entry("1", "a", "same", now),
            entry("2", "b", "same", now)
        )
        val health = VaultHealthCalculator.calculate(entries, now)
        assertThat(health.reusedCount).isGreaterThan(0)
    }

    private fun entry(id: String, title: String, password: String, now: Long) = PasswordEntry(
        id = id,
        title = title,
        username = "u",
        password = password,
        url = "",
        notes = "",
        category = PasswordCategory.OTHER,
        tags = emptyList(),
        favorite = false,
        createdAtEpochMs = now,
        updatedAtEpochMs = now
    )
}
