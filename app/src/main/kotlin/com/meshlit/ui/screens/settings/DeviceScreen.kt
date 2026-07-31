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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meshlit.MeshlitApplication
import com.meshlit.R
import com.meshlit.core.common.DesktopBackend
import com.meshlit.core.common.HostOS

/**
 * Device settings screen. Shows the user what the system probe found:
 *
 *  - Host OS (Android / Linux x86 / Waydroid / ChromeOS ARC / ...)
 *  - Detected chipset family + model
 *  - GPU family + NPU presence
 *  - RAM + storage
 *  - eGPU status
 *
 * Every row is read-only in Phase 1; Phase 2 adds the manual override
 * editor. The Host OS row is the most prominent — it's what tells
 * the user they're running on Linux/ChromeOS/emulator and what eGPU
 * backends are available.
 *
 * To replace the generic [CategoryScreen] DEVICE branch, this
 * composable is wired from `CategoryScreen` via a `when` clause.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as MeshlitApplication
    val host = app.hostOSDetection

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_cat_device)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(16.dp),
        ) {
            item {
                HostOSCard(host = host)
                Spacer(Modifier.height(8.dp))
            }
            item {
                Text(
                    text = "Hardware",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            item { FactRow(icon = Icons.Default.Memory, label = "Chipset", value = "(probed)") }
            item { FactRow(icon = Icons.Default.Computer, label = "GPU", value = "(probed)") }
            item { FactRow(icon = Icons.Default.Storage, label = "RAM", value = "(probed)") }
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Peripherals",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            item {
                Text(
                    text = stringResource(R.string.settings_category_coming_soon),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun HostOSCard(host: com.meshlit.core.common.HostOSDetection) {
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
                Spacer(Modifier.size(12.dp))
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
            if (host.preferredDesktopBackend != null) {
                Spacer(Modifier.height(8.dp))
                val backendName = host.preferredDesktopBackend?.displayName ?: "CPU"
                Text(
                    text = "Recommended eGPU backend: $backendName",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
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
private fun FactRow(icon: ImageVector, label: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null)
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall)
                Text(value, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}