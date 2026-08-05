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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.meshlit.MeshlitApplication
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
    onOpenDrawer: () -> Unit = {},
) {
    val context = LocalContext.current
    val title = titleOverride ?: stringResource(id = titleResFor(destination))
    val body = bodyOverride ?: stringResource(id = bodyResFor(destination))
    Scaffold(
        topBar = {
            com.meshlit.ui.components.MeshlitHeader(
                title = title,
                subtitle = null,
                tier = (context.applicationContext as MeshlitApplication).capabilityTier,
                active = false,
                onOpenDrawer = onOpenDrawer,
            )
        },
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
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
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = body,
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
}

private fun titleResFor(d: TopLevelDestination): Int = when (d) {
    TopLevelDestination.Devices -> com.meshlit.R.string.devices_empty_title
    TopLevelDestination.Jobs -> com.meshlit.R.string.jobs_empty_title
    TopLevelDestination.Agent -> com.meshlit.R.string.agent_empty_title
    TopLevelDestination.Models -> com.meshlit.R.string.models_empty_title
    TopLevelDestination.Files -> com.meshlit.R.string.files_empty_title
    TopLevelDestination.Sessions -> com.meshlit.R.string.sessions_empty_title
    TopLevelDestination.Cluster -> com.meshlit.R.string.cluster_empty_title
    TopLevelDestination.Network -> com.meshlit.R.string.network_empty_title
    TopLevelDestination.Users -> com.meshlit.R.string.users_empty_title
    TopLevelDestination.Settings -> com.meshlit.R.string.settings_about
    TopLevelDestination.Voice -> com.meshlit.R.string.voice_title
    TopLevelDestination.Structured -> com.meshlit.R.string.structured_title
    TopLevelDestination.Catalog -> com.meshlit.R.string.catalog_title
    TopLevelDestination.Vision -> com.meshlit.R.string.vision_title
    TopLevelDestination.Advanced -> com.meshlit.R.string.settings_about
    TopLevelDestination.Cloud -> com.meshlit.R.string.screen_cloud
}

private fun bodyResFor(d: TopLevelDestination): Int = when (d) {
    TopLevelDestination.Devices -> com.meshlit.R.string.devices_empty_body
    TopLevelDestination.Jobs -> com.meshlit.R.string.jobs_empty_body
    TopLevelDestination.Agent -> com.meshlit.R.string.agent_empty_body
    TopLevelDestination.Models -> com.meshlit.R.string.models_empty_body
    TopLevelDestination.Files -> com.meshlit.R.string.files_empty_body
    TopLevelDestination.Sessions -> com.meshlit.R.string.sessions_empty_body
    TopLevelDestination.Cluster -> com.meshlit.R.string.cluster_empty_body
    TopLevelDestination.Network -> com.meshlit.R.string.network_empty_body
    TopLevelDestination.Users -> com.meshlit.R.string.users_empty_body
    TopLevelDestination.Settings -> com.meshlit.R.string.app_tagline
    TopLevelDestination.Voice -> com.meshlit.R.string.voice_subtitle
    TopLevelDestination.Structured -> com.meshlit.R.string.structured_subtitle
    TopLevelDestination.Catalog -> com.meshlit.R.string.catalog_subtitle
    TopLevelDestination.Vision -> com.meshlit.R.string.vision_subtitle
    TopLevelDestination.Advanced -> com.meshlit.R.string.app_tagline
    TopLevelDestination.Cloud -> com.meshlit.R.string.cloud_hub_connected
}