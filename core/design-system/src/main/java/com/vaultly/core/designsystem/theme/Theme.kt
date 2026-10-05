package com.vaultly.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Light: white surfaces, black text, grey accents.
 * Dark: near-black surfaces, white text, grey accents.
 * System: follows device setting.
 */
private val LightColors = lightColorScheme(
    primary = Grey100,
    onPrimary = Grey00,
    primaryContainer = Grey10,
    onPrimaryContainer = Grey100,
    secondary = Grey50,
    onSecondary = Grey00,
    secondaryContainer = Grey10,
    onSecondaryContainer = Grey80,
    tertiary = Grey60,
    onTertiary = Grey00,
    background = Grey05,
    onBackground = Grey100,
    surface = Grey00,
    onSurface = Grey100,
    surfaceVariant = Grey10,
    onSurfaceVariant = Grey50,
    outline = Grey30,
    outlineVariant = Grey20,
    error = Grey70,
    onError = Grey00,
    errorContainer = Grey10,
    onErrorContainer = Grey90,
    inverseSurface = Grey90,
    inverseOnSurface = Grey05,
    inversePrimary = Grey20,
    scrim = Grey100
)

private val DarkColors = darkColorScheme(
    primary = Grey00,
    onPrimary = Grey100,
    primaryContainer = Grey70,
    onPrimaryContainer = Grey00,
    secondary = Grey40,
    onSecondary = Grey100,
    secondaryContainer = Grey80,
    onSecondaryContainer = Grey10,
    tertiary = Grey30,
    onTertiary = Grey100,
    background = Grey95,
    onBackground = Grey05,
    surface = Grey90,
    onSurface = Grey05,
    surfaceVariant = Grey80,
    onSurfaceVariant = Grey30,
    outline = Grey50,
    outlineVariant = Grey70,
    error = Grey30,
    onError = Grey100,
    errorContainer = Grey80,
    onErrorContainer = Grey10,
    inverseSurface = Grey10,
    inverseOnSurface = Grey90,
    inversePrimary = Grey80,
    scrim = Grey100
)

enum class VaultlyThemeMode { LIGHT, DARK, SYSTEM }

@Composable
fun VaultlyTheme(
    themeMode: VaultlyThemeMode = VaultlyThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        VaultlyThemeMode.LIGHT -> false
        VaultlyThemeMode.DARK -> true
        VaultlyThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = VaultlyTypography,
        content = content
    )
}

object VaultlyTokens {
    val Success = Grey100
    val Warning = Grey50
    val Info = Grey40
    val SpacingXs = 4
    val SpacingSm = 8
    val SpacingMd = 12
    val SpacingLg = 16
    val SpacingXl = 24
    val RadiusSm = 8
    val RadiusMd = 12
    val RadiusLg = 16
}
