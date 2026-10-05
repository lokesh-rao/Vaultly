package com.vaultly.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vaultly.core.data.auth.LocalAuthRepository
import com.vaultly.core.designsystem.theme.VaultlyTheme
import com.vaultly.core.designsystem.theme.VaultlyThemeMode
import com.vaultly.core.domain.auth.AutoLockTimeout
import com.vaultly.core.domain.auth.ThemePreference
import com.vaultly.core.domain.repository.AuthRepository
import com.vaultly.core.security.biometric.BiometricKeyManager
import com.vaultly.feature.ai.AiRoute
import com.vaultly.feature.ai.aiGraph
import com.vaultly.feature.auth.AuthRoute
import com.vaultly.feature.auth.AuthViewModel
import com.vaultly.feature.auth.authGraph
import com.vaultly.feature.home.HomeRoute
import com.vaultly.feature.home.homeGraph
import com.vaultly.feature.notes.NotesListRoute
import com.vaultly.feature.notes.notesGraph
import com.vaultly.feature.settings.SettingsRoute
import com.vaultly.feature.settings.settingsGraph
import com.vaultly.feature.vault.VaultHealthRoute
import com.vaultly.feature.vault.VaultListRoute
import com.vaultly.feature.vault.vaultGraph
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel

class MainActivity : AppCompatActivity() {
    private val authRepository: AuthRepository by inject()
    private val biometricKeyManager: BiometricKeyManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val theme by authRepository.themePreference.collectAsStateWithLifecycle(
                initialValue = ThemePreference.SYSTEM
            )
            val security by authRepository.securitySettings.collectAsStateWithLifecycle(
                initialValue = com.vaultly.core.domain.auth.SecuritySettings()
            )
            val unlocked by authRepository.isUnlocked.collectAsStateWithLifecycle()

            LaunchedEffect(security.screenshotProtection) {
                if (security.screenshotProtection) {
                    window.setFlags(
                        WindowManager.LayoutParams.FLAG_SECURE,
                        WindowManager.LayoutParams.FLAG_SECURE
                    )
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            AutoLockEffect(
                unlocked = unlocked,
                timeout = security.autoLockTimeout,
                authRepository = authRepository as LocalAuthRepository
            )

            VaultlyTheme(
                themeMode = when (theme) {
                    ThemePreference.LIGHT -> VaultlyThemeMode.LIGHT
                    ThemePreference.DARK -> VaultlyThemeMode.DARK
                    ThemePreference.SYSTEM -> VaultlyThemeMode.SYSTEM
                }
            ) {
                VaultlyAppNav(
                    unlocked = unlocked,
                    onBiometricUnlock = { promptBiometricUnlock() },
                    onWrapBiometric = { promptBiometricWrap() },
                    biometricKeyManager = biometricKeyManager,
                    authRepository = authRepository as LocalAuthRepository
                )
            }
        }
    }

    private fun promptBiometricUnlock() {
        if (!biometricKeyManager.hasWrappedKey()) return
        val cipher = runCatching { biometricKeyManager.createCipherForDecryption() }.getOrNull() ?: return
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val crypto = result.cryptoObject?.cipher ?: return
                    val dek = runCatching { biometricKeyManager.unwrapKey(crypto) }.getOrNull() ?: return
                    (authRepository as LocalAuthRepository).setSessionFromDek(dek)
                }
            }
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Vaultly")
                .setSubtitle("Confirm biometrics")
                .setNegativeButtonText("Cancel")
                .build(),
            BiometricPrompt.CryptoObject(cipher)
        )
    }

    private fun promptBiometricWrap() {
        if (!biometricKeyManager.isAvailable()) return
        val cipher = runCatching { biometricKeyManager.createCipherForEncryption() }.getOrNull() ?: return
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val crypto = result.cryptoObject?.cipher ?: return
                    val dek = (authRepository as LocalAuthRepository).currentDekBytes() ?: return
                    biometricKeyManager.wrapKey(crypto, dek)
                }
            }
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Enable biometric unlock")
                .setNegativeButtonText("Not now")
                .build(),
            BiometricPrompt.CryptoObject(cipher)
        )
    }
}

@Composable
private fun AutoLockEffect(
    unlocked: Boolean,
    timeout: AutoLockTimeout,
    authRepository: LocalAuthRepository
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, unlocked, timeout) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && unlocked) {
                if (timeout == AutoLockTimeout.IMMEDIATE) {
                    authRepository.lock()
                }
            }
            if (event == Lifecycle.Event.ON_START && unlocked) {
                if (authRepository.shouldAutoLock(timeout)) {
                    authRepository.lock()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(unlocked, timeout) {
        while (unlocked && timeout != AutoLockTimeout.IMMEDIATE) {
            delay(15_000)
            if (authRepository.shouldAutoLock(timeout)) {
                authRepository.lock()
                break
            }
        }
    }
}

@Composable
private fun VaultlyAppNav(
    unlocked: Boolean,
    onBiometricUnlock: () -> Unit,
    onWrapBiometric: () -> Unit,
    biometricKeyManager: BiometricKeyManager,
    authRepository: LocalAuthRepository
) {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = koinViewModel()
    var showBottomBar by remember { mutableStateOf(false) }

    LaunchedEffect(unlocked) {
        if (!unlocked) {
            navController.navigate(AuthRoute) {
                popUpTo(0) { inclusive = true }
            }
            showBottomBar = false
        } else {
            showBottomBar = true
            navController.navigate(HomeRoute) {
                popUpTo(0) { inclusive = true }
            }
            // Offer biometric wrap after unlock if available and not wrapped
            if (biometricKeyManager.isAvailable() && !biometricKeyManager.hasWrappedKey()) {
                onWrapBiometric()
            }
        }
    }

    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route.orEmpty()
    val tabsVisible = unlocked && showBottomBar && !route.contains("AuthRoute")

    Scaffold(
        bottomBar = {
            if (tabsVisible) {
                NavigationBar {
                    val items = listOf(
                        Triple(HomeRoute, "Home", Icons.Default.Home),
                        Triple(VaultListRoute, "Vault", Icons.Default.Lock),
                        Triple(NotesListRoute, "Notes", Icons.Default.Notes),
                        Triple(AiRoute, "AI", Icons.Default.SmartToy),
                        Triple(SettingsRoute, "Settings", Icons.Default.Settings)
                    )
                    items.forEach { (dest, label, icon) ->
                        val selected = route.contains(dest::class.simpleName ?: label)
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = if (unlocked) HomeRoute else AuthRoute,
            modifier = Modifier.padding(padding)
        ) {
            authGraph(
                onUnlocked = { /* LaunchedEffect handles */ },
                onRequestBiometric = onBiometricUnlock
            )
            homeGraph(
                onOpenVault = { navController.navigate(VaultListRoute) },
                onOpenNotes = { navController.navigate(NotesListRoute) },
                onOpenHealth = { navController.navigate(VaultHealthRoute) },
                onOpenAi = { navController.navigate(AiRoute) }
            )
            vaultGraph(navController)
            notesGraph(navController)
            aiGraph()
            settingsGraph(onLocked = { authRepository.lock() })
        }
    }
}
