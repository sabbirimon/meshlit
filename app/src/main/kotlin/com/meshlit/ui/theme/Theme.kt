package com.meshlit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Meshlit theme entry point. Reads the user-configured
 * [MeshlitThemeConfig] from the local (provided by [MeshlitApp] from
 * settings DataStore), resolves it against system dark mode if
 * [ThemeMode.SYSTEM] is selected, and applies the resulting
 * [MeshlitThemeConfig] via CompositionLocalProvider so descendants
 * can read both the resolved colors and the raw config.
 *
 * For previews and tests, use [MeshlitTheme] with an explicit config.
 */
@Composable
fun MeshlitTheme(
    config: MeshlitThemeConfig = LocalMeshlitThemeConfig.current,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
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
    val colorScheme = buildColorScheme(effectiveConfig)
    CompositionLocalProvider(LocalMeshlitThemeConfig provides effectiveConfig) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MeshlitTypography,
            content = content,
        )
    }
}