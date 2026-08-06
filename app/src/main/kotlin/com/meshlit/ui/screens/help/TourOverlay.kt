package com.meshlit.ui.screens.help

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.meshlit.R

/**
 * First-visit popover. Fired by `MeshlitApp.kt` whenever the
 * user navigates to a destination whose route is not in
 * [com.meshlit.setup.FirstRunSetupRepository.tourSeenFlow].
 *
 * Why a Dialog rather than a Snackbar:
 *   - The blurb is 2-3 sentences; a Snackbar feels too transient.
 *   - The user can tap "Show me more" to deep-link into the full
 *     Tour screen.
 *   - "Got it" writes the route to the seen-set so the overlay
 *     never fires again for that destination.
 */
@Composable
fun TourOverlay(
    title: String,
    intent: String,
    useCase: String,
    onGotIt: () -> Unit,
    onShowMore: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shape = MaterialTheme.shapes.large,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Map,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.padding(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
                Spacer(Modifier.padding(8.dp))
                Text(
                    text = intent,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.padding(4.dp))
                Text(
                    text = useCase,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.padding(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onShowMore) {
                        Text(stringResource(R.string.help_overlay_show_more))
                    }
                    TextButton(onClick = onGotIt) {
                        Text(stringResource(R.string.help_overlay_got_it))
                    }
                }
            }
        }
    }
}