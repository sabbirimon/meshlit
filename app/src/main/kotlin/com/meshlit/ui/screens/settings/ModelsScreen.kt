package com.meshlit.ui.screens.settings

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Button
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
import com.meshlit.MeshlitApplication
import com.meshlit.R
import com.meshlit.capability.CapabilityBadge
import com.meshlit.core.inference.BundledModelInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Models settings screen. Reachable from Settings → Models and (in
 * future) from the top-level Models tab once the file picker flow
 * lands.
 *
 * Renders inside the parent [CategoryScreen]'s scaffold when reached
 * via Settings; the standalone route uses its own scaffold. Two
 * pieces of state live here:
 *  1. Custom GGUF path override — written to
 *     [com.meshlit.settings.SettingsRepository.setCustomModelPath].
 *  2. Bundled model extraction — surfaces the install state for
 *     the bundled Qwen2.5-1.5B-Instruct asset.
 *
 * Auto-load wiring lives in `InferenceForegroundService.onCreate`;
 * this screen is the read-side / settings-side UI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as MeshlitApplication }
    val scope = rememberCoroutineScope()

    val customPath by app.settingsRepository.customModelPathFlow
        .collectAsState(initial = "")
    var pathField by remember(customPath) { mutableStateOf(customPath) }
    var installStatus by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_models)) },
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
                .padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "tier") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.capability_tier_label),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    CapabilityBadge(app = app)
                }
            }

            item(key = "bundled-header") {
                SectionHeader(text = stringResource(R.string.models_bundled_section))
            }
            item(key = "bundled-card") {
                BundledModelCard(
                    app = app,
                    onReextract = { status ->
                        scope.launch {
                            installStatus = status
                            val result = runCatching {
                                withContext(Dispatchers.IO) {
                                    BundledModelInstaller().ensureInstalled(app)
                                }
                            }
                            installStatus = result.fold(
                                onSuccess = { file ->
                                    if (file != null) {
                                        app.resources.getString(
                                            R.string.models_reextract_done,
                                            file.absolutePath,
                                        )
                                    } else {
                                        app.resources.getString(R.string.models_no_asset)
                                    }
                                },
                                onFailure = { t ->
                                    app.resources.getString(
                                        R.string.models_reextract_failed,
                                        t.message ?: "unknown",
                                    )
                                },
                            )
                        }
                    },
                    status = installStatus,
                )
            }

            item(key = "override-header") {
                SectionHeader(text = stringResource(R.string.models_override_section))
            }
            item(key = "override-card") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.models_override_path_label),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.models_override_path_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = pathField,
                            onValueChange = { pathField = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.models_override_path_hint)) },
                            singleLine = true,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        app.settingsRepository.setCustomModelPath(pathField)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.models_override_save))
                            }
                            Button(
                                onClick = {
                                    pathField = ""
                                    scope.launch {
                                        app.settingsRepository.setCustomModelPath("")
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.models_override_clear))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun BundledModelCard(
    app: MeshlitApplication,
    onReextract: (String) -> Unit,
    status: String?,
) {
    val context = LocalContext.current
    val installer = remember { BundledModelInstaller() }
    val installed = remember { installer.installedFile(app) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Memory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.models_bundled_name),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.models_bundled_quant),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (installed != null) {
                    app.resources.getString(
                        R.string.models_bundled_installed,
                        installed.absolutePath,
                        installed.length(),
                    )
                } else {
                    stringResource(R.string.models_bundled_not_installed)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onReextract(context.getString(R.string.models_reextract_in_progress)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.models_reextract_button))
            }
            status?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
