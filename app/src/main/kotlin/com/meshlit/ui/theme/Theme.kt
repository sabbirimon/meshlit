package com.meshlit.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
    var hour by remember { mutableStateOf(java.time.LocalTime.now().hour) }
    LaunchedEffect(config.themeMode) {
        if(config.themeMode==ThemeMode.AUTO_TIME) while(isActive) {
            hour=java.time.LocalTime.now().hour
            delay(60_000)
        }
    }
    val dark = when(config.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.AUTO_TIME -> hour >= 19 || hour < 7
    }
    val useDynamicColor = config.dynamicColors && config.customPalette is CustomPalette.None &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val context = LocalContext.current
    var constrained by remember{mutableStateOf(true)}
    LaunchedEffect(context) {
        while(isActive) {
            constrained=withContext(Dispatchers.IO) {
                val manager=context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                val memory=android.app.ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
                val power=context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
                manager.isLowRamDevice || memory.lowMemory || power.isPowerSaveMode ||
                    (Build.VERSION.SDK_INT>=29 && power.currentThermalStatus>=android.os.PowerManager.THERMAL_STATUS_SEVERE)
            }
            delay(10000)
        }
    }
    val effectiveConfig = config.copy(surfaceStyle=if(constrained || config.highContrast) SurfaceStyle.SOLID else config.surfaceStyle,animationsEnabled=config.animationsEnabled && !constrained,basePalette = if(dark) {
        if(config.basePalette == BasePalette.PAPER) BasePalette.MIDNIGHT else config.basePalette
    } else BasePalette.PAPER)
    // Phase 12.2 — when the user picked an AnimatedGradient custom
    // palette we need a live AnimatedGradientBrush sampled from the
    // current infinite-transition phase. `phaseFor` is @Composable
    // (it calls `rememberInfiniteTransition`) so we collect the
    // phase here and build the brush right at the call site — that
    // way `buildColorScheme` stays a pure function. For Solid /
    // GradientStops we don't need a brush (the static path builds
    // its own internally with phase = 0f).
    val animatedBrush: AnimatedGradientBrush? = run {
        val custom = effectiveConfig.customPalette
        if (custom is CustomPalette.AnimatedGradient) {
            val phase = AnimatedGradient.phaseFor(effectiveConfig, custom)
            AnimatedGradient.brush(
                stops = custom.stops.map { Color(it) },
                angleDeg = custom.angleDeg,
                phaseFraction = phase,
            )
        } else null
    }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val scaledDensity = androidx.compose.ui.unit.Density(density.density * config.densityScale.coerceIn(0.85f, 1.3f), density.fontScale * config.fontScale.coerceIn(0.85f, 1.5f))
    val colorScheme = if (useDynamicColor) {
        if (dark) dynamicDarkColorScheme(context)
        else dynamicLightColorScheme(context)
    } else {
        buildColorScheme(effectiveConfig, animatedBrush)
    }
    val styledColors=if(effectiveConfig.surfaceStyle==SurfaceStyle.GLASS) colorScheme.copy(surfaceContainerLow=colorScheme.surfaceContainerLow.copy(alpha=0.88f),surfaceContainer=colorScheme.surfaceContainer.copy(alpha=0.92f),surfaceContainerHigh=colorScheme.surfaceContainerHigh.copy(alpha=0.96f)) else colorScheme
    val typography=remember(config.uiFont){typographyFor(config.uiFont)}
    CompositionLocalProvider(LocalMeshlitThemeConfig provides effectiveConfig, androidx.compose.ui.platform.LocalDensity provides scaledDensity) {
        MaterialTheme(
            colorScheme = styledColors,
            typography = typography,
            shapes = androidx.compose.material3.Shapes(
                extraSmall=androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                small=androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                medium=androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                large=androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                extraLarge=androidx.compose.foundation.shape.RoundedCornerShape(28.dp)),
            content = content,
        )
    }
}
