package com.meshlit.ui.v2.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meshlit.ui.theme.MeshlitPulseViolet
import com.meshlit.ui.theme.MeshlitSurfaceContainer
import com.meshlit.ui.theme.MeshlitTextSecondaryV2

/**
 * Two-pill transport toggle for the v2 Scan screen. Renders
 * side-by-side pills labelled "mDNS" and "BLE" with the active
 * state matching the [enabled] set. Pure rendering — the caller
 * owns the state and reacts to [onToggle].
 *
 * The bleed-out focus is **clarity, not chrome**. The active
 * pill carries `MeshlitPulseViolet` background + on-violet text;
 * the inactive pill carries `MeshlitSurfaceContainer` background
 * + secondary text. No animations — these are quick switches
 * and the user expects the state flip to be immediate.
 *
 * @param enabled set of transport names that are currently on.
 *                Unknown names are silently ignored.
 * @param options ordered list of `(name, label)` pairs to render.
 * @param onToggle fired with the transport name as the user taps
 *                 a pill. Toggle semantics (on/off) live with the
 *                 caller; this composable just reports the tap.
 */
@Composable
fun MeshlitTransportSwitch(
    enabled: Set<String>,
    options: List<Pair<String, String>>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { (name, label) ->
            val on = name in enabled
            Pill(
                label = label,
                active = on,
                onClick = { onToggle(name) },
            )
        }
    }
}

@Composable
private fun Pill(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (active) MeshlitPulseViolet else MeshlitSurfaceContainer
    val fg = if (active) Color.White else MeshlitTextSecondaryV2
    Surface(
        color = bg,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold,
            ),
            color = fg,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}