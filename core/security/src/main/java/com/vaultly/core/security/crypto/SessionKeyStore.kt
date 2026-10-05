package com.vaultly.core.security.crypto

import javax.crypto.SecretKey

/**
 * In-memory data-encryption key. Cleared on lock / auto-lock.
 */
class SessionKeyStore {
    @Volatile
    private var key: SecretKey? = null

    fun set(key: SecretKey) {
        this.key = key
    }

    fun get(): SecretKey? = key

    fun clear() {
        key = null
    }

    fun isUnlocked(): Boolean = key != null
}
