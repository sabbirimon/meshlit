package com.meshlit.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meshlit.R
import com.meshlit.ui.components.HsvColorPicker
import com.meshlit.ui.theme.AnimatedGradient
import com.meshlit.ui.theme.CustomPalette
import kotlinx.coroutines.launch

/**
 * Phase 12.2 — custom color picker screen.
 *
 * Three tabs that produce a [CustomPalette] value:
 *  - **Solid** — five hand-picked ARGB swatches (primary / secondary
 *    / tertiary / surface / surfaceVariant). The user's "modern light
 *    pink" use case lives here.
 *  - **Gradient (static)** — 2–5 color stops blended linearly at a
 *    configurable angle.
 *  - **Gradient (animated)** — same stops + angle + cycle-seconds
 *    slider. The "slow animation" the user asked for lives here.
 *
 * The screen has a live preview card at the top showing the current
 * palette as a mini chat list (2 bubbles + composer pill), so the user
 * sees the result before they commit.
 *
 * A "Reset to curated palette" button at the bottom sets
 * `customPalette = None` (the standard `BasePalette` + `AccentHue`
 * fall back into place).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomThemeScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: ThemeSettingsViewModel = viewModel(
        factory = themeSettingsViewModelFactory(context),
    )
    val config by viewModel.config.collectAsState()
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(0) }
    var showResetDialog by remember { mutableStateOf(false) }

    // Local draft state — commits to the repository only when the
    // user taps "Apply" (or, for tabs with sliders, every change).
    // Keeps the picker snappy without thrashing DataStore.
    var draftSolid by remember { mutableStateOf(emptySolid()) }
    var draftGradient by remember { mutableStateOf(emptyGradientStops()) }
    var draftAnimated by remember { mutableStateOf(emptyAnimatedGradient()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Custom color palette") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Outlined.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { showResetDialog = true }) {
                        Text("Reset")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Live preview card.
            CustomPalettePreview(customPalette = config.customPalette)

            // Tabs.
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Solid") },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Gradient") },
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Animated") },
                )
            }

            when (selectedTab) {
                0 -> SolidTab(
                    state = draftSolid,
                    onState = { draftSolid = it },
                    onApply = {
                        viewModel.setCustomPalette(draftSolid.toPalette())
                    },
                )
                1 -> GradientTab(
                    state = draftGradient,
                    onState = { draftGradient = it },
                    onApply = {
                        viewModel.setCustomPalette(draftGradient.toPalette())
                    },
                )
                2 -> AnimatedGradientTab(
                    state = draftAnimated,
                    onState = { draftAnimated = it },
                    onApply = {
                        viewModel.setCustomPalette(draftAnimated.toPalette())
                    },
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset to curated palette?") },
            text = { Text("This clears the custom palette and reverts to the standard BasePalette + AccentHue.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setCustomPalette(CustomPalette.None)
                    showResetDialog = false
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Preview card
// ---------------------------------------------------------------------------

/**
 * Mini chat-list preview that reflects the user's current
 * [CustomPalette]. Shows 2 bubbles + a composer pill so the user
 * sees how the palette renders in a real chat.
 */
@Composable
private fun CustomPalettePreview(customPalette: CustomPalette) {
    val primary = remember(customPalette) { sample(customPalette, 0.0f) }
    val secondary = remember(customPalette) { sample(customPalette, 0.33f) }
    val surface = remember(customPalette) { sample(customPalette, 0.85f) }
    val container = remember(customPalette) { sample(customPalette, 1.0f) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Preview",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Assistant bubble.
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(surface)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    "Assistant response goes here.",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            // User bubble.
            Box(
                modifier = Modifier
                    .align(Alignment.End)
                    .clip(RoundedCornerShape(12.dp))
                    .background(primary)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    "User message",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            // Composer pill.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(container)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Type a message…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(secondary),
                )
            }
        }
    }
}

private fun sample(customPalette: CustomPalette, t: Float): Color = when (customPalette) {
    CustomPalette.None -> MaterialThemeColorFallbacks[t]
    is CustomPalette.Solid -> when (t) {
        0.0f -> Color(customPalette.primary)
        0.33f -> Color(customPalette.secondary)
        0.85f -> Color(customPalette.surface)
        else -> Color(customPalette.surfaceVariant)
    }
    is CustomPalette.GradientStops -> {
        val brush = AnimatedGradient.brush(
            stops = customPalette.stops.map { Color(it) },
            angleDeg = customPalette.angleDeg,
            phaseFraction = 0f,
        )
        brush.colorAt(t)
    }
    is CustomPalette.AnimatedGradient -> {
        val brush = AnimatedGradient.brush(
            stops = customPalette.stops.map { Color(it) },
            angleDeg = customPalette.angleDeg,
            phaseFraction = 0f,
        )
        brush.colorAt(t)
    }
}

private val MaterialThemeColorFallbacks: Map<Float, Color> = mapOf(
    0.0f to Color(0xFF8B5CF6),
    0.33f to Color(0xFF22D3EE),
    0.85f to Color(0xFF1B2238),
    1.0f to Color(0xFF121829),
)

// ---------------------------------------------------------------------------
// Solid tab
// ---------------------------------------------------------------------------

private data class SolidDraft(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val surface: Color,
    val surfaceVariant: Color,
) {
    fun toPalette(): CustomPalette.Solid = CustomPalette.Solid(
        primary = primary.toArgbLong(),
        secondary = secondary.toArgbLong(),
        tertiary = tertiary.toArgbLong(),
        surface = surface.toArgbLong(),
        surfaceVariant = surfaceVariant.toArgbLong(),
    )
}

private fun emptySolid() = SolidDraft(
    primary = Color(0xFFEC4899),
    secondary = Color(0xFF22D3EE),
    tertiary = Color(0xFF10B981),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF0F0F5),
)

@Composable
private fun SolidTab(
    state: SolidDraft,
    onState: (SolidDraft) -> Unit,
    onApply: () -> Unit,
) {
    var editingSlot by remember { mutableStateOf<Pair<String, Color>?>(null) }
    val slots = listOf(
        "Primary" to state.primary,
        "Secondary" to state.secondary,
        "Tertiary" to state.tertiary,
        "Surface" to state.surface,
        "Surface variant" to state.surfaceVariant,
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Pick each swatch. Tap to open the HSV picker.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(slots) { (name, color) ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { editingSlot = name to color },
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                CircleShape,
                            ),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Button(onClick = onApply, modifier = Modifier.fillMaxWidth()) {
            Text("Apply")
        }
    }

    editingSlot?.let { (slotName, slotColor) ->
        AlertDialog(
            onDismissRequest = { editingSlot = null },
            title = { Text("Pick $slotName") },
            text = {
                HsvColorPicker(
                    initialColor = slotColor,
                    onColorChange = { newColor ->
                        onState(
                            when (slotName) {
                                "Primary" -> state.copy(primary = newColor)
                                "Secondary" -> state.copy(secondary = newColor)
                                "Tertiary" -> state.copy(tertiary = newColor)
                                "Surface" -> state.copy(surface = newColor)
                                "Surface variant" -> state.copy(surfaceVariant = newColor)
                                else -> state
                            },
                        )
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { editingSlot = null }) { Text("Done") }
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Gradient tab
// ---------------------------------------------------------------------------

private data class GradientDraft(
    val stops: List<Color>,
    val angleDeg: Int,
) {
    fun toPalette(): CustomPalette.GradientStops = CustomPalette.GradientStops(
        stops = stops.map { it.toArgbLong() },
        angleDeg = angleDeg,
    )
}

private fun emptyGradientStops() = GradientDraft(
    stops = listOf(
        Color(0xFFEC4899),
        Color(0xFF8B5CF6),
        Color(0xFF22D3EE),
    ),
    angleDeg = 135,
)

@Composable
private fun GradientTab(
    state: GradientDraft,
    onState: (GradientDraft) -> Unit,
    onApply: () -> Unit,
) {
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "2–5 stops. Tap a swatch to edit. Use ± to add/remove stops.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Live preview of the gradient.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        colors = state.stops,
                    ),
                ),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.stops.withIndex().toList()) { (i, color) ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                CircleShape,
                            )
                            .clickable { editingIndex = i },
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "#${i + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.stops.size < 5) {
                        IconButton(onClick = {
                            onState(state.copy(stops = state.stops + Color(0xFF8B5CF6)))
                        }) {
                            Icon(Icons.Outlined.Add, contentDescription = "Add stop")
                        }
                    }
                    if (state.stops.size > 2) {
                        IconButton(onClick = {
                            onState(state.copy(stops = state.stops.dropLast(1)))
                        }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Remove stop")
                        }
                    }
                }
            }
        }
        Text(
            "Angle: ${state.angleDeg}°",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = state.angleDeg.toFloat(),
            onValueChange = { onState(state.copy(angleDeg = it.toInt())) },
            valueRange = 0f..360f,
            steps = 36,
        )
        Button(onClick = onApply, modifier = Modifier.fillMaxWidth()) {
            Text("Apply")
        }
    }

    editingIndex?.let { i ->
        // Picker keyed on the current stop color; consumer mutates
        // the list when the user moves the cursor.
        AlertDialog(
            onDismissRequest = { editingIndex = null },
            title = { Text("Pick stop #${i + 1}") },
            text = {
                HsvColorPicker(
                    initialColor = state.stops.getOrElse(i) { Color(0xFF8B5CF6) },
                    onColorChange = { newColor ->
                        val updated = state.stops.toMutableList()
                        if (i < updated.size) updated[i] = newColor
                        onState(state.copy(stops = updated))
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { editingIndex = null }) { Text("Done") }
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Animated gradient tab
// ---------------------------------------------------------------------------

private data class AnimatedDraft(
    val stops: List<Color>,
    val cycleSeconds: Int,
    val angleDeg: Int,
) {
    fun toPalette(): CustomPalette.AnimatedGradient = CustomPalette.AnimatedGradient(
        stops = stops.map { it.toArgbLong() },
        cycleSeconds = cycleSeconds,
        angleDeg = angleDeg,
    )
}

private fun emptyAnimatedGradient() = AnimatedDraft(
    stops = listOf(
        Color(0xFFEC4899),
        Color(0xFF8B5CF6),
        Color(0xFF22D3EE),
    ),
    cycleSeconds = 12,
    angleDeg = 135,
)

@Composable
private fun AnimatedGradientTab(
    state: AnimatedDraft,
    onState: (AnimatedDraft) -> Unit,
    onApply: () -> Unit,
) {
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Same as gradient, but the brush drifts over the cycle.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        colors = state.stops,
                    ),
                ),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.stops.withIndex().toList()) { (i, color) ->
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            CircleShape,
                        )
                        .clickable { editingIndex = i },
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.stops.size < 5) {
                        IconButton(onClick = {
                            onState(state.copy(stops = state.stops + Color(0xFF8B5CF6)))
                        }) {
                            Icon(Icons.Outlined.Add, contentDescription = "Add stop")
                        }
                    }
                    if (state.stops.size > 2) {
                        IconButton(onClick = {
                            onState(state.copy(stops = state.stops.dropLast(1)))
                        }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Remove stop")
                        }
                    }
                }
            }
        }
        Text(
            "Cycle: ${state.cycleSeconds}s",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = state.cycleSeconds.toFloat(),
            onValueChange = { onState(state.copy(cycleSeconds = it.toInt())) },
            valueRange = 4f..60f,
            steps = 0,
        )
        Text(
            "Angle: ${state.angleDeg}°",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = state.angleDeg.toFloat(),
            onValueChange = { onState(state.copy(angleDeg = it.toInt())) },
            valueRange = 0f..360f,
            steps = 36,
        )
        Button(onClick = onApply, modifier = Modifier.fillMaxWidth()) {
            Text("Apply")
        }
    }

    editingIndex?.let { i ->
        AlertDialog(
            onDismissRequest = { editingIndex = null },
            title = { Text("Pick stop #${i + 1}") },
            text = {
                HsvColorPicker(
                    initialColor = state.stops.getOrElse(i) { Color(0xFF8B5CF6) },
                    onColorChange = { newColor ->
                        val updated = state.stops.toMutableList()
                        if (i < updated.size) updated[i] = newColor
                        onState(state.copy(stops = updated))
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { editingIndex = null }) { Text("Done") }
            },
        )
    }
}

private fun Color.toArgbLong(): Long = this.toArgb().toLong() and 0xFFFFFFFFL