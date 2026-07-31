package com.meshlit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val MeshlitDarkScheme = darkColorScheme(
    primary = MeshlitViolet,
    onPrimary = MeshlitTextPrimary,
    primaryContainer = MeshlitVioletDim,
    onPrimaryContainer = MeshlitTextPrimary,
    secondary = MeshlitCyan,
    onSecondary = MeshlitMidnight,
    secondaryContainer = MeshlitCyanDim,
    onSecondaryContainer = MeshlitTextPrimary,
    tertiary = MeshlitEmerald,
    onTertiary = MeshlitMidnight,
    tertiaryContainer = MeshlitEmeraldDim,
    onTertiaryContainer = MeshlitTextPrimary,
    background = MeshlitMidnight,
    onBackground = MeshlitTextPrimary,
    surface = MeshlitSurface,
    onSurface = MeshlitTextPrimary,
    surfaceVariant = MeshlitSurfaceVariant,
    onSurfaceVariant = MeshlitTextSecondary,
    outline = MeshlitOutline,
    outlineVariant = MeshlitOutline,
    error = MeshlitError,
    onError = MeshlitTextPrimary,
)

private val MeshlitLightScheme = lightColorScheme(
    primary = MeshlitViolet,
    onPrimary = MeshlitTextPrimary,
    primaryContainer = MeshlitVioletDim,
    onPrimaryContainer = MeshlitTextPrimary,
    secondary = MeshlitCyan,
    onSecondary = MeshlitMidnight,
    tertiary = MeshlitEmerald,
    onTertiary = MeshlitMidnight,
    background = MeshlitMidnight,
    onBackground = MeshlitTextPrimary,
    surface = MeshlitSurface,
    onSurface = MeshlitTextPrimary,
    surfaceVariant = MeshlitSurfaceVariant,
    onSurfaceVariant = MeshlitTextSecondary,
    outline = MeshlitOutline,
    error = MeshlitError,
    onError = MeshlitTextPrimary,
)

/**
 * Meshlit theme — always renders the dark palette regardless of system
 * preference, because the brand identity is built around the midnight
 * background. The `isSystemInDarkTheme` parameter is kept for API
 * symmetry but does not gate a light scheme.
 */
@Composable
fun MeshlitTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = MeshlitDarkScheme,
        typography = MeshlitTypography,
        content = content,
    )
}
