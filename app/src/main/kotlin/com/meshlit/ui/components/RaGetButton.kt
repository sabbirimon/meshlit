package com.meshlit.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meshlit.ui.theme.MeshlitAmber

/**
 * RunAnywhere-style "Get" / "Set token" filled-orange pill button.
 * Used in the trailing slot of `RaListCard` when a model isn't
 * downloaded yet. Mirrors upstream `DownloadChip` semantically
 * (`Set token` if HF auth is required and no token, else `Get`) but
 * renders as a filled-orange pill to match the screenshots, not the
 * tonal `AssistChip` the upstream sample uses.
 *
 * Visual contract:
 *  - container: filled `MeshlitAmber`, `RoundedCornerShape(20.dp)`
 *  - content: white, `labelLarge` SemiBold
 *  - leading icon: 18dp white `CloudDownload` (or caller's icon) with
 *    a 6dp spacer
 *  - padding: 16dp horizontal / 8dp vertical (40dp tall total)
 *
 * Pass `enabled = false` while a download is in flight (the
 * `DownloadProgressAction` in `ModelTrailingAction.kt` takes over the
 * trailing slot then).
 */
@Composable
fun RaGetButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector = Icons.Filled.CloudDownload,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MeshlitAmber,
            contentColor = Color.White,
            disabledContainerColor = MeshlitAmber.copy(alpha = 0.4f),
            disabledContentColor = Color.White.copy(alpha = 0.7f),
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}