package com.meshlit.ui.v2.components

import androidx.compose.foundation.background
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
import com.meshlit.core.common.RemoteEndpoint
import com.meshlit.ui.theme.MeshlitOutlineV2
import com.meshlit.ui.theme.MeshlitPulseViolet
import com.meshlit.ui.theme.MeshlitSurfaceContainer
import com.meshlit.ui.theme.MeshlitTextPrimaryV2
import com.meshlit.ui.theme.MeshlitTextSecondaryV2

/**
 * v2 endpoint row. Renders a single `RemoteEndpoint` as a card
 * with the MeshlitPulseViolet left-rail (4 dp) when the row is
 * selected. The active endpoint is highlighted by the
 * `DevicesScreen` view-model passing `isActive = endpoint.id ==
 * activeEndpointId`.
 *
 * Used by `DevicesScreen` inside its `LazyColumn` of endpoints
 * (per the plan's §4 DevicesScreen bullet). Tap handling is left
 * to the parent screen — this composable just emits `onClick`.
 */
@Composable
fun MeshlitEndpointCard(
    endpoint: RemoteEndpoint,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = if (isActive) MeshlitSurfaceContainer
        else androidx.compose.ui.graphics.Color.Transparent,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 12.dp),
        ) {
            // Left rail — violet when active.
            Spacer(
                modifier = Modifier
                    .size(width = 4.dp, height = 28.dp)
                    .background(
                        color = if (isActive) MeshlitPulseViolet
                        else androidx.compose.ui.graphics.Color.Transparent,
                        shape = RoundedCornerShape(2.dp),
                    ),
            )
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = endpoint.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = MeshlitTextPrimaryV2,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = endpoint.baseUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MeshlitTextSecondaryV2,
                )
            }
        }
    }
    // The outline is rendered by the parent; no separate divider
    // here so active rows don't double up.
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MeshlitOutlineV2.copy(alpha = 0.2f)),
    )
}
