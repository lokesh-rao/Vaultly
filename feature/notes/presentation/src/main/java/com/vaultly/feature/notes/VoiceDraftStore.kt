package com.vaultly.feature.notes

/**
 * Holds a one-shot voice transcript between Notes list → editor without
 * stuffing large text into navigation arguments.
 */
object VoiceDraftStore {
    @Volatile
    private var pending: String? = null

    fun put(text: String) {
        pending = text
    }

    fun consume(): String? {
        val value = pending
        pending = null
        return value
    }
}
