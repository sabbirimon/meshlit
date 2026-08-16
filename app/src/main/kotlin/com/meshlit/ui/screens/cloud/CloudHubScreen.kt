package com.meshlit.ui.screens.cloud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meshlit.R
import com.meshlit.core.cloudmcp.ProviderConfig
import com.meshlit.core.cloudmcp.ProviderKind
import com.meshlit.core.cloudmcp.rag.RagDecision
import com.meshlit.core.cloudmcp.rag.RagMode
import com.meshlit.di.koinInject
import com.meshlit.ui.components.MeshlitHeader
import com.meshlit.ui.components.RaListCard
import com.meshlit.ui.theme.RaBrandStrip

/**
 * Top-level Cloud Hub. Shows a horizontally-scrollable row of
 * provider cards (AWS / DO / Azure / GCP / Custom), a "Manage
 * providers" CTA, and a "Open Agent Terminal" button. The
 * RAG-mode chip is pinned to the header so the user always sees
 * which RAG backend is active.
 *
 * Tap a provider card → opens the Agent Terminal filtered to that
 * provider. Tap "Add Custom Cloud" → AddCustomCloudScreen. Tap
 * "Open Agent Terminal" → AgentTerminalScreen with all providers
 * in scope.
 */
@Composable
fun CloudHubScreen(
    onOpenDrawer: () -> Unit,
    onOpenAddCustom: () -> Unit,
    onOpenTerminal: (providerId: String?) -> Unit,
    ragMode: RagMode = RagMode.Auto,
    ragDecision: RagDecision? = null,
) {
    val context = LocalContext.current
    val tier: com.meshlit.capability.CapabilityTier = koinInject()

    Scaffold(
        topBar = {
            MeshlitHeader(
                title = stringResource(R.string.screen_cloud),
                subtitle = null,
                tier = tier,
                active = false,
                onOpenDrawer = onOpenDrawer,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // RunAnywhere brand gradient — orange → gold. Pinned
            // right under the header so every entry into the hub
            // carries the visual signature.
            RaBrandStrip(height = 6.dp, horizontal = true)

            // RAG indicator — always visible so the user sees
            // which backend is active.
            RagIndicatorChip(mode = ragMode, state = ragDecision)

            Text(
                text = stringResource(R.string.cloud_hub_connected),
                style = MaterialTheme.typography.headlineMedium,
            )

            // Horizontal slider of providers.
            LazyRow(
                contentPadding = PaddingValues(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items = defaultProviders(), key = { it.id }) { provider ->
                    ProviderCard(
                        provider = provider,
                        onClick = { onOpenTerminal(provider.id) },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.cloud_hub_agent_workspace),
                style = MaterialTheme.typography.titleMedium,
            )

            RaListCard(
                leadingIcon = Icons.Outlined.Cloud,
                title = stringResource(R.string.cloud_open_terminal),
                subtitle = stringResource(R.string.cloud_open_terminal_subtitle),
                onClick = { onOpenTerminal(null) },
            )

            RaListCard(
                leadingIcon = Icons.Outlined.Cloud,
                title = stringResource(R.string.cloud_add_custom),
                subtitle = stringResource(R.string.cloud_add_custom_subtitle),
                onClick = onOpenAddCustom,
            )
        }
    }
}

private fun defaultProviders(): List<ProviderConfig> = listOf(
    ProviderConfig(
        id = "aws",
        name = "AWS",
        kind = ProviderKind.AWS,
        baseUrl = "https://mcp.aws.example.com/sse",
        authKind = com.meshlit.core.cloudmcp.AuthKind.AwsIam,
        credentialRef = "aws/access_key_id",
    ),
    ProviderConfig(
        id = "do",
        name = "DigitalOcean",
        kind = ProviderKind.DigitalOcean,
        baseUrl = "https://mcp.digitalocean.example.com/sse",
        authKind = com.meshlit.core.cloudmcp.AuthKind.BearerToken,
        credentialRef = "do/token",
    ),
    ProviderConfig(
        id = "azure",
        name = "Azure",
        kind = ProviderKind.Azure,
        baseUrl = "https://mcp.azure.example.com/sse",
        authKind = com.meshlit.core.cloudmcp.AuthKind.OAuth2,
        credentialRef = "azure/access_token",
    ),
    ProviderConfig(
        id = "gcp",
        name = "Google Cloud",
        kind = ProviderKind.GoogleCloud,
        baseUrl = "https://mcp.gcp.example.com/sse",
        authKind = com.meshlit.core.cloudmcp.AuthKind.OAuth2,
        credentialRef = "gcp/access_token",
    ),
    ProviderConfig(
        id = "custom",
        name = "Custom",
        kind = ProviderKind.Custom,
        baseUrl = "",
        authKind = com.meshlit.core.cloudmcp.AuthKind.None,
        credentialRef = "",
    ),
)

@Composable
private fun ProviderCard(
    provider: ProviderConfig,
    onClick: () -> Unit,
) {
    val tone = when (provider.kind) {
        ProviderKind.AWS -> "AWS"
        ProviderKind.DigitalOcean -> "DO"
        ProviderKind.Azure -> "Az"
        ProviderKind.GoogleCloud -> "GCP"
        ProviderKind.Custom -> "?"
        ProviderKind.Llm -> "LLM"
        ProviderKind.WebSearch -> "Srch"
        ProviderKind.HttpTool -> "HTTP"
        ProviderKind.Browser -> "Br"
        ProviderKind.AndroidAutomation -> "droid"
    }
    androidx.compose.material3.Card(
        modifier = Modifier
            .fillMaxWidth(0.42f)
            .padding(vertical = 4.dp),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = tone,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = provider.name,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.cloud_provider_disconnected),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}