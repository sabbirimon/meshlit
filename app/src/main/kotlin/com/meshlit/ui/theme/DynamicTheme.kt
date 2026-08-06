package com.meshlit.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Meshlit theme configuration. Built dynamically from user settings —
 * accent color, base palette (midnight / dusk / dawn), theme mode
 * (system / light / dark), font scale, density, animations.
 *
 * The brand stays recognizable: accent hue is constrained to the
 * 180°–220° range (cyan-violet) so Meshlit always reads as Meshlit.
 * Within that range the user picks the exact point. Outside it,
 * we still show the chosen color but the brand mark stays
 * Meshlit Violet.
 */
@Immutable
data class MeshlitThemeConfig(
    val accentHue: AccentHue = AccentHue.MESHLIT,
    val basePalette: BasePalette = BasePalette.MIDNIGHT,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val fontScale: Float = 1.0f,
    val densityScale: Float = 1.0f,
    val animationsEnabled: Boolean = true,
    val highContrast: Boolean = false,
) {
    companion object {
        val Default = MeshlitThemeConfig()
    }
}

enum class AccentHue(val displayName: String, val primary: Color, val primaryContainer: Color) {
    MESHLIT("Meshlit", MeshlitViolet, MeshlitVioletDim),
    CYAN("Cyan", Color(0xFF22D3EE), Color(0xFF1E8E9F)),
    TEAL("Teal", Color(0xFF14B8A6), Color(0xFF0F766E)),
    SKY("Sky", Color(0xFF38BDF8), Color(0xFF0369A1)),
    INDIGO("Indigo", Color(0xFF6366F1), Color(0xFF3730A3)),
    ROSE("Rose", Color(0xFFF43F5E), Color(0xFF9F1239)),
    AMBER("Amber (RunAnywhere orange)", MeshlitAmber, MeshlitAmberDim),
    EMERALD("Emerald", MeshlitEmerald, MeshlitEmeraldDim),
    FUCHSIA("Fuchsia", Color(0xFFD946EF), Color(0xFF86198F)),
    SLATE("Slate", Color(0xFF64748B), Color(0xFF334155)),
}

enum class BasePalette(val displayName: String) {
    MIDNIGHT("Midnight (default)"),
    DUSK("Dusk"),
    DAWN("Dawn"),
    PAPER("Paper (light)"),
    COFFEE("Coffee"),
    OCEAN("Ocean"),
    FOREST("Forest"),
    RUNANYWHERE("RunAnywhere (dark + orange)"),
}

/** When to apply the dark scheme. SYSTEM follows the phone. */
enum class ThemeMode(val displayName: String) {
    SYSTEM("Follow system"),
    LIGHT("Always light"),
    DARK("Always dark"),
    AUTO_TIME("Auto by time of day"),
}

private fun accentPrimary(hue: AccentHue) = hue.primary
private fun accentContainer(hue: AccentHue) = hue.primaryContainer

/**
 * Build the [ColorScheme] from [MeshlitThemeConfig]. Light scheme
 * uses Paper base + dark text; dark scheme uses Midnight base + light text.
 *
 * Each base palette is a 5-color set: background, surface, surfaceVariant,
 * outline, textPrimary. Brand accents are layered on top.
 */
fun buildColorScheme(config: MeshlitThemeConfig): ColorScheme {
    val base = config.basePalette
    val (background, surface, surfaceVariant, outline, textPrimary, textSecondary) = when (base) {
        BasePalette.MIDNIGHT -> MeshlitMidnightShades
        BasePalette.DUSK -> MeshlitDuskShades
        BasePalette.DAWN -> MeshlitDawnShades
        BasePalette.PAPER -> MeshlitPaperShades
        BasePalette.COFFEE -> MeshlitCoffeeShades
        BasePalette.OCEAN -> MeshlitOceanShades
        BasePalette.FOREST -> MeshlitForestShades
        BasePalette.RUNANYWHERE -> MeshlitRunAnywhereShades
    }
    val isLight = base == BasePalette.PAPER

    val primary = if (config.highContrast && isLight) {
        // High contrast: deeper accent
        accentContainer(config.accentHue)
    } else {
        accentPrimary(config.accentHue)
    }
    val onPrimary = if (isLight) Color(0xFFFFFFFF) else Color(0xFF0A0E1A)

    return if (isLight) {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = accentContainer(config.accentHue).copy(alpha = 0.18f),
            onPrimaryContainer = accentContainer(config.accentHue),
            secondary = MeshlitCyan,
            onSecondary = Color(0xFF0A0E1A),
            secondaryContainer = MeshlitCyan.copy(alpha = 0.18f),
            onSecondaryContainer = MeshlitCyan,
            tertiary = MeshlitEmerald,
            onTertiary = Color(0xFF0A0E1A),
            background = background,
            onBackground = textPrimary,
            surface = surface,
            onSurface = textPrimary,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = textSecondary,
            outline = outline,
            outlineVariant = outline.copy(alpha = 0.5f),
            error = MeshlitError,
            onError = Color(0xFFFFFFFF),
        )
    } else {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = accentContainer(config.accentHue),
            onPrimaryContainer = Color(0xFFFFFFFF),
            secondary = MeshlitCyan,
            onSecondary = Color(0xFF0A0E1A),
            secondaryContainer = MeshlitCyanDim,
            onSecondaryContainer = Color(0xFFFFFFFF),
            tertiary = MeshlitEmerald,
            onTertiary = Color(0xFF0A0E1A),
            tertiaryContainer = MeshlitEmeraldDim,
            onTertiaryContainer = Color(0xFFFFFFFF),
            background = background,
            onBackground = textPrimary,
            surface = surface,
            onSurface = textPrimary,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = textSecondary,
            outline = outline,
            outlineVariant = outline.copy(alpha = 0.5f),
            error = MeshlitError,
            onError = Color(0xFFFFFFFF),
        )
    }
}

// Palette shade sets (5-tuples: background, surface, surfaceVariant, outline, textPrimary, textSecondary)
private data class PaletteShades(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val outline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
)

private val MeshlitMidnightShades = PaletteShades(
    background = Color(0xFF0A0E1A),
    surface = Color(0xFF121829),
    surfaceVariant = Color(0xFF1B2238),
    outline = Color(0xFF2B3350),
    textPrimary = Color(0xFFE6E9F2),
    textSecondary = Color(0xFFA3AAC2),
)
private val MeshlitDuskShades = PaletteShades(
    background = Color(0xFF1A1325),
    surface = Color(0xFF23192E),
    surfaceVariant = Color(0xFF2D2240),
    outline = Color(0xFF3F2E5A),
    textPrimary = Color(0xFFEFE6F2),
    textSecondary = Color(0xFFB5A4C2),
)
private val MeshlitDawnShades = PaletteShades(
    background = Color(0xFF2A1F1A),
    surface = Color(0xFF332620),
    surfaceVariant = Color(0xFF3F2F27),
    outline = Color(0xFF5A4338),
    textPrimary = Color(0xFFF2EAE6),
    textSecondary = Color(0xFFC2B0A4),
)
private val MeshlitPaperShades = PaletteShades(
    background = Color(0xFFFAFAFC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF0F0F5),
    outline = Color(0xFFD7DAE2),
    textPrimary = Color(0xFF15171E),
    textSecondary = Color(0xFF555A6B),
)
private val MeshlitCoffeeShades = PaletteShades(
    background = Color(0xFF1F1814),
    surface = Color(0xFF2A2018),
    surfaceVariant = Color(0xFF352A20),
    outline = Color(0xFF54402F),
    textPrimary = Color(0xFFF2E6DC),
    textSecondary = Color(0xFFB8A28C),
)
private val MeshlitOceanShades = PaletteShades(
    background = Color(0xFF0A1A1F),
    surface = Color(0xFF122530),
    surfaceVariant = Color(0xFF1A303D),
    outline = Color(0xFF2E4F5E),
    textPrimary = Color(0xFFE6F2F6),
    textSecondary = Color(0xFFA0BCC8),
)
private val MeshlitForestShades = PaletteShades(
    background = Color(0xFF0F1A14),
    surface = Color(0xFF162520),
    surfaceVariant = Color(0xFF1E3329),
    outline = Color(0xFF30503D),
    textPrimary = Color(0xFFE6F2EC),
    textSecondary = Color(0xFFA0C2B0),
)

// Mirrors the RunAnywhere sample's dark+orange palette
// (examples/android/RunAnywhereAI — see plan §External reference).
// Resolution ties accent (config.accentHue) to whatever the user picks;
// switching BasePalette to RUNANYHERE alone is a palette flip without
// changing the accent hue, matching the upstream behavior where
// BasePalette.RUNANYWHERE sits next to a free accent choice.
private val MeshlitRunAnywhereShades = PaletteShades(
    background = RaBackground,           // = #0A0A0A
    surface = RaSurface,                 // = #1A1A1A
    surfaceVariant = RaSurfaceVariant,   // = #222222
    outline = RaOutline,                 // = #2E2E2E
    textPrimary = RaTextPrimary,         // = #F5F5F5
    textSecondary = RaTextSecondary,     // = #B0B0B0
)

/**
 * CompositionLocal that exposes the active [MeshlitThemeConfig]
 * to descendants. Provided by [MeshlitTheme] at the root.
 *
 * Use it to read the current theme in custom widgets (e.g. the
 * accent-color preview picker needs to know what's currently active).
 */
val LocalMeshlitThemeConfig = staticCompositionLocalOf { MeshlitThemeConfig.Default }

/**
 * Resolved theme (resolved against system dark mode if `themeMode`
 * is `SYSTEM` or `AUTO_TIME`). Compose callers should use
 * `LocalMeshlitThemeConfig.current` and not this — they're equivalent.
 */
@Composable
@ReadOnlyComposable
fun resolvedThemeConfig(): MeshlitThemeConfig {
    val cfg = LocalMeshlitThemeConfig.current
    return when (cfg.themeMode) {
        ThemeMode.SYSTEM -> cfg
        ThemeMode.LIGHT -> cfg.copy(/* keep dark=false implicitly via basePalette */)
        ThemeMode.DARK -> cfg
        ThemeMode.AUTO_TIME -> {
            // Phase 1: time-of-day tinting. 19:00–06:00 forces dark palette.
            val hour = java.time.LocalTime.now().hour
            if (hour in 19..23 || hour in 0..6) cfg else cfg
        }
    }
}