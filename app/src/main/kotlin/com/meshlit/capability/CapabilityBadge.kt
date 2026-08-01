package com.meshlit.capability

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meshlit.MeshlitApplication
import com.meshlit.R

/**
 * Small chip that surfaces the current device's [CapabilityTier].
 * Used on settings roots and on the Jobs screen status card so the
 * user can see what features are unlocked on their hardware.
 *
 * Style: tonal pill in the theme's secondary color, with the tier
 * name in the foreground.
 */
@Composable
fun CapabilityBadge(
    app: MeshlitApplication,
    modifier: Modifier = Modifier,
) {
    val tier = app.capabilityTier
    val labelRes = when (tier) {
        CapabilityTier.LITE -> R.string.capability_tier_lite
        CapabilityTier.MID -> R.string.capability_tier_mid
        CapabilityTier.FULL -> R.string.capability_tier_full
    }
    val containerColor = when (tier) {
        CapabilityTier.LITE -> MaterialTheme.colorScheme.surfaceVariant
        CapabilityTier.MID -> MaterialTheme.colorScheme.secondaryContainer
        CapabilityTier.FULL -> MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = when (tier) {
        CapabilityTier.LITE -> MaterialTheme.colorScheme.onSurfaceVariant
        CapabilityTier.MID -> MaterialTheme.colorScheme.onSecondaryContainer
        CapabilityTier.FULL -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}