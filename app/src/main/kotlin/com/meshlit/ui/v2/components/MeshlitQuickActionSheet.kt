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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meshlit.ui.components.MeshlitMark
import com.meshlit.ui.theme.MeshlitPulseViolet
import com.meshlit.ui.theme.MeshlitSurfaceContainer
import com.meshlit.ui.theme.MeshlitTextSecondaryV2

/**
 * Quick-action sheet — replaces the v1 sidebar's `QuickActionTile`
 * row. Triggered from the pill input's leading Menu button.
 *
 * Three large tiles: SYNC / BOOST / ABOUT. Each tile is a
 * `Surface(RoundedCornerShape(20.dp))` containing a small
 * `MeshlitMark` (24 dp) + a label + a short hint.
 *
 * The sheet's header reads "Quick actions" + "Tune the cluster
 * in one place." in the v2 type system (headlineSmall + bodySmall).
 *
 * The `onAction` callback fires the underlying `BoostViewModel` /
 * `SyncViewModel` / `MeshlitApp.about()` call the v1 sidebar would
 * have fired; the caller wires the right behavior for its screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshlitQuickActionSheet(
    onDismiss: () -> Unit,
    onAction: (QuickAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Quick actions",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Tune the cluster in one place.",
                style = MaterialTheme.typography.bodySmall,
                color = MeshlitTextSecondaryV2,
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                QuickAction.entries.forEach { action ->
                    QuickActionTile(
                        action = action,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onAction(action)
                            onDismiss()
                        },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * The three quick actions. The icon glyphs match the v1 sidebar's
 * `QuickActionTile`s so muscle memory carries over. The order
 * here (SYNC / BOOST / ABOUT) is the order they appear in the
 * sheet.
 */
enum class QuickAction { SYNC, BOOST, ABOUT }

@Composable
private fun QuickActionTile(
    action: QuickAction,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val (label, hint, icon) = when (action) {
        QuickAction.SYNC -> Triple("Sync", "Pull latest role + peers", Icons.Filled.Refresh)
        QuickAction.BOOST -> Triple("Boost", "Pre-warm inference paths", Icons.Filled.Bolt)
        QuickAction.ABOUT -> Triple("About", "Version + licenses", Icons.Filled.Info)
    }
    Surface(
        modifier = modifier
            .height(96.dp),
        color = MeshlitSurfaceContainer,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 1.dp,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            MeshlitMark(
                size = 24.dp,
                ringColor = MeshlitPulseViolet,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MeshlitTextSecondaryV2,
            )
        }
    }
}