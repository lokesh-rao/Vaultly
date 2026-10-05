package com.vaultly.core.security.biometric

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class BiometricKeyManager(private val context: Context) {
    private val androidKeyStore = "AndroidKeyStore"
    private val alias = "vaultly_biometric_wrap"
    private val prefs = context.getSharedPreferences("vaultly_biometric", Context.MODE_PRIVATE)

    fun isAvailable(): Boolean {
        val manager = BiometricManager.from(context)
        return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    fun hasWrappedKey(): Boolean = prefs.contains(WRAPPED_KEY) && prefs.contains(WRAPPED_IV)

    fun createCipherForEncryption(): Cipher {
        ensureKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        return cipher
    }

    fun createCipherForDecryption(): Cipher {
        val iv = Base64.decode(prefs.getString(WRAPPED_IV, null), Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), GCMParameterSpec(128, iv))
        return cipher
    }

    fun wrapKey(cipher: Cipher, dek: ByteArray) {
        val wrapped = cipher.doFinal(dek)
        prefs.edit()
            .putString(WRAPPED_KEY, Base64.encodeToString(wrapped, Base64.NO_WRAP))
            .putString(WRAPPED_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun unwrapKey(cipher: Cipher): ByteArray {
        val wrapped = Base64.decode(prefs.getString(WRAPPED_KEY, null), Base64.NO_WRAP)
        return cipher.doFinal(wrapped)
    }

    fun clear() {
        prefs.edit().clear().apply()
        runCatching {
            val ks = KeyStore.getInstance(androidKeyStore).apply { load(null) }
            if (ks.containsAlias(alias)) ks.deleteEntry(alias)
        }
    }

    private fun ensureKey() {
        val ks = KeyStore.getInstance(androidKeyStore).apply { load(null) }
        if (ks.containsAlias(alias)) return
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, androidKeyStore)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
                .build()
        )
        keyGenerator.generateKey()
    }

    private fun getSecretKey(): SecretKey {
        val ks = KeyStore.getInstance(androidKeyStore).apply { load(null) }
        return ks.getKey(alias, null) as SecretKey
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val WRAPPED_KEY = "wrapped_key"
        private const val WRAPPED_IV = "wrapped_iv"
    }
}
