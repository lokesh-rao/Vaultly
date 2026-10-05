package com.vaultly.core.security.crypto

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoEngine {
    private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val CIPHER = "AES/GCM/NoPadding"
    private const val KEY_BITS = 256
    private const val ITERATIONS = 120_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    fun generateSalt(): ByteArray = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }

    fun deriveKey(masterPassword: CharArray, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance(KDF_ALGORITHM)
        val spec = PBEKeySpec(masterPassword, salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    fun encrypt(plainText: String, key: SecretKey): String {
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = iv + encrypted
        return Base64.getEncoder().encodeToString(combined)
    }

    fun decrypt(cipherText: String, key: SecretKey): String {
        val combined = Base64.getDecoder().decode(cipherText)
        require(combined.size > IV_BYTES) { "Invalid ciphertext" }
        val iv = combined.copyOfRange(0, IV_BYTES)
        val encrypted = combined.copyOfRange(IV_BYTES, combined.size)
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    fun verifyPassword(masterPassword: CharArray, salt: ByteArray, verificationCipher: String): Boolean {
        return try {
            val key = deriveKey(masterPassword, salt)
            decrypt(verificationCipher, key) == "vaultly-ok"
        } catch (_: Exception) {
            false
        }
    }

    fun createVerificationBlob(key: SecretKey): String = encrypt("vaultly-ok", key)

    fun toBase64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    fun fromBase64(value: String): ByteArray = Base64.getDecoder().decode(value)
}
