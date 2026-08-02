package com.meshlit.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

/**
 * Meshlit theme entry point. Reads the user-configured
 * [MeshlitThemeConfig] from the local (provided by [MeshlitApp] from
 * settings DataStore), resolves it against system dark mode if
 * [ThemeMode.SYSTEM] is selected, and applies the resulting
 * [MeshlitThemeConfig] via CompositionLocalProvider so descendants
 * can read both the resolved colors and the raw config.
 *
 * Dynamic color (Android 12+ / Material You): when the user hasn't
 * picked a custom accent palette we sample the system wallpaper
 * palette via [dynamicLightColorScheme] / [dynamicDarkColorScheme].
 * Users with the curated Meshlit palette bypass this so the brand
 * stays recognizable.
 *
 * For previews and tests, use [MeshlitTheme] with an explicit config.
 */
@Composable
fun MeshlitTheme(
    config: MeshlitThemeConfig = LocalMeshlitThemeConfig.current,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val useDynamicColor = config.basePalette == BasePalette.MIDNIGHT &&
        config.accentHue == AccentHue.MESHLIT &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val effectiveConfig = when (config.themeMode) {
        ThemeMode.SYSTEM -> config.copy(
            basePalette = if (systemDark) config.basePalette
                          else if (config.basePalette == BasePalette.MIDNIGHT) BasePalette.PAPER
                          else config.basePalette
        )
        ThemeMode.LIGHT -> if (config.basePalette == BasePalette.MIDNIGHT) {
            config.copy(basePalette = BasePalette.PAPER)
        } else config
        ThemeMode.DARK -> config
        ThemeMode.AUTO_TIME -> {
            val hour = java.time.LocalTime.now().hour
            val isNight = hour in 19..23 || hour in 0..6
            if (isNight) config
            else if (config.basePalette == BasePalette.MIDNIGHT) config.copy(basePalette = BasePalette.PAPER)
            else config
        }
    }
    val context = LocalContext.current
    val colorScheme = if (useDynamicColor) {
        if (systemDark) dynamicDarkColorScheme(context)
        else dynamicLightColorScheme(context)
    } else {
        buildColorScheme(effectiveConfig)
    }
    CompositionLocalProvider(LocalMeshlitThemeConfig provides effectiveConfig) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MeshlitTypography,
            content = content,
        )
    }
}