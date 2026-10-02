# Meshlit — UI Audit (v2 build no. 1)

This document tracks the open follow-ups for the v2 UI build
(`com.meshlit.v2`, `versionName = 2.0.0-v2build1`). Most are
deliberately deferred from the initial commit to keep the
"build no. 1" surface small; each entry lists the location, the
issue, and a concrete next step.

The entries are organized by severity. Anything tagged
**#audit-shipped** has been resolved in `build no. 1`; the
remaining entries are tracked for `v2build2` and later.

## 1. Pulse gradient contrast

**Location:** `ui/theme/MeshlitPulseGradient.kt` —
`MeshlitPulseViolet = #7C6FF2` on `MeshlitInk = #06080F`
(`MeshlitTheme.kt` dark-first ramp).

**Issue:** the violet stop against the dark ink base reads as
~5.4:1 against `MeshlitTextPrimaryV2` (=`#E8ECF8`) — passes
WCAG AA for body text but the same stop used as a 4 dp accent
rail on `MeshlitLeadBar` against `MeshlitSurface` (=`#0E1322`)
drops to ~4.2:1. Below the AA 4.5:1 threshold for body and
the 3:1 threshold for non-text UI.

**Fix:** when used as a 4 dp accent rail, swap `MeshlitPulseViolet`
for `MeshlitPulseAqua` (=`#4DD9C0`) which sits at ~7.0:1 against
the dark ramp. The lead bar's accent rail becomes aqua instead
of violet; the rest of the violet accents (drawer selected
pill, bottom bar selected pill) stay violet because they're
applied against a brighter surface.

**Plan:** swap the lead bar's `MeshlitPulseViolet` to
`MeshlitPulseAqua` in `v2/components/MeshlitLeadBar.kt` once
the v2 design team signs off.

## 2. Pill input keyboard avoidance

**Location:** `ui/v2/components/MeshlitPillInput.kt:86-185`.

**Issue:** the `Surface` parent does not apply `Modifier.imePadding()`
or `Modifier.navigationBarsPadding()`. On a phone with the soft
keyboard open, the pill input is partially covered by the IME
until the user dismisses the keyboard.

**Fix:** wrap the `Surface` in:

```kotlin
Surface(
    modifier = modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 8.dp)
        .imePadding()
        .navigationBarsPadding(),
    ...
)
```

The outer `Modifier` chain is the right place — the `fillMaxWidth`
stays first so the padding modifiers apply to the same target
bounds. The existing `vertical = 8.dp` padding stays inside.

**Plan:** add the two padding modifiers + verify on a 6.1"
Pixel-class device with the IME open over the ClusterScreen.

## 3. PermanentNavigationDrawer width on Expanded

**Location:** `ui/v2/MeshlitAppV2.kt:102-103` —
`WindowWidthSizeClass.EXPANDED` (≥ 840 dp).

**Issue:** the `PermanentNavigationDrawer` Material 3 default
width is 240 dp; on a 12.9" iPad Pro (Expanded = 1024 dp) this
leaves the rail occupying ~23% of the width. The hero identity
card in `MeshlitFullDrawerContent` is sized at 200 dp tall +
fixed 280 dp wide — when the rail is narrower than 280 dp, the
identity card clips off the right edge.

**Fix:** pass `modifier = Modifier.width(280.dp)` to the
`PermanentNavigationDrawer` (modifying the `drawerContent`
slot, not the host) so the rail is always exactly 280 dp wide
on tablets. On phones (`ModalNavigationDrawer`), the M3 default
slide-over width of ~80% screen is correct.

**Plan:** add the `Modifier.width(280.dp)` to the
`PermanentNavigationDrawer` modifier chain + verify on a
tablet at 1024 dp width.

## 4. Page-enter animation on slow devices

**Location:** `ui/v2/MeshlitAppV2.kt` navigation graph (the
`MeshlitAppV2` Composable).

**Issue:** the plan's risk section #4 flagged that an
`AnimatedContent` page-enter (180 ms `slideInHorizontally` +
`fadeIn`) can stutter on ≤ 4 GB RAM devices. The current
implementation does not wrap the `NavHost` body in
`AnimatedContent` — build no. 1 ships the chrome without the
page-enter animation, which sidesteps the stutter but also
sidesteps the brand differentiation.

**Fix:** the v2 build needs to wire `AnimatedContent` around
the `NavHost` body, gated on `MeshlitThemeConfig.animationsEnabled`:

```kotlin
val config = LocalMeshlitThemeConfig.current
if (config.animationsEnabled) {
    AnimatedContent(
        targetState = currentRoute,
        transitionSpec = {
            (fadeIn(MeshlitMotion.PageEnter) +
                slideInHorizontally(MeshlitMotion.PageEnter) { it / 6 })
                .togetherWith(fadeOut(MeshlitMotion.PageEnter))
        },
        label = "v2-page-enter",
    ) { route ->
        NavHost(navController, startDestination = route) { ... }
    }
} else {
    NavHost(navController, startDestination = startRoute) { ... }
}
```

**Plan:** wire the gated `AnimatedContent` for `v2build2`
after the v2 design team agrees on the slide direction (Start
vs End) — the plan leaves it open and the v2 cromo initially
ships without the slide.

## 5. MeshlitMark aliasing at 24 dp

**Location:** `ui/components/MeshlitMark.kt` and the bottom
bar's icon container (`ui/v2/MeshlitBottomBarV2.kt:120-128`).

**Issue:** the mark is rendered on a `Canvas` with three
satellite nodes, a Brain node, and an orbital ring. At 24 dp
(the size used in the bottom bar's `MeshlitMark`-style icon
container and the quick-action sheet tiles) the orbital ring's
stroke alpha drops below 0.3 on Pixel-class devices, making
the ring look like a smudge.

**Fix:** when `size < 32.dp`, fall back to the static mark
(no `pulseFraction` animation) and bump the ring stroke alpha
to 0.5. The drawWithCache pattern documented in the plan's
risk section #5 keeps the static mark cheap (no recomposition
on pulse phase).

**Plan:** add the `size < 32.dp` size-based code path in
`MeshlitMark.kt` for `v2build2`.

## Verification

```bash
# Build the v2 flavor + run unit tests + audit
./gradlew :app:assembleMeshlitV2Debug
./gradlew :app:testMeshlitV2DebugUnitTest --tests "com.meshlit.ui.v2.screens.*"
./scripts/audit-ui-wiring.sh

# Install both flavors side-by-side
./gradlew :app:installMeshlitV1Debug
./gradlew :app:installMeshlitV2Debug
adb shell am start -n com.meshlit.v2.debug/com.meshlit.MainActivity
```

## Revert path

To roll back to v1 only:

```bash
# Remove the meshlitV2 flavor from app/build.gradle.kts
git revert <commit>  # OR delete the productFlavors block
./gradlew :app:assembleDebug  # builds via meshlitV1
```

The v1 source tree is not modified by v2 commits — `ui/v2/`
is a self-contained tree under `app/src/main/kotlin/com/meshlit/`.
Deleting it returns the v2 build to a no-op without affecting
v1.