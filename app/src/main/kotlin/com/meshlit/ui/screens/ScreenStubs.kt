package com.meshlit.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.meshlit.ui.nav.TopLevelDestination

/**
 * Phase 0 stub used by every tab that hasn't yet shipped its real UI.
 * Shows the screen title, an icon, and a copy explaining what's coming.
 */
@Composable
fun ScreenStub(
    destination: TopLevelDestination,
    icon: ImageVector,
    titleOverride: String? = null,
    bodyOverride: String? = null,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                modifier = Modifier.size(96.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = titleOverride ?: stringResource(id = titleResFor(destination)),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = bodyOverride ?: stringResource(id = bodyResFor(destination)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            Text(
                text = "Phase 0 — empty-state scaffold",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

private fun titleResFor(d: TopLevelDestination): Int = when (d) {
    TopLevelDestination.Devices -> com.meshlit.R.string.devices_empty_title
    TopLevelDestination.Jobs -> com.meshlit.R.string.jobs_empty_title
    TopLevelDestination.Models -> com.meshlit.R.string.models_empty_title
    TopLevelDestination.Files -> com.meshlit.R.string.files_empty_title
    TopLevelDestination.Sessions -> com.meshlit.R.string.sessions_empty_title
    TopLevelDestination.Cluster -> com.meshlit.R.string.cluster_empty_title
    TopLevelDestination.Network -> com.meshlit.R.string.network_empty_title
    TopLevelDestination.Users -> com.meshlit.R.string.users_empty_title
    TopLevelDestination.Settings -> com.meshlit.R.string.settings_about
}

private fun bodyResFor(d: TopLevelDestination): Int = when (d) {
    TopLevelDestination.Devices -> com.meshlit.R.string.devices_empty_body
    TopLevelDestination.Jobs -> com.meshlit.R.string.jobs_empty_body
    TopLevelDestination.Models -> com.meshlit.R.string.models_empty_body
    TopLevelDestination.Files -> com.meshlit.R.string.files_empty_body
    TopLevelDestination.Sessions -> com.meshlit.R.string.sessions_empty_body
    TopLevelDestination.Cluster -> com.meshlit.R.string.cluster_empty_body
    TopLevelDestination.Network -> com.meshlit.R.string.network_empty_body
    TopLevelDestination.Users -> com.meshlit.R.string.users_empty_body
    TopLevelDestination.Settings -> com.meshlit.R.string.app_tagline
}