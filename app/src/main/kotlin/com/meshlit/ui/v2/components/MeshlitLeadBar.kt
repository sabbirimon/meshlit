package com.meshlit.ui.v2.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meshlit.ui.theme.MeshlitInk
import com.meshlit.ui.theme.MeshlitPulseViolet
import com.meshlit.ui.theme.MeshlitSurface
import com.meshlit.ui.theme.MeshlitSurfaceContainer
import com.meshlit.ui.theme.MeshlitTextPrimaryV2
import com.meshlit.ui.theme.MeshlitTextSecondaryV2

/**
 * The "bold-lead content" pattern. Every v2 top-level screen
 * uses this in place of the v1 `MeshlitHeader` (titleSmall
 * SemiBold + accent dot). Layout:
 *
 * ```
 *   ┃ Meshlit                              <-- 4 dp accent rail
 *   ┃ Devices                              <-- headlineMedium Bold
 *   ┃ Scope: LAN · 3 endpoints             <-- bodyLarge secondary
 * ```
 *
 * The accent rail is solid `MeshlitPulseViolet` (no animation) so
 * the lead stays crisp on every screen and the Pulse gradient
 * stays reserved for the larger surfaces (hero identity, pill
 * input glow). Height auto-sizes to the headline; minimum 56 dp
 * so even a one-line headline reads as a lead bar rather than a
 * thin sliver.
 *
 * Used by:
 *   - `ui/v2/screens/DevicesScreen.kt`
 *   - `ui/v2/screens/ClusterScreen.kt`
 *   - `ui/v2/screens/AgentScreen.kt`
 *   - `ui/v2/screens/SettingsScreen.kt`
 *
 * Pass an optional `trailing` slot for screen-level actions
 * (e.g. a refresh icon, an export button). The trailing slot
 * sits to the right of the headline column, vertically
 * centered, with 16 dp start-padding.
 */
@Composable
fun MeshlitLeadBar(
    headline: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    // Lead bar gets a slightly lifted surface tone so it
    // separates from the screen body below. Without this,
    // the headline + subtitle float directly on the ink
    // background and visually overlap with whatever the
    // screen body renders immediately underneath. The
    // rounded bottom corners (top edge stays flush with
    // whatever the screen stacks ahead — drawer, top bar,
    // BootstrapScreen) tell the user where the lead ends
    // and the content begins.
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MeshlitSurface,
        shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MeshlitSurface)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            // 4 dp accent rail — solid violet, sharp edges. Anchors
            // the lead to the Pulse gradient system without competing
            // with the larger gradient surfaces. Trimmed from 56 dp
            // to 44 dp so the lead doesn't dominate the chat
            // surface on the Jobs / Agent screens.
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(44.dp)
                    .background(MeshlitPulseViolet),
            )
            Spacer(Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MeshlitTextPrimaryV2,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MeshlitTextSecondaryV2,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(16.dp))
                trailing()
            }
        }
    }
}
