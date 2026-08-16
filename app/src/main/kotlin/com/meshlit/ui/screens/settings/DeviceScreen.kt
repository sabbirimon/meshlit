package com.meshlit.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meshlit.MeshlitApplication
import com.meshlit.R
import com.meshlit.core.common.DesktopBackend
import com.meshlit.core.common.DeviceProfile
import com.meshlit.core.common.EffectiveDeviceInfo
import com.meshlit.core.common.HostOS
import com.meshlit.core.common.HostOSDetection
import com.meshlit.core.common.PeripheralDevice

/**
 * Device settings screen — the user-facing surface for the device
 * profile. Built from three pieces:
 *
 *  1. Host OS card (always visible) — what the host-OS probe found
 *  2. Hardware facts (always visible) — chipset, GPU, RAM, storage,
 *     peripherals
 *  3. Advanced override (toggle) — when on, shows the editor below
 *     the read-only facts so the user can correct auto-detect mistakes
 *
 * The screen reads the resolved [DeviceProfile] from the repository;
 * the rest of the app uses [EffectiveDeviceInfo] (which is the
 * override-resolved view).
 *
 * NOTE: this composable does NOT provide its own Scaffold/TopAppBar —
 * the parent [CategoryScreen] already supplies one. Providing a second
 * Scaffold here caused two top bars to stack on top of each other,
 * which made the upper half of the screen unresponsive to taps.
 */
@Composable
fun DeviceScreen(
    @Suppress("UNUSED_PARAMETER") onBack: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as MeshlitApplication
    val viewModel: DeviceScreenViewModel = viewModel(
        factory = deviceScreenViewModelFactory(context),
    )
    val state by viewModel.state.collectAsState()
    val host = app.hostOSDetection

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(16.dp),
    ) {
        item {
            HostOSCard(host = host)
            Spacer(Modifier.height(8.dp))
        }

        item {
            Text(
                text = stringResource(R.string.device_section_hardware),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        item {
            HardwareCard(
                effective = state.profile.effective,
                detection = state.profile.detection,
            )
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.device_section_peripherals),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        item {
            PeripheralsCard(peripherals = state.profile.connectedPeripherals)
        }

        item {
            Spacer(Modifier.height(8.dp))
            GpuPanelSummaryCard(
                effective = state.profile.effective,
                detection = state.profile.detection,
            )
        }

        item {
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.device_advanced_override),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.device_advanced_override_desc),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = state.advancedEnabled,
                    onCheckedChange = { viewModel.setAdvancedEnabled(it) },
                )
            }
        }
        if (state.advancedEnabled) {
            item {
                OverrideEditorCard(
                    effective = state.profile.effective,
                    hasOverride = state.profile.hasOverride,
                    onManufacturer = viewModel::setManualManufacturer,
                    onModel = viewModel::setManualModelName,
                    onChipset = viewModel::setManualChipset,
                    onSocModel = viewModel::setManualSocModel,
                    onGpu = viewModel::setManualGpu,
                    onRam = viewModel::setManualRamMb,
                    onCores = viewModel::setManualCpuCores,
                    onNote = viewModel::setManualNote,
                    onClearOverride = viewModel::clearOverride,
                )
            }
        }

        item { Spacer(Modifier.height(32.dp)) }
    }
}

// ---------------------------------------------------------------------------
// Sub-components
// ---------------------------------------------------------------------------

@Composable
private fun HostOSCard(host: HostOSDetection) {
    val isX86 = host.hostOS.isX86Host
    val icon: ImageVector = if (isX86) Icons.Default.Computer else Icons.Default.PhoneAndroid
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isX86)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = host.hostOS.displayName,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "ABI: ${host.abi} · Kernel ${host.kernelVersion}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (isX86)
                    "x86_64 host — desktop-class compute is available. " +
                    "Inference can use CUDA, ROCm, or oneAPI if the host " +
                    "has those drivers; otherwise Vulkan via /dev/dri."
                else
                    "Mobile ARM host — inference uses Vulkan / CPU. " +
                    "Attach an eGPU over USB-C for desktop-class compute.",
                style = MaterialTheme.typography.bodyMedium,
            )
            val backendName = host.preferredDesktopBackend?.displayName ?: "CPU"
            Text(
                text = "Recommended eGPU backend: $backendName",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
            host.hostCpuModel?.let { model ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Host CPU: $model",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun HardwareCard(
    effective: EffectiveDeviceInfo,
    detection: com.meshlit.core.common.DetectedDeviceInfo,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FactRow(
                icon = Icons.Default.Computer,
                label = "Model",
                value = effective.model,
                hint = detection.manufacturer,
            )
            FactRow(
                icon = Icons.Default.Memory,
                label = "Chipset",
                value = effective.socFamily.displayName,
                hint = effective.socModel?.let { "$it · ${effective.primaryAbi}" },
            )
            FactRow(
                icon = Icons.Default.Computer,
                label = "GPU",
                value = effective.gpuFamily.displayName,
                hint = if (effective.hasNpu) "NPU: ${effective.npuName ?: "present"}" else null,
            )
            FactRow(
                icon = Icons.Default.Memory,
                label = "RAM",
                value = "${effective.totalRamMb / 1024} GB (${effective.totalRamMb} MB)",
                hint = "${effective.cpuCoreCount} cores @ ${detection.cpuMaxFreqKHz / 1000} MHz",
            )
            FactRow(
                icon = Icons.Default.Storage,
                label = "Storage",
                value = "${detection.totalStorageMb / 1024} GB total · ${detection.availableStorageMb / 1024} GB free",
                hint = "Android ${effective.androidVersion} (SDK ${effective.androidSdkInt})",
            )
            effective.externalGpu?.let { egpu ->
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = egpu.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "${egpu.kind.displayName} · ${egpu.driverStatus.displayName}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PeripheralsCard(peripherals: List<PeripheralDevice>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (peripherals.isEmpty()) {
                Text(
                    text = stringResource(R.string.device_no_peripherals),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                peripherals.forEach { p ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.tertiary),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = p.name ?: p.kind.displayName,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = "${p.kind.displayName} · ${p.transport.tag}" +
                                    (p.vendorId?.let { " · VID 0x%04x".format(it) } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FactRow(
    icon: ImageVector,
    label: String,
    value: String,
    hint: String? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(value, style = MaterialTheme.typography.bodyMedium)
            hint?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Compact GPU + eGPU summary shown on the Device screen. The
 * detailed view (Vulkan toggle, re-probe, link-speed graph) lives
 * under Advanced → Devices → GPU panel; this card is just a teaser.
 */
@Composable
private fun GpuPanelSummaryCard(
    effective: EffectiveDeviceInfo,
    detection: com.meshlit.core.common.DetectedDeviceInfo,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "GPU panel",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Backend: ${effective.gpuFamily.displayName}",
                style = MaterialTheme.typography.bodyMedium,
            )
            effective.externalGpu?.let { egpu ->
                Text(
                    text = "eGPU: ${egpu.displayName} · ${egpu.kind.displayName} · ${egpu.transport.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            } ?: Text(
                text = "No external GPU detected. Plug an RTX 4060 / RX 8000 over USB-C for desktop-class compute.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "ABI: ${detection.primaryAbi}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}