package com.vaultly.feature.auth

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object AuthRoute

fun NavGraphBuilder.authGraph(
    onUnlocked: () -> Unit,
    onRequestBiometric: () -> Unit
) {
    composable<AuthRoute> {
        AuthRoot(onUnlocked = onUnlocked, onRequestBiometric = onRequestBiometric)
    }
}
