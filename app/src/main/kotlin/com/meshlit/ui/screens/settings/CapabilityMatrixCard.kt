package com.meshlit.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meshlit.core.common.Abi
import com.meshlit.core.common.CapabilityMatrix
import com.meshlit.core.common.FileFormat
import com.meshlit.core.common.GpuBackendAvailability
import com.meshlit.core.common.NpuAvailability
import com.meshlit.core.common.Quantization

/**
 * Capability-matrix renderer for the Devices screen.
 *
 * Renders a [CapabilityMatrix] as a self-contained card. **MUST NOT** reference
 * any `Ra*` theme tokens or `Ra*` UI components — those are owned by the
 * `redesign/drawer-gemini` integration branch and are not yet merged. This card
 * is intentionally token-free (uses `MaterialTheme.colorScheme.*` only) so it
 * compiles cleanly against either branch.
 *
 * Slice 1 deliverable. The card is wired into the Devices screen in a
 * follow-up so we can land the data class + the renderer independently of
 * the Devices screen rewrite.
 *
 * Why this is a separate file (not an inline Composable in DevicesScreen.kt):
 *  - DevicesScreen.kt is heavy and changes for unrelated reasons.
 *  - Putting the renderer here keeps Slice 1 additive-only.
 *  - The renderer is unit-testable via Compose preview without spinning
 *    up the Devices screen scaffolding.
 */
@Composable
fun CapabilityMatrixCard(
    matrix: CapabilityMatrix,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Header(matrix)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(matrix)
            AbiRow(matrix.abis)
            FormatRow(matrix.formats)
            QuantRow(matrix.quants)
            ContextRow(matrix.contextLimit)
            if (matrix == CapabilityMatrix.NONE) {
                NoneState()
            }
        }
    }
}

@Composable
private fun Header(matrix: CapabilityMatrix) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "Capability matrix",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "API ${matrix.androidApi} · ${matrix.tier.name}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Row(matrix: CapabilityMatrix) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Pill("RAM · ${matrix.ramTier.displayName}")
        Pill("GPU · ${matrix.gpu.tag}")
        Pill("NPU · ${matrix.npu.tag}")
    }
}

@Composable
private fun AbiRow(abis: Set<Abi>) {
    LabeledLine(
        label = "ABI",
        value = if (abis.isEmpty()) "—" else abis.joinToString { it.tag },
    )
}

@Composable
private fun FormatRow(formats: Set<FileFormat>) {
    LabeledLine(
        label = "Formats",
        value = if (formats.isEmpty()) "—" else formats.joinToString { it.tag },
    )
}

@Composable
private fun QuantRow(quants: Set<Quantization>) {
    LabeledLine(
        label = "Quants",
        value = if (quants.isEmpty()) "—" else quants.joinToString { it.tag },
    )
}

@Composable
private fun ContextRow(contextLimit: Int) {
    LabeledLine(
        label = "Context",
        value = if (contextLimit <= 0) "—" else "≤ $contextLimit tokens",
    )
}

@Composable
private fun LabeledLine(label: String, value: String) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun Pill(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun NoneState() {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Text(
            text = "Device not yet probed — capabilities will populate after first engine init.",
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * Stable preview shape so designers / reviewers can eyeball the card without
 * a real probe result.
 */
@Composable
fun CapabilityMatrixCardPreviewFixture() {
    CapabilityMatrixCard(
        matrix = CapabilityMatrix(
            androidApi = 35,
            abis = setOf(Abi.ARM64_V8A, Abi.ARMEABI_V7A),
            ramTier = com.meshlit.core.common.RamTier.HIGH,
            gpu = GpuBackendAvailability.VULKAN,
            npu = NpuAvailability.PROBED_NO_DRIVER,
            formats = setOf(FileFormat.GGUF, FileFormat.ONNX),
            contextLimit = 8192,
            quants = Quantization.STANDARD_GGUF,
        ),
    )
}
