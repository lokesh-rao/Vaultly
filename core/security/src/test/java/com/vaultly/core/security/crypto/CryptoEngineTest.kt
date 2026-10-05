package com.vaultly.core.security.crypto

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test

class CryptoEngineTest {
    @Test
    fun `encrypt then decrypt round trip`() {
        val salt = CryptoEngine.generateSalt()
        val key = CryptoEngine.deriveKey("master-password".toCharArray(), salt)
        val cipher = CryptoEngine.encrypt("secret-note", key)
        assertThat(CryptoEngine.decrypt(cipher, key)).isEqualTo("secret-note")
    }

    @Test
    fun `verification fails for wrong password`() {
        val salt = CryptoEngine.generateSalt()
        val key = CryptoEngine.deriveKey("correct-horse".toCharArray(), salt)
        val verification = CryptoEngine.createVerificationBlob(key)
        assertThat(CryptoEngine.verifyPassword("correct-horse".toCharArray(), salt, verification)).isTrue()
        assertThat(CryptoEngine.verifyPassword("wrong-password".toCharArray(), salt, verification)).isFalse()
    }
}
