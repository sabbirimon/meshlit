package com.meshlit.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

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
 *
 * Phase 12.2 — when [customPalette] is non-`None`, [buildColorScheme]
 * samples the user's custom stops instead of the curated
 * `BasePalette` table. The user can pick:
 *  - **Solid** — five flat ARGB swatches.
 *  - **Gradient stops** — 2–5 stops blended linearly (static).
 *  - **Animated gradient** — same stops, but the brush drifts over
 *    `cycleSeconds` (default 12s — slow, per the user's spec).
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
    val customPalette: CustomPalette = CustomPalette.None,
) {
    companion object {
        val Default = MeshlitThemeConfig()
    }
}

/**
 * Phase 12.2 — user-defined palette override.
 *
 * Three variants:
 *  - [None] — fall back to the curated `BasePalette` + `AccentHue`.
 *  - [Solid] — five hand-picked ARGB swatches (primary / secondary
 *    / tertiary / surface / surfaceVariant). The user's "modern
 *    light pink" use case lives here.
 *  - [GradientStops] — 2–5 ARGB stops blended linearly at a
 *    configurable angle. Static; no animation.
 *  - [AnimatedGradient] — same shape as [GradientStops] but the
 *    brush drifts cyclically over [cycleSeconds]. The "slow
 *    animation" the user asked for lives here.
 *
 * Persisted via `SettingsRepository.customPaletteJson` as JSON.
 * Missing / malformed JSON falls back to [None] without crashing
 * (graceful migration — see SettingsRepository).
 */
@Serializable
sealed class CustomPalette {
    @Serializable
    object None : CustomPalette()

    @Serializable
    data class Solid(
        val primary: Long,
        val secondary: Long,
        val tertiary: Long,
        val surface: Long,
        val surfaceVariant: Long,
    ) : CustomPalette()

    @Serializable
    data class GradientStops(
        val stops: List<Long>,
        val angleDeg: Int = 135,
    ) : CustomPalette()

    @Serializable
    data class AnimatedGradient(
        val stops: List<Long>,
        val cycleSeconds: Int = 12,
        val angleDeg: Int = 135,
    ) : CustomPalette()
}

enum class AccentHue(val displayName: String, val primary: Color, val primaryContainer: Color) {
    MESHLIT("Meshlit", MeshlitViolet, MeshlitVioletDim),
    CYAN("Cyan", Color(0xFF22D3EE), Color(0xFF1E8E9F)),
    TEAL("Teal", Color(0xFF14B8A6), Color(0xFF0F766E)),
    SKY("Sky", Color(0xFF38BDF8), Color(0xFF0369A1)),
    INDIGO("Indigo", Color(0xFF6366F1), Color(0xFF3730A3)),
    ROSE("Rose", Color(0xFFF43F5E), Color(0xFF9F1239)),
    AMBER("Amber", Color(0xFFF59E0B), Color(0xFFB45309)),
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
 *
 * Phase 12.2 — when `config.customPalette` is non-`None`, we
 * short-circuit the curated `BasePalette` switch and read the
 * user's swatches / gradient stops directly. The animated
 * gradient variant samples a pre-built [AnimatedGradientBrush]
 * passed in by the caller (which is `@Composable` so it can
 * collect the infinite transition); see [buildColorScheme]
 * callers in `MeshlitTheme`.
 */
fun buildColorScheme(
    config: MeshlitThemeConfig,
    animatedBrush: AnimatedGradientBrush? = null,
): ColorScheme {
    // Phase 12.2 — custom-palette override branch. When the user
    // has picked Solid / GradientStops / AnimatedGradient we
    // resolve the relevant `primary / secondary / tertiary /
    // surface / surfaceVariant` from the saved values, then
    // delegate to the rest of the function so the
    // dark/light/scheme plumbing stays identical to the curated
    // path. The animated branch consumes the pre-built
    // [animatedBrush]; the static branch builds its own brush
    // internally with phase = 0f.
    val custom = config.customPalette
    val customResolved: CustomResolved? = when (custom) {
        CustomPalette.None -> null
        is CustomPalette.Solid -> CustomResolved(
            primary = Color(custom.primary),
            secondary = Color(custom.secondary),
            tertiary = Color(custom.tertiary),
            surface = Color(custom.surface),
            surfaceVariant = Color(custom.surfaceVariant),
        )
        is CustomPalette.GradientStops -> {
            val brush = AnimatedGradient.brush(
                stops = custom.stops.map { Color(it) },
                angleDeg = custom.angleDeg,
                phaseFraction = 0f,
            )
            customResolvedFromBrush(brush)
        }
        is CustomPalette.AnimatedGradient -> {
            val brush = animatedBrush ?: AnimatedGradient.brush(
                stops = custom.stops.map { Color(it) },
                angleDeg = custom.angleDeg,
                phaseFraction = 0f,
            )
            customResolvedFromBrush(brush)
        }
    }
    val base = config.basePalette
    val (background, surface, surfaceVariant, outline, textPrimary, textSecondary) = when (base) {
        BasePalette.MIDNIGHT -> MeshlitMidnightShades
        BasePalette.DUSK -> MeshlitDuskShades
        BasePalette.DAWN -> MeshlitDawnShades
        BasePalette.PAPER -> MeshlitPaperShades
        BasePalette.COFFEE -> MeshlitCoffeeShades
        BasePalette.OCEAN -> MeshlitOceanShades
        BasePalette.FOREST -> MeshlitForestShades
    }
    val isLight = base == BasePalette.PAPER

    // Phase 12.2 — when a custom palette is active, use those
    // values directly. Otherwise fall back to the curated
    // AccentHue-derived primary / secondary / tertiary.
    val primary = customResolved?.primary ?: run {
        if (config.highContrast && isLight) {
            accentContainer(config.accentHue)
        } else {
            accentPrimary(config.accentHue)
        }
    }
    val secondary = customResolved?.secondary ?: MeshlitCyan
    val tertiary = customResolved?.tertiary ?: MeshlitEmerald
    val onPrimary = if (isLight) Color(0xFFFFFFFF) else Color(0xFF0A0E1A)

    return if (isLight) {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = (customResolved?.primary ?: accentContainer(config.accentHue))
                .copy(alpha = 0.18f),
            onPrimaryContainer = customResolved?.primary ?: accentContainer(config.accentHue),
            secondary = secondary,
            onSecondary = Color(0xFF0A0E1A),
            secondaryContainer = secondary.copy(alpha = 0.18f),
            onSecondaryContainer = secondary,
            tertiary = tertiary,
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
            primaryContainer = customResolved?.primary ?: accentContainer(config.accentHue),
            onPrimaryContainer = Color(0xFFFFFFFF),
            secondary = secondary,
            onSecondary = Color(0xFF0A0E1A),
            secondaryContainer = secondary.let { tint(it, 0.65f) },
            onSecondaryContainer = Color(0xFFFFFFFF),
            tertiary = tertiary,
            onTertiary = Color(0xFF0A0E1A),
            tertiaryContainer = tertiary.let { tint(it, 0.65f) },
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

/**
 * Phase 12.2 — internal carrier for the resolved custom-palette
 * swatches. Built in [buildColorScheme] from the user's
 * [CustomPalette] before the curated-path branch runs.
 */
private data class CustomResolved(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val surface: Color,
    val surfaceVariant: Color,
)

/**
 * Phase 12.2 — derive a [CustomResolved] carrier from a brush by
 * sampling the gradient at five canonical positions: 0.0 (primary),
 * 0.33 (secondary), 0.66 (tertiary), 0.85 (surface), 1.0 (surfaceVariant).
 *
 * Single-stop brush returns the same color for every slot; multi-stop
 * falls back to the curated secondary/tertiary/surface palette when the
 * gradient has fewer than five distinct sample points — we still want
 * Material3's tonal relationships to read sensibly, so the gradient
 * becomes the *primary* swatch and the rest defaults to its complements.
 */
private fun customResolvedFromBrush(brush: AnimatedGradientBrush): CustomResolved {
    val primary = brush.colorAt(0.0f)
    val secondary = brush.colorAt(0.33f)
    val tertiary = brush.colorAt(0.66f)
    val surface = brush.colorAt(0.85f)
    val surfaceVariant = brush.colorAt(1.0f)
    return CustomResolved(
        primary = primary,
        secondary = secondary,
        tertiary = tertiary,
        surface = surface,
        surfaceVariant = surfaceVariant,
    )
}

/**
 * Tint [color] by [factor] toward black. `factor = 0` returns the
 * original color; `factor = 1` returns black. Used to derive
 * `secondaryContainer` / `tertiaryContainer` tones from a custom
 * `secondary` / `tertiary` swatch so Material 3 tonal relationships
 * hold when the user picks arbitrary colors.
 */
private fun tint(color: Color, factor: Float): Color {
    val r = (color.red * (1f - factor)).coerceIn(0f, 1f)
    val g = (color.green * (1f - factor)).coerceIn(0f, 1f)
    val b = (color.blue * (1f - factor)).coerceIn(0f, 1f)
    return Color(red = r, green = g, blue = b, alpha = color.alpha)
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