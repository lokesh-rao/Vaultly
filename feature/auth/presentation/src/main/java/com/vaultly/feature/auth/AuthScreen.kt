package com.vaultly.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaultly.core.presentation.ObserveAsEvents
import org.koin.androidx.compose.koinViewModel

@Composable
fun AuthRoot(
    onUnlocked: () -> Unit,
    onRequestBiometric: () -> Unit,
    viewModel: AuthViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            AuthEvent.NavigatedToHome -> onUnlocked()
            AuthEvent.RequestBiometric -> onRequestBiometric()
        }
    }
    AuthScreen(state = state, onAction = viewModel::onAction)
}

@Composable
fun AuthScreen(
    state: AuthState,
    onAction: (AuthAction) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            if (state.vaultExists) "Unlock Vaultly" else "Welcome to Vaultly",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (state.vaultExists) "Enter your master password" else "Create your Master Password",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = state.masterPassword,
            onValueChange = { onAction(AuthAction.OnMasterPasswordChange(it)) },
            label = { Text("Master Password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (!state.vaultExists) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.confirmPassword,
                onValueChange = { onAction(AuthAction.OnConfirmPasswordChange(it)) },
                label = { Text("Confirm Master Password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        state.error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(24.dp))
        if (state.isLoading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    onAction(if (state.vaultExists) AuthAction.OnUnlock else AuthAction.OnCreateVault)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.vaultExists) "Unlock" else "Create Vault")
            }
            if (state.vaultExists && state.biometricAvailable) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { onAction(AuthAction.OnBiometricUnlockReady) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Unlock with biometrics") }
            }
        }
    }
}
