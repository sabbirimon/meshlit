package com.meshlit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.meshlit.ui.theme.MeshlitAmber
import com.meshlit.ui.theme.RaOrangeSoft

/**
 * RunAnywhere-style hero icon. A circular tinted background with a
 * full-color foreground glyph. Used for empty states where a single
 * image carries the meaning:
 *
 *  - Agent "Working late?" → `Icons.Filled.Bolt`
 *  - Settings → About → `Icons.Filled.Info`
 *  - Models empty state → `Icons.Filled.Storage`
 *
 * Visual contract:
 *  - container: `CircleShape` filled with `RaOrangeSoft` (#33FF7A1A,
 *    a 20% alpha overlay of the brand orange). Soft fill rather than
 *    full saturation so the glyph stays the focal point.
 *  - glyph: `RaOrange` (`MeshlitAmber`) at 36dp inside a 96dp
 *    container (the screenshot ratio is ~0.375 of the container).
 *
 * `tint` defaults to `MeshlitAmber`. Pass a different color when the
 * surrounding surface already provides orange context and a different
 * focal color is needed (e.g. a "Success" hero for the empty-state
 * after a clean install).
 */
@Composable
fun RaHeroIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    tint: Color = MeshlitAmber,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(RaOrangeSoft),
        contentAlignment = Alignment.Center,
    ) {
        // Glyph is 0.375 * container, matching the RunAnywhere sample.
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size * 0.375f),
        )
    }
}