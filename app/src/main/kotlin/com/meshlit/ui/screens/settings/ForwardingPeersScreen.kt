package com.meshlit.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meshlit.R
import com.meshlit.di.koinInject
import com.meshlit.inference.PeerRegistry
import kotlinx.coroutines.launch

/**
 * Settings → Network → Forwarding peers screen.
 *
 * Lets the user add/remove IPs that the embedded inference server
 * forwards to when the local coordinator is busy or unable to serve
 * the request. Phase 1 keeps it small: a single text field + a list
 * of currently-configured peers.
 *
 * Persistence: [PeerRegistry] is DataStore-backed; this screen
 * writes through `registry.add/remove`. Changes are picked up live
 * by the FGS-owned router because [PeerRegistry.peers] is a `Flow`.
 *
 * Why manual IPs and not discovery (yet):
 *  - Phase 1 has no NSD/mDNS yet.
 *  - Phase 2's discovery replaces this with a node picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForwardingPeersScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val registry: com.meshlit.inference.PeerRegistry = koinInject()
    val scope = rememberCoroutineScope()

    val peers by registry.peers.collectAsState(initial = emptyList())
    var newIp by remember { mutableStateOf("") }
    var invalidFlash by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_network_forwarding_peers)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_search_clear),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_network_forwarding_peers_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )

            // Add row.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = newIp,
                    onValueChange = {
                        newIp = it
                        invalidFlash = null
                    },
                    label = { Text(stringResource(R.string.jobs_remote_ip_label)) },
                    placeholder = { Text(stringResource(R.string.jobs_remote_ip_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        val candidate = newIp.trim()
                        if (PeerRegistry.normalize(candidate) == null) {
                            invalidFlash = candidate
                            return@IconButton
                        }
                        scope.launch {
                            registry.add(candidate)
                            newIp = ""
                        }
                    },
                    enabled = newIp.isNotBlank(),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.settings_network_add_peer),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            invalidFlash?.let { bad ->
                Text(
                    text = "Invalid IP: $bad",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            HorizontalDivider()

            if (peers.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Text(
                        text = stringResource(R.string.settings_network_forwarding_peers_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 32.dp),
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    items(peers, key = { it }) { ip ->
                        PeerRow(
                            ip = ip,
                            onRemove = { scope.launch { registry.remove(ip) } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PeerRow(
    ip: String,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 8.dp),
        ) {
            Text(
                text = ip,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}