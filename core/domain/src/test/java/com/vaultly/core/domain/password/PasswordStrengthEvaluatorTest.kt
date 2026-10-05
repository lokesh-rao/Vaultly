package com.vaultly.core.domain.password

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isTrue
import com.vaultly.core.domain.model.PasswordStrength
import org.junit.jupiter.api.Test

class PasswordStrengthEvaluatorTest {
    @Test
    fun `short common password is weak`() {
        val report = PasswordStrengthEvaluator.evaluate("password")
        assertThat(report.strength).isEqualTo(PasswordStrength.VERY_WEAK)
        assertThat(report.issues).isNotEmpty()
    }

    @Test
    fun `long diverse password is strong`() {
        val report = PasswordStrengthEvaluator.evaluate("x8\$Kp2!mQ9#vL7@zR4&nW")
        assertThat(
            report.strength == PasswordStrength.STRONG || report.strength == PasswordStrength.VERY_STRONG
        ).isTrue()
    }

    @Test
    fun `detects reuse`() {
        val report = PasswordStrengthEvaluator.evaluate("UniquePass1!", listOf("UniquePass1!"))
        assertThat(report.issues.any { it.contains("reuse", ignoreCase = true) }).isTrue()
    }
}
