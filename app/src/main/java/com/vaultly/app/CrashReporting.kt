package com.vaultly.app

import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Sanitized Crashlytics helpers — never log vault secrets.
 */
object CrashReporting {
    fun setNonPiiKeys(vaultCreated: Boolean, theme: String) {
        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.setCustomKey("vault_created", vaultCreated)
        crashlytics.setCustomKey("theme", theme)
        // Explicitly do not set user identifiers tied to vault contents.
    }

    fun recordNonFatal(throwable: Throwable) {
        // Message may be logged by Crashlytics; ensure callers never pass secrets.
        FirebaseCrashlytics.getInstance().recordException(throwable)
    }
}
