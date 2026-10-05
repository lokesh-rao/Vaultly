package com.vaultly.app

import android.app.Application
import com.vaultly.core.data.di.coreDataModule
import com.vaultly.feature.ai.aiPresentationModule
import com.vaultly.feature.auth.di.authPresentationModule
import com.vaultly.feature.home.homePresentationModule
import com.vaultly.feature.notes.notesPresentationModule
import com.vaultly.feature.settings.settingsPresentationModule
import com.vaultly.feature.vault.vaultPresentationModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class VaultlyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@VaultlyApp)
            modules(
                coreDataModule,
                authPresentationModule,
                homePresentationModule,
                vaultPresentationModule,
                notesPresentationModule,
                aiPresentationModule,
                settingsPresentationModule
            )
        }
    }
}
