package com.meshlit.ui.v2

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.meshlit.ui.theme.MeshlitInk
import com.meshlit.ui.theme.MeshlitSurface
import com.meshlit.ui.theme.MeshlitSurfaceContainer
import com.meshlit.ui.theme.MeshlitSurfaceHigh
import com.meshlit.ui.theme.MeshlitTextPrimaryV2
import com.meshlit.ui.theme.MeshlitTextSecondaryV2
import com.meshlit.ui.theme.MeshlitTextTertiaryV2
import com.meshlit.ui.theme.MeshlitOutlineV2
import com.meshlit.ui.v2.components.MeshlitDynamicBackdrop

/**
 * v2 entry composable. Replaces the v1
 * `NavHost { "setup" -> SetupWizardScreen; "main" -> MeshlitApp() }`
 * tree in `MainActivity.kt` when `BuildConfig.USE_NEW_UI == true`.
 *
 * For build no. 1 the root delegates straight to `MeshlitAppV2`,
 * which owns the v2 nav graph + adaptive drawer + bottom bar.
 * The first-run setup wizard is wired in step 4 via a sibling
 * composable (`MeshlitV2SetupScreen`) that gates on the same
 * `firstRunDone` flow as the v1 root.
 *
 * The v2 build wraps content in a **hard-coded dark Compose
 * colorScheme** (per the plan's "Dark-first" row in
 * §"Design language"): the shared `MeshlitTheme` provider
 * keeps the v1 light/dark toggle, but every v2 surface that
 * reads `MaterialTheme.colorScheme.*` will see dark tokens so
 * `onBackground` reads white-on-ink, not black-on-paper. The
 * v1 build still uses the system-themed `MeshlitTheme` and is
 * unaffected by this override.
 *
 * Wiring concerns handled here:
 *   - `MeshlitAppV2` consumes the same `koinInject` singletons
 *     the v1 main uses (SettingsRepository, BootstrapSnapshotProvider).
 *   - The first-run detection is left to `MeshlitAppV2`'s
 *     `startRoute` parameter; the v2 root passes `startRoute`
 *     from the same `firstRunDone` flow the v1 root uses.
 *
 * @param startRoute the initial destination. Defaults to
 *        "devices". First-run wizards set this to "setup" and
 *        `V2Root` switches on the same condition as the v1 root.
 */
@Composable
fun V2Root(
    modifier: Modifier = Modifier,
    startRoute: String = "devices",
) {
    MaterialTheme(colorScheme = v2DarkColorScheme()) {
        // Box (not Surface) so the MeshlitDynamicBackdrop
        // can paint behind the chrome. The backdrop itself
        // lays down a MeshlitInk base + two animated
        // radial blobs (violet + aqua/coral mix) + a final
        // 0.85 ink overlay so text keeps WCAG AA contrast.
        // Net effect: the background hue shifts subtly over
        // 32 s like Gemini's home screen, but stays dark
        // enough for the v2 light-on-dark text to read.
        Box(modifier = modifier.fillMaxSize()) {
            MeshlitDynamicBackdrop(modifier = Modifier.fillMaxSize())
            MeshlitAppV2(
                startRoute = startRoute,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Hard-coded dark `ColorScheme` for the v2 build. Mirrors the
 * Pulse palette so `MaterialTheme.colorScheme.onBackground`
 * resolves to [MeshlitTextPrimaryV2] (near-white) and the body
 * surfaces read as the v2 ink / surface tokens instead of the
 * stock light Material defaults. v1 builds are untouched.
 */
@Composable
private fun v2DarkColorScheme() = darkColorScheme(
    background = MeshlitInk,
    surface = MeshlitSurface,
    surfaceVariant = MeshlitSurfaceContainer,
    surfaceContainer = MeshlitSurfaceContainer,
    surfaceContainerHigh = MeshlitSurfaceHigh,
    onBackground = MeshlitTextPrimaryV2,
    onSurface = MeshlitTextPrimaryV2,
    onSurfaceVariant = MeshlitTextSecondaryV2,
    outline = MeshlitOutlineV2,
    outlineVariant = MeshlitOutlineV2,
    primary = MeshlitTextPrimaryV2,
    onPrimary = MeshlitInk,
    secondary = MeshlitTextSecondaryV2,
    onSecondary = MeshlitInk,
    tertiary = MeshlitTextTertiaryV2,
    onTertiary = MeshlitInk,
)
