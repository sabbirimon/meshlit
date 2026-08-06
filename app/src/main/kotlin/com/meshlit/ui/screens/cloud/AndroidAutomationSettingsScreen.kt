package com.meshlit.ui.screens.cloud

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meshlit.R
import com.meshlit.core.cloudmcp.android.AccessibilityServiceStatus
import com.meshlit.core.cloudmcp.android.AutomationRequest
import com.meshlit.core.cloudmcp.android.MeshlitAccessibilityService
import com.meshlit.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings → Cloud → Android automation. Master toggle +
 * service status + allowlist editor.
 *
 * When the master toggle flips on, the user is routed to
 * `Settings.ACTION_ACCESSIBILITY_SETTINGS` — the system prompt
 * is non-bypassable; we just hand off. The user enables the
 * service; on next launch, the Cloud Hub's Android card reads
 * `MeshlitAccessibilityService.isEnabled(context)` and renders
 * the active automation tools.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AndroidAutomationSettingsScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val enabled by settingsRepository.androidAutomationEnabledFlow
        .collectAsState(initial = false)
    val allowlist by settingsRepository.androidAutomationAllowlistFlow
        .collectAsState(initial = emptySet())
    val highRiskPackages by settingsRepository.androidAutomationHighRiskPackagesFlow
        .collectAsState(initial = SettingsRepository.defaultHighRiskPackages)
    val status = MeshlitAccessibilityService.currentStatus(context)
    var testStatusLine by remember { mutableStateOf<String?>(null) }
    var testStatusIsError by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cloud_android_settings)) },
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
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.cloud_android_master_toggle),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Allows Meshlit to read your screen and drive installed apps at your request.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { on ->
                        scope.launch {
                            settingsRepository.setAndroidAutomationEnabled(on)
                            if (on) {
                                // Route to system Accessibility
                                // settings so the user can enable
                                // the service.
                                val intent = Intent(
                                    Settings.ACTION_ACCESSIBILITY_SETTINGS,
                                ).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            }
                        }
                    },
                )
            }

            Card {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val statusText = when (status) {
                        is AccessibilityServiceStatus.Enabled ->
                            stringResource(R.string.cloud_android_service_enabled)
                        is AccessibilityServiceStatus.Disabled ->
                            stringResource(R.string.cloud_android_service_disabled)
                        is AccessibilityServiceStatus.Missing ->
                            stringResource(R.string.cloud_android_service_missing)
                    }
                    Text(
                        stringResource(R.string.cloud_android_service_status, statusText),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (status is AccessibilityServiceStatus.Enabled) {
                        Text(
                            status.serviceName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Text(
                stringResource(R.string.cloud_android_allowlist),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                "Packages whose actions are auto-allowed without confirmation. " +
                    "Total: ${allowlist.size}",
                style = MaterialTheme.typography.bodySmall,
            )
            allowlist.take(8).forEach { pkg ->
                Text("• $pkg", style = MaterialTheme.typography.bodySmall)
            }

            Text(
                stringResource(R.string.cloud_android_high_risk),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                "High-risk packages always require explicit per-action confirmation. " +
                    "Total: ${highRiskPackages.size}",
                style = MaterialTheme.typography.bodySmall,
            )
            highRiskPackages.take(8).forEach { pkg ->
                Text("• $pkg", style = MaterialTheme.typography.bodySmall)
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.cloud_android_open_settings))
                }
                Button(
                    onClick = {
                        scope.launch {
                            // Dispatch an `app_snapshot` request
                            // via the bound service instance. The
                            // dispatch is synchronous on the
                            // service thread — wrap it in `withContext(Dispatchers.Default)`
                            // so the click handler doesn't block.
                            val service = MeshlitAccessibilityService.instance
                            if (service == null) {
                                testStatusLine = "Service not bound — enable it in Accessibility Settings first"
                                testStatusIsError = true
                                return@launch
                            }
                            testStatusLine = "Testing…"
                            testStatusIsError = false
                            val response = withContext(Dispatchers.Default) {
                                service.dispatch(
                                    AutomationRequest.Snapshot(targetPackage = ""),
                                )
                            }
                            testStatusLine = when {
                                response.ok -> "Snapshot captured — accessibility tools wired correctly"
                                response.error == "no active window" ->
                                    "Bridge is alive but no app is in the foreground — open an app first"
                                else -> "Failed: ${response.error ?: "unknown"}"
                            }
                            testStatusIsError = !response.ok
                        }
                    },
                    enabled = status is AccessibilityServiceStatus.Enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.cloud_android_test_action))
                }
            }
            testStatusLine?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (testStatusIsError) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}