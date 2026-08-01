package com.meshlit.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.meshlit.R
import com.meshlit.core.common.EffectiveDeviceInfo
import com.meshlit.core.common.GpuFamily
import com.meshlit.core.common.SocFamily

/**
 * Manual override editor for the device profile. Phase 1 covers the
 * fields users actually correct when auto-detect is wrong:
 *  - Manufacturer name (custom ROMs, brand-masked devices)
 *  - Model name (user wants a friendlier label)
 *  - Chipset family (rare, but Snapdragon 8 Gen 2 vs 8 Gen 3
 *    matters for inference fit)
 *  - SoC model (e.g. "SM8550" — used by the chipset database)
 *  - GPU family (Adreno 740 vs 750 — used by llama.cpp backend)
 *  - Total RAM (RAM extenders / custom ROMs ship inconsistent totals)
 *  - CPU cores (rare; sometimes used by emulator users)
 *  - Note (free-text "why I overrode")
 *
 * Each field is an OutlinedTextField or a dropdown. Saves write
 * through the ViewModel to DataStore. A "Reset" button at the bottom
 * clears the override entirely.
 */
@Composable
fun OverrideEditorCard(
    effective: EffectiveDeviceInfo,
    hasOverride: Boolean,
    onManufacturer: (String?) -> Unit,
    onModel: (String?) -> Unit,
    onChipset: (SocFamily?) -> Unit,
    onSocModel: (String?) -> Unit,
    onGpu: (GpuFamily?) -> Unit,
    onRam: (Long?) -> Unit,
    onCores: (Int?) -> Unit,
    onNote: (String?) -> Unit,
    onClearOverride: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            Text(
                text = if (hasOverride) stringResource(R.string.device_override_active)
                       else stringResource(R.string.device_override_inactive),
                style = MaterialTheme.typography.titleSmall,
                color = if (hasOverride) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = effective.manufacturer,
                onValueChange = { onManufacturer(it.takeIf { s -> s.isNotBlank() }) },
                label = { Text("Manufacturer") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = effective.model,
                onValueChange = { onModel(it.takeIf { s -> s.isNotBlank() }) },
                label = { Text("Model") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            ChipsetDropdown(
                current = effective.socFamily,
                onChange = onChipset,
            )

            OutlinedTextField(
                value = effective.socModel.orEmpty(),
                onValueChange = { onSocModel(it.takeIf { s -> s.isNotBlank() }) },
                label = { Text("SoC model (e.g. SM8550)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            GpuDropdown(
                current = effective.gpuFamily,
                onChange = onGpu,
            )

            OutlinedTextField(
                value = if (effective.totalRamMb > 0) effective.totalRamMb.toString() else "",
                onValueChange = {
                    val parsed = it.toLongOrNull()
                    onRam(parsed)
                },
                label = { Text("Total RAM (MB)") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = effective.cpuCoreCount.toString(),
                onValueChange = {
                    val parsed = it.toIntOrNull()
                    onCores(parsed)
                },
                label = { Text("CPU cores") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            HorizontalDivider()

            if (hasOverride) {
                Button(
                    onClick = onClearOverride,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.device_clear_override))
                }
            } else {
                Text(
                    text = stringResource(R.string.device_override_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ChipsetDropdown(
    current: SocFamily,
    onChange: (SocFamily) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = current.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Chipset family") },
            trailingIcon = {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SocFamily.entries.forEach { family ->
                DropdownMenuItem(
                    text = { Text(family.displayName) },
                    onClick = {
                        onChange(family)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun GpuDropdown(
    current: GpuFamily,
    onChange: (GpuFamily) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = current.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("GPU family") },
            trailingIcon = {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            GpuFamily.entries.forEach { family ->
                DropdownMenuItem(
                    text = { Text(family.displayName) },
                    onClick = {
                        onChange(family)
                        expanded = false
                    },
                )
            }
        }
    }
}