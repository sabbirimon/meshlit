package com.meshlit.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meshlit.R
import com.meshlit.ui.theme.AccentHue
import com.meshlit.ui.theme.BasePalette
import com.meshlit.ui.theme.CustomPalette
import com.meshlit.ui.theme.MeshlitThemeConfig
import com.meshlit.ui.theme.ThemeMode

/**
 * Live theme customization screen. Every control writes through the
 * [ThemeSettingsViewModel] which persists to DataStore. Because
 * `MeshlitTheme` reads the config from a CompositionLocal that the
 * repository writes to, the entire app re-themes the moment a switch
 * flips. There's no "Apply" button — the user sees the change
 * immediately.
 *
 * Layout:
 *  - Accent color row: 10 hue swatches with check mark on active
 *  - Base palette row: 7 palette previews (small color block + name)
 *  - Theme mode: 4 segmented chips
 *  - Font scale: slider 0.85x → 1.5x with live preview text
 *  - Density scale: slider 0.85x → 1.3x with live preview layout
 *  - Animations: switch (advanced)
 *  - High contrast: switch (accessibility)
 *  - Reset to defaults: button + confirmation dialog
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeCustomizationScreen(
    advanced: Boolean,
    onOpenCustomPalette: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val viewModel: ThemeSettingsViewModel = viewModel(
        factory = themeSettingsViewModelFactory(context),
    )
    val config by viewModel.config.collectAsState()
    var showResetDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SectionHeader(text = stringResource(R.string.settings_theme_section_appearance))

        LabeledBlock(
            label = stringResource(R.string.settings_theme_accent_label),
            description = stringResource(R.string.settings_theme_accent_desc),
        ) {
            AccentHueRow(
                current = config.accentHue,
                onPick = viewModel::setAccentHue,
            )
        }

        LabeledBlock(
            label = stringResource(R.string.settings_theme_palette_label),
            description = stringResource(R.string.settings_theme_palette_desc),
        ) {
            BasePaletteRow(
                current = config.basePalette,
                onPick = viewModel::setBasePalette,
            )
        }

        // Phase 12.2 — entry point to the custom palette screen.
        // Hidden until the caller wires `onOpenCustomPalette`; older
        // call sites that don't provide it keep the old surface.
        if (onOpenCustomPalette != null) {
            LabeledBlock(
                label = "Custom color palette",
                description = customPaletteDescription(config.customPalette),
            ) {
                Button(onClick = onOpenCustomPalette) {
                    Text(
                        if (config.customPalette is CustomPalette.None)
                            "Pick custom colors"
                        else "Edit custom palette",
                    )
                }
            }
        }

        LabeledBlock(
            label = stringResource(R.string.settings_theme_mode_label),
            description = stringResource(R.string.settings_theme_mode_desc),
        ) {
            ThemeModeRow(
                current = config.themeMode,
                onPick = viewModel::setThemeMode,
            )
        }

        SectionHeader(text = stringResource(R.string.settings_theme_section_typography))

        LabeledBlock(
            label = stringResource(R.string.settings_theme_font_label),
            description = stringResource(R.string.settings_theme_font_desc),
        ) {
            FontScaleSlider(
                value = config.fontScale,
                onChange = viewModel::setFontScale,
            )
        }

        LabeledBlock(
            label = stringResource(R.string.settings_theme_density_label),
            description = stringResource(R.string.settings_theme_density_desc),
        ) {
            DensityScaleSlider(
                value = config.densityScale,
                onChange = viewModel::setDensityScale,
            )
        }

        SectionHeader(text = stringResource(R.string.settings_theme_section_accessibility))

        LabeledBlock(
            label = stringResource(R.string.settings_theme_animations_label),
            description = stringResource(R.string.settings_theme_animations_desc),
        ) {
            Switch(
                checked = config.animationsEnabled,
                onCheckedChange = viewModel::setAnimationsEnabled,
            )
        }

        LabeledBlock(
            label = stringResource(R.string.settings_theme_high_contrast_label),
            description = stringResource(R.string.settings_theme_high_contrast_desc),
        ) {
            Switch(
                checked = config.highContrast,
                onCheckedChange = viewModel::setHighContrast,
            )
        }

        if (advanced) {
            SectionHeader(text = stringResource(R.string.settings_theme_section_danger))
            LabeledBlock(
                label = stringResource(R.string.settings_theme_reset_label),
                description = stringResource(R.string.settings_theme_reset_desc),
            ) {
                Button(onClick = { showResetDialog = true }) {
                    Text(stringResource(R.string.settings_theme_reset_label))
                }
            }
        }

        Spacer(Modifier.height(48.dp))
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.settings_theme_reset_label)) },
            text = { Text(stringResource(R.string.settings_theme_reset_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetToDefaults()
                    showResetDialog = false
                }) { Text(stringResource(R.string.settings_theme_reset_label)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun LabeledBlock(
    label: String,
    description: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = label, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun AccentHueRow(
    current: AccentHue,
    onPick: (AccentHue) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(AccentHue.entries) { hue ->
            val selected = hue == current
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(hue.primary)
                    .border(
                        width = if (selected) 3.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.onBackground
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        shape = CircleShape,
                    )
                    .clickable { onPick(hue) },
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun BasePaletteRow(
    current: BasePalette,
    onPick: (BasePalette) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(BasePalette.entries) { palette ->
            val selected = palette == current
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable { onPick(palette) }
                    .padding(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp, 40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(palettePreviewColor(palette))
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(8.dp),
                        ),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = palette.displayName.substringBefore(' '),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

/**
 * Approximate preview color per palette. The actual surface comes
 * from the theme, but the picker shows a small block so the user can
 * scan palettes quickly.
 */
@Composable
private fun palettePreviewColor(palette: BasePalette): Color = when (palette) {
    BasePalette.MIDNIGHT -> Color(0xFF0A0E1A)
    BasePalette.DUSK -> Color(0xFF23192E)
    BasePalette.DAWN -> Color(0xFF332620)
    BasePalette.PAPER -> Color(0xFFFAFAFC)
    BasePalette.COFFEE -> Color(0xFF2A2018)
    BasePalette.OCEAN -> Color(0xFF122530)
    BasePalette.FOREST -> Color(0xFF162520)
    BasePalette.RUNANYWHERE -> Color(0xFF0A0A0A)
}

@Composable
private fun ThemeModeRow(
    current: ThemeMode,
    onPick: (ThemeMode) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeMode.entries.forEach { mode ->
            val selected = mode == current
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.clickable { onPick(mode) },
            ) {
                Text(
                    text = mode.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun FontScaleSlider(
    value: Float,
    onChange: (Float) -> Unit,
) {
    Column {
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = MeshlitThemeConfig.Default.fontScale.let { 0.85f..1.5f },
            steps = 12,
        )
        // Live preview — actually scales with the slider.
        Text(
            text = "Sample text at ${"%.2f".format(value)}x",
            fontSize = (16 * value).sp,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }
}

@Composable
private fun DensityScaleSlider(
    value: Float,
    onChange: (Float) -> Unit,
) {
    Column {
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = 0.85f..1.3f,
            steps = 8,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy((8 * value).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(5) { index ->
                Box(
                    modifier = Modifier
                        .size((12 * value).dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f + index * 0.12f)),
                )
            }
        }
    }
}

/**
 * Phase 12.2 — short description for the currently active
 * [CustomPalette]. Drives the "Custom color palette" LabeledBlock
 * subtitle in [ThemeCustomizationScreen].
 */
private fun customPaletteDescription(palette: CustomPalette): String = when (palette) {
    CustomPalette.None -> "Solid / gradient stops / animated gradient. Tap to design your own."
    is CustomPalette.Solid -> "Solid (5 swatches)"
    is CustomPalette.GradientStops -> "Static gradient (${palette.stops.size} stops, ${palette.angleDeg}°)"
    is CustomPalette.AnimatedGradient -> "Animated gradient (${palette.stops.size} stops, ${palette.cycleSeconds}s cycle, ${palette.angleDeg}°)"
}