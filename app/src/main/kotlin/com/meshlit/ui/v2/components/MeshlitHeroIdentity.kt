package com.meshlit.ui.v2.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meshlit.ui.components.MeshlitMark
import com.meshlit.ui.theme.MeshlitInk
import com.meshlit.ui.theme.MeshlitPulseViolet
import com.meshlit.ui.theme.MeshlitSurfaceContainer
import com.meshlit.ui.theme.MeshlitTextSecondaryV2
import com.meshlit.ui.theme.MeshlitTextTertiaryV2

/**
 * Drawer header card — the v2 answer to v1's `HeroBanner`.
 *
 * Fills the top 200 dp of the drawer. Layout:
 * ```
 *   ┌─────────────────────────────────────────┐
 *   │   ◯  Meshlit                            │
 *   │   mark Distributed inference, on-device │
 *   │        [Brain · 83%]  [nodeId…  ]       │
 *   └─────────────────────────────────────────┘
 * ```
 *
 * The mark is the new `MeshlitMark` Compose component (the
 * three-ring geometry from `drawable/ic_launcher_foreground.xml`,
 * redrawn on a Canvas). The role pill uses the Pulse violet
 * tint when the role is `Brain` and falls back to the surface
 * container for the other roles. The node ID chip uses the
 * existing Maple Mono font (`MeshlitMapleMono`) so the long
 * UUID stays readable at small sizes.
 *
 * The identity card is intentionally NOT animated itself — the
 * mark pulses via `rememberMeshlitPulsePhase()` (called inside
 * `MeshlitMark`) and the rest is static. Keeps the drawer
 * feeling anchored rather than busy.
 *
 * @param role display name (e.g. "Brain", "Tool", "Idle").
 * @param confidence 0..1 — rendered as a percentage next to the
 *        role name. Pass `null` to hide the percentage.
 * @param nodeId the mesh node identifier; truncated to last 8
 *        characters with a leading ellipsis so the chip stays
 *        narrow on phones.
 */
@Composable
fun MeshlitHeroIdentity(
    role: String,
    confidence: Float? = null,
    nodeId: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
        color = MeshlitInk,
        shape = RoundedCornerShape(0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MeshlitMark(size = 88.dp)
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "Meshlit",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "Distributed inference, on-device",
                        style = MaterialTheme.typography.titleMedium,
                        color = MeshlitTextSecondaryV2,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Role + confidence pill (left) and nodeId chip (right).
            // The row sits at the bottom of the card; the mark +
            // title block stacks above. Two pills in one row keeps
            // the card at exactly 200 dp on every screen size.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    color = MeshlitPulseViolet.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        text = if (confidence != null) {
                            "$role · ${(confidence * 100).toInt()}%"
                        } else {
                            role
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MeshlitPulseViolet,
                    )
                }

                Surface(
                    color = MeshlitSurfaceContainer,
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        text = "…${nodeId.takeLast(8)}",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = com.meshlit.ui.theme.MeshlitMapleMono,
                        ),
                        color = MeshlitTextTertiaryV2,
                    )
                }
            }
        }
    }
}
