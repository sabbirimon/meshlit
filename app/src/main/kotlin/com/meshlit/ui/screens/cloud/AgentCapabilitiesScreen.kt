package com.meshlit.ui.screens.cloud

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.meshlit.R
import com.meshlit.agent.AgentCapabilityRegistryHolder
import com.meshlit.core.cloudmcp.agent.AgentCapability
import com.meshlit.settings.SettingsRepository
import kotlinx.coroutines.launch

/**
 * Settings → Cloud → Agent capabilities.
 *
 * One row per [AgentCapability]. Each row carries:
 *  - The capability's title and risk-class.
 *  - A master toggle (`feature.cloud.agent.<tag>`).
 *  - When the toggle is on AND the capability requires a runtime
 *    permission that isn't yet granted, a "Request permission"
 *    button fires `ActivityResultContracts.RequestPermission`.
 *  - For high-risk capabilities (`Sms`, `Storage`), an allowlist
 *    editor: SMS lists phone numbers, Storage lists tree URIs.
 *
 * **Why this screen exists separately from the Cloud Hub:**
 * The Cloud Hub shows the high-level agent state ("6 capabilities
 * enabled, 1 awaiting permission"). The Settings screen is where
 * the user actually flips individual capabilities. The Agent
 * Terminal surfaces a confirmation dialog per action regardless
 * of what's flipped here — this screen is the trust gate, the
 * dialog is the per-action gate.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentCapabilitiesScreen(
    settingsRepository: SettingsRepository,
    holder: AgentCapabilityRegistryHolder,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cloud_agent_capabilities_title)) },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.cloud_agent_capabilities_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(AgentCapability.entries.toList()) { cap ->
                CapabilityCard(
                    capability = cap,
                    settingsRepository = settingsRepository,
                    holder = holder,
                )
            }
        }
    }
}

@Composable
private fun CapabilityCard(
    capability: AgentCapability,
    settingsRepository: SettingsRepository,
    holder: AgentCapabilityRegistryHolder,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val enabled by settingsRepository.agentCapabilityEnabledFlow(capability.tag)
        .collectAsState(initial = false)
    val allowlist by settingsRepository.agentCapabilityAllowlistFlow(capability.tag)
        .collectAsState(initial = emptySet())
    val permissionGranted = remember(enabled) {
        // Re-read each time `enabled` flips because the holder
        // refreshes the registry when the user grants.
        val perm = capability.permission
        if (perm == null) true
        else ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { _ ->
        holder.refreshPermission(capability)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (capability.riskLabel) {
                AgentCapability.Risk.HIGH -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                AgentCapability.Risk.MEDIUM -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                AgentCapability.Risk.LOW -> MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = capability.icon(),
                    contentDescription = null,
                    modifier = Modifier.padding(end = 12.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = capability.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(
                            when (capability.riskLabel) {
                                AgentCapability.Risk.LOW -> R.string.cloud_risk_low
                                AgentCapability.Risk.MEDIUM -> R.string.cloud_risk_medium
                                AgentCapability.Risk.HIGH -> R.string.cloud_risk_high
                            },
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { desired ->
                        scope.launch {
                            settingsRepository.setAgentCapabilityEnabled(
                                tag = capability.tag,
                                enabled = desired,
                            )
                        }
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = capability.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (enabled) {
                Spacer(Modifier.height(8.dp))
                when (capability) {
                    AgentCapability.Camera,
                    AgentCapability.Microphone,
                    AgentCapability.Location,
                    AgentCapability.Sms -> {
                        // Permission required row.
                        if (capability.permission != null && !permissionGranted) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = stringResource(R.string.cloud_agent_permission_required),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                                Button(
                                    onClick = {
                                        capability.permission?.let {
                                            permissionLauncher.launch(it)
                                        }
                                    },
                                ) {
                                    Text(stringResource(R.string.cloud_agent_grant_permission))
                                }
                            }
                        } else {
                            Text(
                                text = stringResource(R.string.cloud_agent_permission_granted),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    AgentCapability.DataState,
                    AgentCapability.Call -> {
                        Text(
                            text = stringResource(R.string.cloud_agent_no_permission_required),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    AgentCapability.Storage -> {
                        StorageAllowlistEditor(
                            allowlist = allowlist,
                            onAdd = { uri ->
                                scope.launch {
                                    settingsRepository.addAgentCapabilityAllowlistEntry(
                                        tag = AgentCapability.Storage.tag,
                                        entry = uri,
                                    )
                                }
                            },
                            onRemove = { uri ->
                                scope.launch {
                                    settingsRepository.removeAgentCapabilityAllowlistEntry(
                                        tag = AgentCapability.Storage.tag,
                                        entry = uri,
                                    )
                                }
                            },
                        )
                    }
                }
                if (capability == AgentCapability.Sms) {
                    SmsAllowlistEditor(
                        allowlist = allowlist,
                        onAdd = { number ->
                            settingsRepository.addAgentCapabilityAllowlistEntry(
                                tag = AgentCapability.Sms.tag,
                                entry = number,
                            )
                        },
                        onRemove = { number ->
                            settingsRepository.removeAgentCapabilityAllowlistEntry(
                                tag = AgentCapability.Sms.tag,
                                entry = number,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SmsAllowlistEditor(
    allowlist: Set<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    var newNumber by remember { mutableStateOf("") }
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = stringResource(R.string.cloud_agent_sms_allowlist_label),
            style = MaterialTheme.typography.labelLarge,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newNumber,
                onValueChange = { newNumber = it },
                placeholder = { Text("+15551234567") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    val trimmed = newNumber.trim()
                    if (trimmed.isNotBlank()) {
                        onAdd(trimmed)
                        newNumber = ""
                    }
                },
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
            }
        }
        allowlist.toList().sorted().forEach { number ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = number,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onRemove(number) }) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun StorageAllowlistEditor(
    allowlist: Set<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val treePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            // Take persistable permission and add to allowlist.
            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, flags)
            }
            scope.launch {
                onAdd(uri.toString())
            }
        }
    }
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = stringResource(R.string.cloud_agent_storage_allowlist_label),
            style = MaterialTheme.typography.labelLarge,
        )
        OutlinedButton(
            onClick = {
                treePickerLauncher.launch(null)
            },
        ) {
            Icon(Icons.Filled.Folder, contentDescription = null)
            Spacer(Modifier.height(0.dp))
            Text(stringResource(R.string.cloud_agent_storage_grant))
        }
        allowlist.forEach { uri ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = uri.takeLast(48),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onRemove(uri) }) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                }
            }
        }
    }
}

private fun AgentCapability.icon(): ImageVector = when (this) {
    AgentCapability.Camera -> Icons.Filled.PhotoCamera
    AgentCapability.Microphone -> Icons.Filled.Mic
    AgentCapability.Location -> Icons.Filled.Place
    AgentCapability.DataState -> Icons.Filled.Wifi
    AgentCapability.Call -> Icons.Filled.Phone
    AgentCapability.Sms -> Icons.Filled.Sms
    AgentCapability.Storage -> Icons.Filled.Storage
}