package com.meshlit.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance

internal fun textContrast(foreground:Color,background:Color):Float {
    val base=background.compositeOver(Color.White)
    val ink=foreground.compositeOver(base)
    return (maxOf(ink.luminance(),base.luminance())+0.05f)/(minOf(ink.luminance(),base.luminance())+0.05f)
}
private fun readable(preferred:Color,background:Color):Color = if(textContrast(preferred,background)>=4.5f)preferred
    else if(textContrast(Color.Black,background)>=textContrast(Color.White,background))Color.Black else Color.White

/** Preserve selected accents/surfaces while keeping ordinary labels readable on their solid surface. */
internal fun ColorScheme.readableTextColors():ColorScheme = copy(
    onBackground=readable(onBackground,background),onSurface=readable(onSurface,surface),
    onSurfaceVariant=readable(onSurfaceVariant,surfaceVariant),
    onPrimary=readable(onPrimary,primary),onSecondary=readable(onSecondary,secondary),onTertiary=readable(onTertiary,tertiary),
    onPrimaryContainer=readable(onPrimaryContainer,primaryContainer.compositeOver(surface)),
    onSecondaryContainer=readable(onSecondaryContainer,secondaryContainer.compositeOver(surface)),
    onTertiaryContainer=readable(onTertiaryContainer,tertiaryContainer.compositeOver(surface)),
    onError=readable(onError,error),onErrorContainer=readable(onErrorContainer,errorContainer),
)
