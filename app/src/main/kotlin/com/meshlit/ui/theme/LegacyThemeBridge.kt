package com.meshlit.ui.theme

import androidx.compose.material.Colors
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.luminance

/** Older tools use Material 2. Their palette must follow the same owner-selected theme. */
internal fun legacyColorsFor(colors:ColorScheme)=Colors(
    primary=colors.primary,primaryVariant=colors.primaryContainer,
    secondary=colors.secondary,secondaryVariant=colors.secondaryContainer,
    background=colors.background,surface=colors.surface,error=colors.error,
    onPrimary=colors.onPrimary,onSecondary=colors.onSecondary,
    onBackground=colors.onBackground,onSurface=colors.onSurface,onError=colors.onError,
    isLight=colors.surface.luminance()>0.45f,
)
