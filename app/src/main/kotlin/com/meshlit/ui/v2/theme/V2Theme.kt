package com.meshlit.ui.v2.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.meshlit.ui.theme.LocalMeshlitThemeConfig
import com.meshlit.ui.theme.MeshlitThemeConfig
import com.meshlit.ui.theme.ThemeMode

/**
 * v2-only theme helpers. The v2 build is dark-first (per the
 * plan's "Dark-first" row in §"Design language"); the shared
 * `BasePalette.ThemeMode` enum keeps `AUTO_TIME` for the v1
 * build's legacy auto-by-time-of-day behaviour, but the v2
 * build never surfaces it — the plan's risk #6 calls this out:
 * "v2 ThemeMode.AUTO_TIME removal — the v2 theme keeps its own
 * V2ThemeMode enum (without AUTO_TIME) so the v1 BasePalette
 * .ThemeMode is unaffected. Migration within v2:
 * `when (mode) { AUTO_TIME -> DARK; … }` in the v2 reader."
 *
 * This file is that v2 reader. v2 screens call [v2ThemeConfig]
 * (instead of reading `LocalMeshlitThemeConfig.current` directly)
 * to get a config where `AUTO_TIME` is folded into `DARK`.
 *
 * The `MeshlitPulseGradient` is layered on top via `usePulseGradient`
 * being on by default in the v2 config (see plan §"Theme defaults").
 * No component reads `usePulseGradient` directly — `meshlitPulseBrush`
 * picks it up via `MeshlitThemeConfig.Default` if/when a screen
 * wants to opt out.
 *
 * This wrapper is purely additive — it does not mutate the shared
 * `MeshlitTheme` providers, so v1 calls to `MeshlitTheme`
 * outside this wrapper are unaffected.
 */

/**
 * Resolved v2 theme config. `AUTO_TIME` is mapped to `DARK` per
 * the plan; everything else passes through. v2 screens should
 * read this instead of `LocalMeshlitThemeConfig.current` when
 * they care about the resolved theme mode.
 */
@Composable
@ReadOnlyComposable
fun v2ThemeConfig(): MeshlitThemeConfig {
    val incoming = LocalMeshlitThemeConfig.current
    return incoming.copy(
        themeMode = when (incoming.themeMode) {
            ThemeMode.AUTO_TIME -> ThemeMode.DARK
            else -> incoming.themeMode
        },
    )
}
