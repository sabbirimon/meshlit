package com.meshlit.ui.v2.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshlit.disco.UsbTetherInfo
import com.meshlit.ui.theme.MeshlitPulseCoral
import com.meshlit.ui.theme.MeshlitSurfaceHigh
import com.meshlit.ui.theme.MeshlitTextPrimaryV2
import com.meshlit.ui.theme.MeshlitTextSecondaryV2
import com.meshlit.ui.theme.MeshlitTextTertiaryV2

/**
 * Coral-accented row rendered at the top of the v2 Scan list when
 * a USB-NCM / RNDIS tether is up. The row communicates "this peer
 * is reachable, but on USB — not the LAN". It's a passive read —
 * no tap action — so the user understands the row is a status
 * surface, not a button.
 *
 * Layout:
 *
 * | orange dot | USB tether                |
 * |            | host: 192.168.42.42       |
 * |            | MTU 1500 · 480 Mbps       |
 *
 * When the USB tether goes down the parent screen drops the row
 * from the `LazyColumn` entirely; this composable has no internal
 * hidden/expanded state.
 */
@Composable
fun UsbPeerRow(
    info: UsbTetherInfo,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MeshlitSurfaceHigh,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Usb,
                contentDescription = "USB tether",
                tint = MeshlitPulseCoral,
                modifier = Modifier.size(18.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "USB tether",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MeshlitTextPrimaryV2,
                )
                Text(
                    text = "host: ${info.host}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = MeshlitTextSecondaryV2,
                )
                Text(
                    text = "MTU ${info.mtu} · ${info.linkMbps} Mbps",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                    ),
                    color = MeshlitTextTertiaryV2,
                )
            }
        }
    }
}