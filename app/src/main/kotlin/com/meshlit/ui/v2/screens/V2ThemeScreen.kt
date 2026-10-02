package com.meshlit.ui.v2.screens

import androidx.compose.runtime.Composable
import com.meshlit.ui.screens.settings.ThemeCustomizationScreen
import com.meshlit.ui.v2.components.MeshlitDeepLinkWrap

/**
 * v2 wrapper for the live theme customization screen. The v1
 * `ThemeCustomizationScreen` already provides accent hue +
 * base palette + theme mode + font scale + density scale +
 * animations + high contrast + reset — all backed by the
 * `ThemeSettingsViewModel` which persists to DataStore. The
 * v2 wrapper only adds the lead bar; the actual color
 * controls render inside the v1 layout, untouched.
 *
 * Reached from the drawer footer ("Customize palette") so
 * the user can theme the cluster without going through
 * Settings → Display.
 */
@Composable
fun V2ThemeScreen() {
    MeshlitDeepLinkWrap(
        headline = "Customize palette",
        subtitle = "Accents, palette, mode, scale",
    ) {
        ThemeCustomizationScreen(
            advanced = true,
            onOpenCustomPalette = null,
        )
    }
}