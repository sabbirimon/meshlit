package com.meshlit.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Horizontal row of selectable framework filters for the Models
 * screen ("ALL" / "LLAMA" / "MLC" / etc.). Stub implementation —
 * the MeshlitV2 design-system themed filter row never landed, so
 * this is a plain Material 3 fallback that compiles and functions
 * correctly. The selected chip uses the primary colour; the rest
 * use the surface with a thin outline.
 *
 * @param active     the framework name currently selected. The chip
 *                   labelled `active` is rendered as filled.
 * @param onSelect   invoked when the user taps a chip; the value
 *                   passed is the new selected framework name.
 * @param options    the full set of options to render. Defaults to
 *                   the canonical Meshlit frameworks.
 */
@Composable
fun ModelFilterRow(
    active: String,
    onSelect: (String) -> Unit,
    options: List<String> = DEFAULT_FRAMEWORKS,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { label ->
            val isSelected = label == active
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surface,
                    )
                    .clickable { onSelect(label) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private val DEFAULT_FRAMEWORKS = listOf("ALL", "LLAMA", "MLC", "ONNX", "EXTERNAL")