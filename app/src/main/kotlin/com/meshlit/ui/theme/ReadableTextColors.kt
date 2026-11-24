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
internal fun ColorScheme.readableTextColors():ColorScheme {
    // Material uses accent colors for text buttons and icons on neutral surfaces.
    fun accent(ink:Color):Color {
        val backgrounds=listOf(surface, background, surfaceContainerHigh.compositeOver(surface))
        if(backgrounds.all { textContrast(ink,it)>=4.5f })return ink
        val target=listOf(Color.Black,Color.White).maxBy { color -> backgrounds.minOf { textContrast(color,it) } }
        for(step in 1..20) {
            val adjusted=androidx.compose.ui.graphics.lerp(ink,target,step/20f)
            if(backgrounds.all { textContrast(adjusted,it)>=4.5f })return adjusted
        }
        return target
    }
    val p=accent(primary)
    val s=accent(secondary)
    val t=accent(tertiary)
    return copy(
        primary=p,secondary=s,tertiary=t,
        onBackground=readable(onBackground,background),onSurface=readable(onSurface,surface),
        onSurfaceVariant=readable(onSurfaceVariant,surfaceVariant),
        onPrimary=readable(onPrimary,p),onSecondary=readable(onSecondary,s),onTertiary=readable(onTertiary,t),
        onPrimaryContainer=readable(onPrimaryContainer,primaryContainer.compositeOver(surface)),
        onSecondaryContainer=readable(onSecondaryContainer,secondaryContainer.compositeOver(surface)),
        onTertiaryContainer=readable(onTertiaryContainer,tertiaryContainer.compositeOver(surface)),
        onError=readable(onError,error),onErrorContainer=readable(onErrorContainer,errorContainer),
    )
}
