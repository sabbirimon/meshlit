package com.meshlit.ui.screens.help

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meshlit.R
import com.meshlit.setup.FirstRunSetupRepository
import com.meshlit.ui.nav.TopLevelDestination
import kotlinx.coroutines.launch

/**
 * Phase Observability 1 — UI Tour. Lists every top-level
 * destination with a 2-3 sentence blurb. Lets the user "Mark as
 * seen" to clear the first-visit overlay for that screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UiTourScreen(
    firstRun: FirstRunSetupRepository,
    onBack: () -> Unit,
    onOpenDestination: (TopLevelDestination) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val tourSeenFlow = remember { firstRun.tourSeenFlow }
    val seen by tourSeenFlow.collectAsStateCompat(initial = emptySet())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.help_tour_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    AssistChip(
                        onClick = { scope.launch { firstRun.resetTour() } },
                        label = { Text(stringResource(R.string.help_tour_reset)) },
                    )
                },
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(TopLevelDestination.all, key = { it.route }) { dest ->
                val isSeen = dest.route in seen
                TourCard(
                    icon = dest.icon,
                    title = stringResource(dest.labelRes),
                    intent = blurbFor(dest),
                    useCase = useCaseFor(dest),
                    seen = isSeen,
                    onOpen = { onOpenDestination(dest) },
                    onMarkSeen = { scope.launch { firstRun.markTourSeen(dest.route) } },
                )
            }
        }
    }
}

@Composable
private fun TourCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    intent: String,
    useCase: String,
    seen: Boolean,
    onOpen: () -> Unit,
    onMarkSeen: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (seen) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape),
            )
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    text = intent,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    text = useCase,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AssistChip(
                        onClick = onOpen,
                        label = { Text(stringResource(R.string.help_tour_blurb_open)) },
                    )
                    if (!seen) {
                        AssistChip(
                            onClick = onMarkSeen,
                            label = { Text(stringResource(R.string.help_tour_blurb_mark_seen)) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Short blurb per destination. Two-three sentences describing what
 * the screen does and when to use it. The Tour screen surfaces
 * this verbatim; the first-visit overlay shows it as a popover.
 */
private fun blurbFor(dest: TopLevelDestination): String = when (dest) {
    TopLevelDestination.Devices -> "Find nearby Meshlit nodes and pair with them."
    TopLevelDestination.Jobs -> "Run an inference job against the active model — pick Local / Remote / Cluster dispatch."
    TopLevelDestination.Voice -> "Speak a prompt, hear a spoken reply."
    TopLevelDestination.Agent -> "Multi-turn agentic loop that calls MCP tools and on-device capabilities."
    TopLevelDestination.Models -> "Browse, download, and switch the on-device language model."
    TopLevelDestination.Advanced -> "Power-user surface — runtime switching, engine config, experimental flags."
    TopLevelDestination.Files -> "Browse internal storage + SAF volumes; copy / move / delete / share."
    TopLevelDestination.Sessions -> "Persistent shell sessions over the local sandbox."
    TopLevelDestination.Cluster -> "Aggregate metrics across every paired Meshlit node."
    TopLevelDestination.Network -> "Inspect Meshlit's HTTP traffic + optionally capture device-wide packets."
    TopLevelDestination.Users -> "Manage users on this node (capability tier + audit log)."
    TopLevelDestination.Settings -> "Hub for theme, notifications, cluster, models, account, performance, privacy, developer, about."
    TopLevelDestination.Structured -> "Prompt the model with a JSON schema and read structured output back."
    TopLevelDestination.Vision -> "Send an image to the multimodal model and ask a question about it."
    TopLevelDestination.Catalog -> "Browse the full RunAnywhere model registry."
    TopLevelDestination.Cloud -> "Manage cloud providers, tool adapters, and on-device agent capabilities."
    TopLevelDestination.Help -> "User manual, UI tour, and the Send Feedback button."
}

private fun useCaseFor(dest: TopLevelDestination): String = when (dest) {
    TopLevelDestination.Devices -> "Pair with a more powerful peer for distributed inference."
    TopLevelDestination.Jobs -> "The default screen — open it whenever you want to prompt the model."
    TopLevelDestination.Voice -> "Hands-free interaction with the model."
    TopLevelDestination.Agent -> "Tasks that need the model to act — read a file, send a message, fetch GPS."
    TopLevelDestination.Models -> "Pick a smaller model for speed or a larger one for quality."
    TopLevelDestination.Advanced -> "Change the inference engine or try a new runtime."
    TopLevelDestination.Files -> "Manage the model library, log exports, screenshots."
    TopLevelDestination.Sessions -> "Run a real terminal session inside Meshlit."
    TopLevelDestination.Cluster -> "See queue depth, success rate, tokens/sec across the cluster."
    TopLevelDestination.Network -> "Debug Remote / Cluster inference calls; export .pcap files."
    TopLevelDestination.Users -> "Multi-user setups with different roles."
    TopLevelDestination.Settings -> "Change how Meshlit behaves."
    TopLevelDestination.Structured -> "Extract entities, fill forms, build typed records."
    TopLevelDestination.Vision -> "OCR, scene description, identifying objects."
    TopLevelDestination.Catalog -> "Discover new models; pull info / status / delete."
    TopLevelDestination.Cloud -> "Configure the LLM endpoint, RAG, MCP servers."
    TopLevelDestination.Help -> "When you want to know what a feature does or report a bug."
}

@Composable
private fun <T> kotlinx.coroutines.flow.Flow<T>.collectAsStateCompat(initial: T): androidx.compose.runtime.State<T> {
    val state = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(initial) }
    androidx.compose.runtime.LaunchedEffect(this) {
        this@collectAsStateCompat.collect { state.value = it }
    }
    return state
}