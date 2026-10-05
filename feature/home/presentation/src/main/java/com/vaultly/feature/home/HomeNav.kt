package com.vaultly.feature.home

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

@Serializable
data object HomeRoute

fun NavGraphBuilder.homeGraph(
    onOpenVault: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenHealth: () -> Unit,
    onOpenAi: () -> Unit
) {
    composable<HomeRoute> {
        HomeRoot(
            onOpenVault = onOpenVault,
            onOpenNotes = onOpenNotes,
            onOpenHealth = onOpenHealth,
            onOpenAi = onOpenAi
        )
    }
}

val homePresentationModule = module {
    viewModelOf(::HomeViewModel)
}
