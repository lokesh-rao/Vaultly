package com.vaultly.core.domain.password

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isTrue
import com.vaultly.core.domain.model.PasswordGeneratorConfig
import org.junit.jupiter.api.Test

class PasswordGeneratorTest {
    @Test
    fun `generates password of requested length`() {
        val password = PasswordGenerator.generate(PasswordGeneratorConfig(length = 24))
        assertThat(password.length).isEqualTo(24)
    }

    @Test
    fun `includes required character classes`() {
        val password = PasswordGenerator.generate(
            PasswordGeneratorConfig(
                length = 32,
                uppercase = true,
                lowercase = true,
                numbers = true,
                symbols = true
            )
        )
        assertThat(password.any { it.isUpperCase() }).isTrue()
        assertThat(password.any { it.isLowerCase() }).isTrue()
        assertThat(password.any { it.isDigit() }).isTrue()
        assertThat(password.any { !it.isLetterOrDigit() }).isTrue()
        assertThat(password.length).isGreaterThanOrEqualTo(32)
    }
}
