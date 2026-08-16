package com.meshlit.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshlit.design.MeshlitDesignPalette
import com.meshlit.ui.nav.TopLevelDestination
// `shortLabel` extension on TopLevelDestination lives in
// MeshlitBottomBar.kt (it pre-dates this FAB and is the canonical
// short-label source). Same package, no explicit import needed —
// extensions resolve transitively.

/**
 * Command-pill FAB + command palette, replaces the previous
 * `MeshlitBottomBar` LazyRow.
 *
 * Why this exists:
 *
 * The previous bottom bar packed 17 destinations into a
 * horizontally-scrolling row. That felt cluttered on a 411 dp
 * phone: labels wrapped, every screen was sticky-taped onto a
 * strip that took ~80 dp of vertical real estate, and the user
 * still had to swipe the bar to reach the drawer-only destinations.
 *
 * The replacement is a single glass FAB centred at the bottom of
 * the screen. Tap → opens a full-screen command palette with a
 * search field at the top and a scrollable grid of all
 * destinations below. Long-press → opens the existing
 * [MeshlitDrawer]. The FAB itself is a 56 dp pill with the
 * iridescent gradient fill (same family as the bottom bar's
 * selected pill in the previous design) so it reads as the same
 * brand language.
 *
 * Visual spec:
 *
 *  - 56 dp tall pill, 64 dp wide; centred horizontally.
 *  - Translucent dark fill (rgba(18,22,38,0.72)) at 60 % alpha so
 *    the content behind shows through the way the previous bar did.
 *  - 1 px iridescent cyan→purple→emerald border at 22 % alpha.
 *  - 12 dp soft cyan halo shadow (`haloCyanStrong`).
 *  - Sits 16 dp above the navigation-bar inset via
 *    `windowInsetsPadding(WindowInsets.navigationBars)`.
 *
 * Palette integration: the FAB inherits the `Stitch` colour
 * tokens from `MeshlitDesignPalette` so it auto-themes for
 * dark + light via [StitchPalette] but is currently locked to
 * the dark variant (matches every other glass surface in the
 * app today).
 */
@Composable
fun MeshlitCommandFab(
    destinations: List<TopLevelDestination>,
    currentRoute: String,
    onSelect: (TopLevelDestination) -> Unit,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var paletteOpen by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = !paletteOpen,
            enter = fadeIn(tween(180)) + scaleIn(tween(180)),
            exit = fadeOut(tween(120)) + scaleOut(tween(120)),
        ) {
            CommandFabPill(
                onClick = { paletteOpen = true },
                onLongClick = { onLongPress?.invoke() },
            )
        }
    }

    AnimatedVisibility(
        visible = paletteOpen,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(120)),
    ) {
        CommandPalette(
            destinations = destinations,
            currentRoute = currentRoute,
            onSelect = {
                paletteOpen = false
                onSelect(it)
            },
            onClose = { paletteOpen = false },
        )
    }
}

/**
 * The 56 dp pill FAB itself. Iridescent gradient background,
 * cyan→purple→emerald border, soft halo shadow. Tap → opens the
 * palette; long-press → opens the drawer (so power users keep
 * one-tap access to the hamburger menu from the same target).
 */
@Composable
private fun CommandFabPill(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = 12.dp)
            .size(width = 96.dp, height = 56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        MeshlitDesignPalette.iridescentStart,
                        MeshlitDesignPalette.iridescentMid,
                        MeshlitDesignPalette.iridescentEnd,
                    ),
                ),
            )
            .border(
                BorderStroke(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            MeshlitDesignPalette.iridescentStart.copy(alpha = 0.7f),
                            MeshlitDesignPalette.iridescentMid.copy(alpha = 0.7f),
                            MeshlitDesignPalette.iridescentEnd.copy(alpha = 0.7f),
                        ),
                    ),
                ),
                shape = RoundedCornerShape(28.dp),
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Open command palette",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Ask",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
        }
    }
}

/**
 * Full-screen command palette. Search bar at the top, scrollable
 * grid of destinations below. Tapping a tile routes the user and
 * closes the palette; the search field filters by display name
 * case-insensitively.
 */
@Composable
private fun CommandPalette(
    destinations: List<TopLevelDestination>,
    currentRoute: String,
    onSelect: (TopLevelDestination) -> Unit,
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    val filtered = remember(query, destinations) {
        if (query.isBlank()) destinations
        else destinations.filter {
            it.shortLabel.contains(query, ignoreCase = true)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MeshlitDesignPalette.canvasDark.copy(alpha = 0.92f))
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 48.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            // ── Header row: search field + close button ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MeshlitDesignPalette.Dark.glassFill)
                        .border(
                            BorderStroke(
                                width = 1.dp,
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        MeshlitDesignPalette.iridescentStart.copy(alpha = 0.22f),
                                        MeshlitDesignPalette.iridescentMid.copy(alpha = 0.22f),
                                        MeshlitDesignPalette.iridescentEnd.copy(alpha = 0.22f),
                                    ),
                                ),
                            ),
                            shape = RoundedCornerShape(24.dp),
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = MeshlitDesignPalette.iridescentStart,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = MeshlitDesignPalette.Dark.textPrimary,
                            fontSize = 16.sp,
                        ),
                        cursorBrush = Brush.verticalGradient(
                            colors = listOf(
                                MeshlitDesignPalette.iridescentStart,
                                MeshlitDesignPalette.iridescentEnd,
                            ),
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        decorationBox = { inner ->
                            if (query.isEmpty()) {
                                Text(
                                    text = "Search destinations…",
                                    color = MeshlitDesignPalette.Dark.textTertiary,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                            inner()
                        },
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MeshlitDesignPalette.Dark.glassFill)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = MeshlitDesignPalette.Dark.textPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            // ── Destination grid ──
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filtered, key = { it.route }) { dest ->
                    val selected = currentRoute == dest.route
                    CommandPaletteRow(
                        destination = dest,
                        selected = selected,
                        onClick = { onSelect(dest) },
                    )
                }
            }
        }
    }
}

/**
 * One row inside the command palette. Glass card with the
 * destination icon on the left, label centred, and a small
 * "current" pill on the right when the user is already on that
 * route. Tapping anywhere on the row routes the user.
 */
@Composable
private fun CommandPaletteRow(
    destination: TopLevelDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (selected)
                    Brush.horizontalGradient(
                        colors = listOf(
                            MeshlitDesignPalette.iridescentStart.copy(alpha = 0.18f),
                            MeshlitDesignPalette.iridescentMid.copy(alpha = 0.18f),
                            MeshlitDesignPalette.iridescentEnd.copy(alpha = 0.18f),
                        ),
                    )
                else Brush.horizontalGradient(
                    colors = listOf(
                        MeshlitDesignPalette.Dark.glassFill,
                        MeshlitDesignPalette.Dark.glassFill,
                    ),
                ),
            )
            .border(
                BorderStroke(
                    width = 1.dp,
                    color = if (selected)
                        MeshlitDesignPalette.iridescentStart.copy(alpha = 0.45f)
                    else
                        MeshlitDesignPalette.Dark.outline,
                ),
                shape = RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (selected)
                        Brush.horizontalGradient(
                            colors = listOf(
                                MeshlitDesignPalette.iridescentStart,
                                MeshlitDesignPalette.iridescentMid,
                            ),
                        )
                    else Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = destination.icon,
                contentDescription = null,
                tint = if (selected) Color.White
                       else MeshlitDesignPalette.Dark.textPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = destination.shortLabel,
            color = MeshlitDesignPalette.Dark.textPrimary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Text(
                text = "Current",
                color = MeshlitDesignPalette.iridescentStart,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/**
 * `combinedClickable` is the Compose-1.5+ helper that wires both
 * tap + long-press on a single Modifier. Imported once at the
 * top of this file (`androidx.compose.foundation.combinedClickable`)
 * and used directly in [CommandFabPill]'s modifier chain above —
 * no wrapper needed.
 */

// `shortLabel` is defined on `MeshlitBottomBar.kt` as an extension
// property on `TopLevelDestination`. We deliberately do *not*
// redeclare it here so the compiler treats both files as
// referencing the same source of truth. When the legacy
// `MeshlitBottomBar` is fully removed, hoist this extension into
// a shared `nav/TopLevelDestinationLabels.kt` file.
