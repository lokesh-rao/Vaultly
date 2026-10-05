package com.vaultly.feature.auth.di

import com.vaultly.feature.auth.AuthViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val authPresentationModule = module {
    viewModelOf(::AuthViewModel)
}
