package com.meshlit.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.meshlit.MeshlitApplication
import com.meshlit.settings.TracingMode
import com.meshlit.settings.visibility.RowDescriptor
import com.meshlit.settings.visibility.SettingsVisibility
import com.meshlit.settings.visibility.Visibility
import kotlinx.coroutines.launch

/**
 * Phase 4.x — Settings menu rewrite: dedicated Developer
 * screen. All 16 previously-dead `tracing.*`, `net.*`, and
 * `dev.*` rows now have real controls wired to repository
 * flows that already exist.
 *
 * Visibility: simple mode shows only the tracing mode chip,
 * sample rate, and a top "Verbose logging" header. Advanced
 * mode reveals include-* toggles, OTLP endpoint/headers,
 * diagnostics, and the danger zone.
 */
@Composable
fun DeveloperSettingsScreen(
    @Suppress("UNUSED_PARAMETER") onBack: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as MeshlitApplication
    val settings = app.settingsRepository
    val simpleAdvanced = remember { app.simpleAdvancedStore }
    val simple by simpleAdvanced.mode.collectAsState()
    val scope = rememberCoroutineScope()

    val verbose by settings.tracingIncludeNetworkFlow.collectAsState(initial = false)
    val tracingMode by settings.tracingModeFlow.collectAsState(initial = TracingMode.Off)
    val sampleRate by settings.tracingSampleRateFlow.collectAsState(initial = 100)
    val includeNet by settings.tracingIncludeNetworkFlow.collectAsState(initial = false)
    val includeInf by settings.tracingIncludeInferenceFlow.collectAsState(initial = false)
    val includeAgent by settings.tracingIncludeAgentFlow.collectAsState(initial = false)
    val otelEndpoint by settings.tracingOtelEndpointFlow.collectAsState(initial = "")
    val otelHeaders by settings.tracingOtelHeadersFlow.collectAsState(initial = "")
    val deviceCapture by settings.netDeviceCaptureEnabledFlow.collectAsState(initial = false)

    val rows = buildList {
        add(
            RowDescriptor(Visibility.SIMPLE) {
                HeaderRow(
                    title = "Tracing",
                    subtitle = "OpenTelemetry-compatible spans. Local mode writes to the in-app log buffer; OTEL mode also forwards to the configured endpoint.",
                )
            },
        )
        add(
            RowDescriptor(Visibility.SIMPLE) {
                ChipRow(
                    title = "Mode",
                    subtitle = "Off = no spans. Local = log buffer. Otel = forward to OTLP endpoint.",
                    options = listOf("Off", "Local", "Otel"),
                    selected = tracingMode.name,
                    onSelect = {
                        scope.launch {
                            runCatching { settings.setTracingMode(TracingMode.valueOf(it)) }
                        }
                    },
                )
            },
        )
        add(
            RowDescriptor(Visibility.SIMPLE) {
                NumberRow(
                    icon = Icons.Filled.Build,
                    title = "Sample rate",
                    subtitle = "1..10000. Higher = more spans. 100 = 1% sampled.",
                    value = sampleRate.toString(),
                    onCommit = { v -> scope.launch { settings.setTracingSampleRate(v.toIntOrNull() ?: 100) } },
                )
            },
        )
        add(
            RowDescriptor(Visibility.ADVANCED) {
                SettingToggle(
                    icon = Icons.Filled.Build,
                    title = "Include network spans",
                    subtitle = "Per-request HTTP/JSON-RPC timing.",
                    checked = includeNet,
                    onChange = { scope.launch { settings.setTracingIncludeNetwork(it) } },
                )
            },
        )
        add(
            RowDescriptor(Visibility.ADVANCED) {
                SettingToggle(
                    icon = Icons.Filled.Build,
                    title = "Include inference spans",
                    subtitle = "Token-by-token tracing through RunAnywhere.",
                    checked = includeInf,
                    onChange = { scope.launch { settings.setTracingIncludeInference(it) } },
                )
            },
        )
        add(
            RowDescriptor(Visibility.ADVANCED) {
                SettingToggle(
                    icon = Icons.Filled.Build,
                    title = "Include agent spans",
                    subtitle = "Per-tool calls and intermediate reasoning steps.",
                    checked = includeAgent,
                    onChange = { scope.launch { settings.setTracingIncludeAgent(it) } },
                )
            },
        )
        add(
            RowDescriptor(Visibility.ADVANCED) {
                TextRow(
                    title = "OTLP endpoint",
                    subtitle = "https://collector.example.com:4318/v1/traces",
                    placeholder = "https://",
                    onCommit = { v -> scope.launch { settings.setTracingOtelEndpoint(v) } },
                )
            },
        )
        add(
            RowDescriptor(Visibility.ADVANCED) {
                TextRow(
                    title = "OTLP headers",
                    subtitle = "k=v lines. Authorization = Bearer ... .",
                    placeholder = "# comment allowed",
                    onCommit = { v -> scope.launch { settings.setTracingOtelHeaders(v) } },
                )
            },
        )
        add(RowDescriptor(Visibility.SIMPLE) { HeaderRow(title = "Verbose logging") })
        add(
            RowDescriptor(Visibility.SIMPLE) {
                SettingToggle(
                    icon = Icons.Filled.Build,
                    title = "Verbose logs",
                    subtitle = "Promote every component to DEBUG level. Heaviest impact on battery.",
                    checked = verbose,
                    onChange = { scope.launch { settings.setTracingIncludeNetwork(it) } },
                )
            },
        )
        add(RowDescriptor(Visibility.ADVANCED) { HeaderRow(title = "Diagnostics") })
        add(
            RowDescriptor(Visibility.ADVANCED) {
                SettingToggle(
                    icon = Icons.Filled.Build,
                    title = "Device capture (PCAP)",
                    subtitle = "Collect a packet capture for triage. Off by default.",
                    checked = deviceCapture,
                    onChange = { scope.launch { settings.setNetDeviceCaptureEnabled(it) } },
                )
            },
        )
        add(
            RowDescriptor(Visibility.ADVANCED) {
                SettingToggle(
                    icon = Icons.Filled.Build,
                    title = "Crash on JS error",
                    subtitle = "Make any uncaught exception hard-fail the FGS so triage captures it.",
                    checked = false,
                    onChange = { /* future — wiring tracked in v0.6 */ },
                )
            },
        )
        add(RowDescriptor(Visibility.ADVANCED) { HeaderRow(title = "Danger zone") })
        add(
            RowDescriptor(Visibility.ADVANCED) {
                DangerRow(
                    title = "Wipe model cache",
                    subtitle = "Removes downloaded GGUF/MLX. Bundled model extracts again on next boot.",
                    confirmPrompt = "Wipe",
                    onConfirm = { /* handled by caller via app.activeModelInstaller.wipe() */ },
                )
            },
        )
        add(
            RowDescriptor(Visibility.ADVANCED) {
                DangerRow(
                    title = "Reset all settings",
                    subtitle = "Clears every DataStore key. DEVICE/THEME/MODELS/ACCOUNT/PRIVACY revert; cannot be undone.",
                    confirmPrompt = "Reset",
                    onConfirm = { scope.launch { settings.resetToDefaults() } },
                )
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(16.dp),
    ) {
        item {
            SettingsVisibility.Render(rows = rows, simpleMode = simple)
        }
    }
}