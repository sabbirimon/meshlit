package com.meshlit.ui.v2

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.scale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meshlit.ui.theme.MeshlitInk
import com.meshlit.ui.theme.MeshlitOutlineV2
import com.meshlit.ui.theme.MeshlitPulseViolet
import com.meshlit.ui.theme.MeshlitTextPrimaryV2
import com.meshlit.ui.theme.MeshlitTextSecondaryV2
import com.meshlit.ui.theme.MeshlitTextTertiaryV2
import com.meshlit.ui.theme.meshlitPulseBrush
import com.meshlit.ui.theme.rememberMeshlitPulsePhase

/**
 * v2 bottom navigation bar. Replaces the v1 `MeshlitBottomBar`
 * for the `meshlitV2` build. Same horizontal `LazyRow` of
 * destinations, but:
 *   - Selected pill uses the Pulse gradient brush instead of
 *     a flat amber fill (per `docs/UI_AUDITION.md` contrast
 *     audit item 1).
 *   - Bar height drops from 80 dp (v1) to 72 dp.
 *   - Inactive icon stays neutral `MeshlitTextSecondary` so the
 *     bar reads as a single column of "next stops" rather than
 *     competing glyphs.
 *
 * The pill's pulse cycle is synced to `MeshlitPulseGradient`'s
 * 2.2 s drift so a Pulse-gradient surface anywhere in the same
 * screen (the lead bar's accent rail is solid, but the drawer
 * identity card pulses) drifts in lockstep with the bar.
 */
@Composable
fun MeshlitBottomBarV2(
    currentRoute: String,
    destinations: List<MeshlitV2Destination>,
    onSelect: (MeshlitV2Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pulsePhase = rememberMeshlitPulsePhase()
    // Hold a LazyListState so we can auto-scroll the selected
    // destination into the viewport. With 8 destinations the bar
    // is wider than a phone viewport (1344 dp / 1440 dp) so the
    // trailing items (Models, Structured, Vision, Catalog,
    // Advanced) start off-screen and are easy to miss. The
    // initial composition also scrollOffset lands inside the
    // initial viewport instead of starting at the far left.
    val listState = rememberLazyListState()
    val currentIndex = destinations.indexOfFirst { it.route == currentRoute }
    LaunchedEffect(currentIndex) {
        // `animateScrollToItem` is a no-op when the item is already
        // visible. The visible threshold (default 50 % in Compose
        // 1.7+) gives a soft "scroll if mostly off-screen" feel —
        // tapping a far-right destination like Advanced eases the
        // bar over instead of jumping.
        if (currentIndex >= 0) {
            listState.animateScrollToItem(currentIndex)
        }
    }
    // Read the system-gesture inset once as a PaddingValues. We
    // want the bar's *content* to sit above the gesture zone, not
    // the bar's outer container to shrink. `windowInsetsPadding`
    // would shrink the bar's height by the inset (Samsung OneUI
    // ships a ~135 px gesture zone), turning a 72 dp bar into
    // ~30 dp of visible icons. Applying the inset as plain bottom
    // padding on a Column wrapper preserves the bar's full height
    // and pushes its content up just enough to clear the system
    // back / home buttons.
    val systemGesturePadding = WindowInsets.systemGestures
        .asPaddingValues()
        .calculateBottomPadding()

    Column(
        modifier = modifier
            .fillMaxWidth()
            // System nav-bar inset. The v2 Scaffold sets
            // `contentWindowInsets = WindowInsets(0, 0, 0, 0)` so
            // the bottom bar has to consume the nav inset itself —
            // otherwise the icons sit under the system back / home
            // buttons (gesture pill on Pixel-style devices).
            //
            // We add a small extra bottom padding (8 dp) so the
            // bar's content sits flush against the system nav
            // bar's top edge, not 30 dp above it. The system-
            // gesture inset alone on Samsung OneUI returns 0, so
            // without `navigationBarsPadding()` the bar would
            // slide under the system back / home buttons.
            .navigationBarsPadding()
            .padding(bottom = systemGesturePadding),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            color = MeshlitInk,
            tonalElevation = 3.dp,
        ) {
            LazyRow(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(destinations, key = { it.route }) { dest ->
                    val selected = currentRoute == dest.route
                    BottomBarV2Item(
                        destination = dest,
                        selected = selected,
                        pulsePhase = pulsePhase,
                        onClick = { onSelect(dest) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomBarV2Item(
    destination: MeshlitV2Destination,
    selected: Boolean,
    pulsePhase: Float,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1.0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 220),
        label = "bar-scale",
    )

    Surface(
        modifier = Modifier
            // Tightened from 4 dp → 2 dp so 8 destinations fit
            // on a 1440-wide screen without the rightmost items
            // (Models, Structured, Vision, Catalog, Advanced)
            // clipping into the next-item overflow. The bar still
            // scrolls horizontally on smaller viewports (the v1
            // pattern) but the per-item slack that used to push
            // Models off-screen is gone.
            .padding(horizontal = 2.dp)
            .height(44.dp)
            .scale(scale),
        color = if (selected) MeshlitPulseViolet.copy(alpha = 0.18f) else Color.Transparent,
        shape = RoundedCornerShape(20.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                // Tightened from 14 dp → 10 dp. The selected pill
                // needs ~110 dp (icon 32 + 8 spacing + label ~50 +
                // 14 dp padding × 2); the unselected pill needs ~50
                // dp (icon 32 + 14 dp padding × 2). 10 dp saves 8 dp
                // per item × 8 items = 64 dp across the row, which
                // is enough to surface Models / Structured in the
                // initial viewport on a 1440-wide screen.
                .padding(horizontal = 10.dp)
                .fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        brush = if (selected) {
                            meshlitPulseBrush(pulsePhase).brush
                        } else {
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                colors = listOf(MeshlitOutlineV2, MeshlitOutlineV2),
                            )
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = destination.icon,
                    contentDescription = destination.label,
                    tint = if (selected) MeshlitTextPrimaryV2 else MeshlitTextSecondaryV2,
                    modifier = Modifier.size(20.dp),
                )
            }
            if (selected) {
                Text(
                    text = destination.label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MeshlitTextPrimaryV2,
                )
            } else {
                // Always render the label so unselected
                // destinations are self-identifying. The label is
                // dimmed (`MeshlitTextTertiaryV2`) so the active
                // pill still reads as the visual focus point, but
                // a user scrolling the bar can recognise what
                // every icon is without having to tap it first.
                Text(
                    text = destination.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MeshlitTextTertiaryV2,
                )
            }
        }
    }
}

// Auto-scroll into the active destination is wired above via
// `LaunchedEffect(currentIndex) { listState.animateScrollToItem(...) }`.
// On a 1344 dp viewport the bar still scrolls horizontally for
// the 8th destination (Advanced) on the first cold start; the
// effect eases the bar so the user sees the active item
// without having to swipe to find it.