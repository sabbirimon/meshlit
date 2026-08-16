package com.meshlit.ui.screens.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshlit.design.MeshlitDesignPalette
import com.meshlit.design.MeshlitGlassCard
import com.meshlit.design.StitchPalette
import com.meshlit.design.stitchDropShadow
import com.meshlit.design.stitchPulseGlow

/**
 * Stitch-parity Settings hub. Surfaces:
 *   - 3 trust tiers (LAN / Temporary-local / WAN) with capability flags
 *   - Telemetry mode (Off / Local / OTel)
 *   - Foreground service toggle + thermal limit + RAM governor
 *
 * Mirror of `SettingsView.tsx` from the Stitch source. Uses
 * design-system tokens: `iridescentEnd` (LAN emerald), `iridescentStart`
 * (Guest cyan), `iridescentPink` (WAN pink), and the iridescent
 * gradient on every slider/toggle track.
 */
@Composable
fun MeshlitV2SettingsScreen(palette: StitchPalette = StitchPalette.DARK) {
    var selectedTier by remember { mutableStateOf(TrustTier.LAN) }
    var telemetryMode by remember { mutableStateOf(TelemetryMode.Off) }
    var foregroundServiceEnabled by remember { mutableStateOf(true) }
    var thermalLimitC by remember { mutableStateOf(41) }
    var ramGovernorGb by remember { mutableStateOf(6.0f) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Column {
                Text(
                    text = "Meshlit Cluster Settings",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = "Trust tiers, runtime memory governor, zero-telemetry controls",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        item {
            MeshlitGlassCard(
                palette = palette,
                modifier = Modifier
                    .fillMaxWidth()
                    .stitchDropShadow(color = MeshlitDesignPalette.Dark.glassShadowAmbient, cornerRadius = 24.dp),
            ) {
                Column {
                    Text(
                        text = "Trust Tier",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    TrustTier.values().forEach { tier ->
                        TrustTierRow(
                            tier = tier,
                            selected = tier == selectedTier,
                            onSelect = { selectedTier = tier },
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        item {
            MeshlitGlassCard(
                palette = palette,
                modifier = Modifier
                    .fillMaxWidth()
                    .stitchDropShadow(color = MeshlitDesignPalette.Dark.glassShadowAmbient, cornerRadius = 24.dp),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Radio,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Telemetry Mode",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TelemetryMode.values().forEach { mode ->
                            PillChoice(
                                label = mode.label,
                                icon = mode.icon,
                                selected = mode == telemetryMode,
                                onClick = { telemetryMode = mode },
                            )
                        }
                    }
                }
            }
        }

        item {
            MeshlitGlassCard(
                palette = palette,
                modifier = Modifier
                    .fillMaxWidth()
                    .stitchDropShadow(color = MeshlitDesignPalette.Dark.glassShadowAmbient, cornerRadius = 24.dp),
            ) {
                Column {
                    ToggleRow(
                        icon = Icons.Outlined.Bolt,
                        title = "Foreground Service",
                        subtitle = "Keep inference alive when the screen is off",
                        checked = foregroundServiceEnabled,
                        onCheckedChange = { foregroundServiceEnabled = it },
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SliderRow(
                        icon = Icons.Outlined.Thermostat,
                        title = "Thermal Limit",
                        valueLabel = "${thermalLimitC}°C",
                        valueFraction = (thermalLimitC - 30).toFloat() / 25f,
                        onChange = { f -> thermalLimitC = (30 + (f * 25)).toInt() },
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SliderRow(
                        icon = Icons.Outlined.Memory,
                        title = "RAM Governor",
                        valueLabel = "${"%.1f".format(ramGovernorGb)} GB",
                        valueFraction = ramGovernorGb / 12f,
                        onChange = { f -> ramGovernorGb = (f * 12f).coerceIn(1f, 12f) },
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Phase 4 — OpenRouter cloud-provider fallback.
        // The card is presentational; the host activity pulls the
        // current status from the [OpenRouterModelBrowserViewModel]
        // and passes the callbacks in. For now we render the
        // not-configured state so the user can paste their key.
        item {
            OpenRouterSettingsCard(
                palette = MeshlitDesignPalette,
                status = OpenRouterStatus.NotConfigured,
                onSave = { /* wired by host activity */ },
                onDisconnect = { /* wired by host activity */ },
                onRetryValidation = { /* wired by host activity */ },
                onPickModel = { /* wired by host activity */ },
                currentModelDisplayName = null,
            )
        }
    }
}

enum class TrustTier(
    val label: String,
    val description: String,
    val color: Color,
    val icon: ImageVector,
    val allowInference: Boolean,
    val allowFileRead: Boolean,
    val allowRemoteExecution: Boolean,
    val requiresPin: Boolean,
) {
    LAN(
        label = "LAN (Trusted Local)",
        description = "Same Wi-Fi subnet. Full inference + shard sync without PIN.",
        color = MeshlitDesignPalette.iridescentEnd,
        icon = Icons.Outlined.Shield,
        allowInference = true,
        allowFileRead = true,
        allowRemoteExecution = false,
        requiresPin = false,
    ),
    TemporaryLocal(
        label = "Temporary-Local (Guest)",
        description = "QR-paired phone nearby. 1-hour expiration. No file ops.",
        color = MeshlitDesignPalette.iridescentStart,
        icon = Icons.Outlined.Radio,
        allowInference = true,
        allowFileRead = false,
        allowRemoteExecution = false,
        requiresPin = true,
    ),
    WAN(
        label = "WAN (Tailscale/VPN)",
        description = "Remote node over WireGuard tunnel. mTLS, read-only telemetry.",
        color = MeshlitDesignPalette.iridescentPink,
        icon = Icons.Outlined.Shield,
        allowInference = true,
        allowFileRead = false,
        allowRemoteExecution = false,
        requiresPin = true,
    ),
}

enum class TelemetryMode(val label: String, val icon: ImageVector) {
    Off("Off", Icons.Outlined.CheckCircle),
    Local("Local", Icons.Outlined.Memory),
    Otel("OTel", Icons.Outlined.Bolt),
}

@Composable
private fun TrustTierRow(
    tier: TrustTier,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val borderColor by animateColorAsState(
        targetValue = if (selected) tier.color.copy(alpha = 0.65f)
                      else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = tween(250),
        label = "tier-border",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) tier.color.copy(alpha = 0.08f) else Color.Transparent,
            )
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(tier.color),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tier.label,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
            Text(
                text = tier.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
        }
        Icon(
            imageVector = tier.icon,
            contentDescription = null,
            tint = tier.color,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun PillChoice(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = MeshlitDesignPalette.iridescentStart
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) accent.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            )
            .border(
                width = 1.dp,
                color = if (selected) accent else Color.Transparent,
                shape = RoundedCornerShape(50),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = label,
                color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
        }
        // Iridescent-gradient toggle (cyan→purple→emerald) matching
        // the brand tile in Stitch.
        Box(
            modifier = Modifier
                .size(width = 40.dp, height = 22.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    if (checked) Brush.horizontalGradient(
                        colors = listOf(
                            MeshlitDesignPalette.iridescentStart,
                            MeshlitDesignPalette.iridescentMid,
                            MeshlitDesignPalette.iridescentEnd,
                        ),
                    )
                    else Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ),
                )
                .stitchPulseGlow(
                    enabled = checked,
                    cyan = MeshlitDesignPalette.iridescentStart,
                    purple = MeshlitDesignPalette.iridescentMid,
                )
                .clickable { onCheckedChange(!checked) },
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

@Composable
private fun SliderRow(
    icon: ImageVector,
    title: String,
    valueLabel: String,
    valueFraction: Float,
    onChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
                Text(
                    text = valueLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(valueFraction.coerceIn(0f, 1f))
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MeshlitDesignPalette.iridescentStart,
                                    MeshlitDesignPalette.iridescentMid,
                                    MeshlitDesignPalette.iridescentEnd,
                                ),
                            ),
                        ),
                )
            }
        }
    }
}