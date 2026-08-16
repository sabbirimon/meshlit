package com.meshlit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meshlit.ui.theme.MeshlitError

/**
 * RunAnywhere-style error banner. A pill-rounded red surface with a
 * leading warning glyph, a bold one-line title, and a smaller
 * two-line subtitle.
 *
 * Mirrors the upstream "Web & tools unavailable — Choose a chat
 * model before enabling Web & tools." pattern from the screenshot.
 * Used by the Agent terminal when the cloud tools / web search /
 * browser stack isn't available because no chat model is loaded.
 *
 * Visual contract:
 *  - shape: `RoundedCornerShape(20.dp)` — pill radius, matches the
 *    suggestion chip row visually.
 *  - container: `MeshlitError` at 18% alpha (the screenshot's red
 *    is a tinted red, not a saturated red; the icon and text are
 *    full saturation).
 *  - leading icon: 20dp warning glyph, full `MeshlitError` color.
 *  - title: `bodyMedium` SemiBold `MeshlitError`.
 *  - subtitle: `bodySmall` `MeshlitError` at 80% alpha for a soft
 *    secondary line.
 *  - padding: 16dp horizontal / 12dp vertical.
 */
@Composable
fun ErrorBanner(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector = Icons.Filled.ErrorOutline,
) {
    val container = MeshlitError.copy(alpha = 0.18f)
    val onContainer = MeshlitError
    val onContainerDim = MeshlitError.copy(alpha = 0.85f)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = container,
        contentColor = onContainer,
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = onContainer,
                modifier = Modifier.size(20.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = onContainer,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = onContainerDim,
                    )
                }
            }
        }
    }
}

/**
 * Convenience overload that takes a raw tint for screens that
 * need a non-error accent (e.g. a yellow "Model not loaded yet"
 * notice in the cloud hub).
 */
@Composable
fun TintedBanner(
    title: String,
    tint: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector = Icons.Filled.ErrorOutline,
) {
    val container = tint.copy(alpha = 0.18f)
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = container,
        contentColor = tint,
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = tint,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = tint.copy(alpha = 0.85f),
                    )
                }
            }
        }
    }
}