package com.meshlit.ui.v2.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meshlit.ui.components.MeshlitMark
import com.meshlit.ui.v2.screens.PairingPayload
import com.meshlit.ui.theme.MeshlitOutlineV2
import com.meshlit.ui.theme.MeshlitPulseViolet
import com.meshlit.ui.theme.MeshlitSurfaceHigh
import com.meshlit.ui.theme.MeshlitTextPrimaryV2
import com.meshlit.ui.theme.MeshlitTextSecondaryV2
import com.meshlit.ui.theme.MeshlitTextTertiaryV2

/**
 * v2 pairing identity card. Renders a `PairingPayload` as a
 * MeshlitMark + descriptor + mono QR-string panel. Lives on the
 * `DevicesScreen` above the endpoint list.
 *
 * For build no. 1 the QR code renders as a mono `Text` (no ZXing
 * dependency) — the v1 build uses a real QR bitmap via
 * `PairingCard`. The plan defers the QR bitmap to the v2
 * follow-up (the visual grammar matches; only the bitmap
 * renderer changes).
 */
@Composable
fun MeshlitPairingIdentityCard(
    payload: PairingPayload,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MeshlitSurfaceHigh,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MeshlitMark(size = 40.dp)
                Spacer(Modifier.size(12.dp))
                Column {
                    Text(
                        text = "Pairing",
                        style = MaterialTheme.typography.labelLarge,
                        color = MeshlitTextTertiaryV2,
                    )
                    Text(
                        text = payload.descriptor,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MeshlitTextPrimaryV2,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Surface(
                color = androidx.compose.ui.graphics.Color.Transparent,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = payload.qrString.ifBlank { "(qr pending)" },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (payload.qrString.isBlank()) MeshlitTextTertiaryV2 else MeshlitTextSecondaryV2,
                    modifier = Modifier.padding(8.dp),
                )
            }
            // Outline accent
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .padding(top = 4.dp),
            )
        }
    }
}
