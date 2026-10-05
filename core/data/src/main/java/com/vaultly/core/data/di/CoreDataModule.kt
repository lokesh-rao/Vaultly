package com.vaultly.core.data.di

import androidx.room.Room
import com.vaultly.core.data.ai.LocalAiOrchestrator
import com.vaultly.core.data.ai.LocalAiToolExecutor
import com.vaultly.core.data.auth.LocalAuthRepository
import com.vaultly.core.data.preferences.VaultPreferences
import com.vaultly.core.data.vault.LocalVaultRepository
import com.vaultly.core.database.VaultlyDatabase
import com.vaultly.core.domain.ai.AiToolExecutor
import com.vaultly.core.domain.repository.AuthRepository
import com.vaultly.core.domain.repository.VaultRepository
import com.vaultly.core.security.biometric.BiometricKeyManager
import com.vaultly.core.security.crypto.SessionKeyStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val coreDataModule = module {
    single { VaultPreferences(androidContext()) }
    single { SessionKeyStore() }
    single { BiometricKeyManager(androidContext()) }
    single {
        Room.databaseBuilder(
            androidContext(),
            VaultlyDatabase::class.java,
            "vaultly.db"
        ).build()
    }
    single { get<VaultlyDatabase>().passwordDao() }
    single { get<VaultlyDatabase>().noteDao() }
    singleOf(::LocalAuthRepository) { bind<AuthRepository>() }
    singleOf(::LocalVaultRepository) { bind<VaultRepository>() }
    singleOf(::LocalAiToolExecutor) { bind<AiToolExecutor>() }
    singleOf(::LocalAiOrchestrator)
}
