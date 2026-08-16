package com.meshlit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meshlit.ui.theme.MeshlitAmber
import com.meshlit.ui.theme.MeshlitAmberDim
import com.meshlit.ui.theme.MeshlitEmerald
import com.meshlit.ui.theme.MeshlitEmeraldDim
import com.meshlit.ui.theme.MeshlitError
import com.meshlit.ui.theme.MeshlitViolet

/**
 * RunAnywhere-style pill chip. Two overloads:
 *
 *  - `RaPillChip(text, tone, icon?)` — type-safe tones used across the
 *    Models picker (TOP_PICK / NPU / BUNDLED / MOE / ACTIVE / ERROR /
 *    NEUTRAL / PRIVATE). Each tone maps to a single accent color so
 *    the chip's meaning is consistent across screens.
 *  - `RaPillChip(text, color, icon?)` — raw-color overload mirroring
 *    upstream `ModelPill(text, color, icon?)`. Use when the chip's
 *    color is derived from data (e.g. an org's brand color).
 *
 * Visual contract (matches upstream `ModelPill.kt`):
 *  - shape: `RoundedCornerShape(8.dp)`
 *  - background: `color.copy(alpha = 0.12f)` (upstream 12% tint)
 *  - text + icon: full-saturation `color`
 *  - padding: 8.dp horizontal / 4.dp vertical
 *  - text: `labelMedium` SemiBold, single-line ellipsis
 *  - leading icon: 14.dp with 4.dp spacer
 *
 * Used by `RaListCard`, `AlternativeModelsCard`,
 * `RunAnywhereCatalogCard`, `BundledModelCard`, `AgentScreen`, and
 * `JobsScreen` suggestion chips.
 */
enum class RaPillTone {
    NPU,
    TOP_PICK,
    BUNDLED,
    MOE,
    ACTIVE,
    ERROR,
    NEUTRAL,
    PRIVATE,
}

@Composable
fun RaPillChip(
    text: String,
    tone: RaPillTone,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    val color = tone.toColor()
    RaPillChip(text = text, color = color, icon = icon, modifier = modifier)
}

@Composable
fun RaPillChip(
    text: String,
    color: Color,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color = color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RaPillTone.toColor(): Color = when (this) {
    RaPillTone.NPU -> MeshlitEmerald         // green = hardware accel
    RaPillTone.TOP_PICK -> MeshlitAmber      // brand orange = best pick
    RaPillTone.BUNDLED -> MeshlitEmerald     // green = bundled with APK
    RaPillTone.MOE -> MeshlitViolet          // violet = MoE architecture
    RaPillTone.ACTIVE -> MeshlitEmerald      // green = currently loaded
    RaPillTone.ERROR -> MeshlitError         // red = load/delete failed
    RaPillTone.NEUTRAL -> Color(0xFFB0B0B0)  // gray = generic label
    RaPillTone.PRIVATE -> MeshlitAmberDim    // dim orange = HF auth gated
}
