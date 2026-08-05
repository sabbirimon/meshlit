package com.meshlit.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.meshlit.ui.theme.MeshlitAmber
import com.meshlit.ui.theme.RaOrange
import com.meshlit.ui.theme.RaOutline
import com.meshlit.ui.theme.RaSurfaceVariant
import com.meshlit.ui.theme.RaTextPrimary
import com.meshlit.ui.theme.RaTextSecondary

/**
 * RunAnywhere-style "Top pick" model card. An orange-outlined,
 * tinted container that shows the model logo, the title row
 * (name + size + NPU chip), the highlight label (`TOP_PICK`),
 * and the trailing `RaGetButton`. Optional metadata + a chip row
 * sit between.
 *
 * Visual contract (mirrors the upstream `RecommendedRow.kt`):
 *  - root: `Surface` with `RoundedCornerShape(16.dp)`
 *  - background: `RaOrange.copy(alpha = 0.08f)` (tinted orange
 *    so the highlight card visually pops)
 *  - border: 1dp `RaOrange.copy(alpha = 0.5f)`
 *  - leading: 40dp `RaSurfaceVariant` rounded square with the
 *    logo glyph at 24dp in `RaOrange`
 *  - highlight chip: orange `TOP_PICK` pill with a bolt icon
 *  - title: `titleMedium` SemiBold `RaTextPrimary`
 *  - subtitle: `bodySmall` `RaTextSecondary` (e.g. "1.91 GB")
 *  - chips: `FlowRow` of `RaPillChip`s (NPU / Smart / Thinks)
 *  - trailing: filled-amber `RaGetButton` (or `RaPillChip` when
 *    the model is already loaded)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TopPickCard(
    logoIcon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String?,
    highlightLabel: String = "Top pick",
    chips: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val containerColor = RaOrange.copy(alpha = 0.08f)
    val borderColor = RaOrange.copy(alpha = 0.5f)

    val surfaceContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Leading orange logo square.
            Surface(
                color = RaSurfaceVariant,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(40.dp),
            ) {
                Row(
                    modifier = Modifier
                        .size(40.dp)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = logoIcon,
                        contentDescription = null,
                        tint = RaOrange,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            // Title / subtitle / chips column.
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                RaPillChip(
                    text = highlightLabel,
                    tone = RaPillTone.TOP_PICK,
                    icon = Icons.Filled.Bolt,
                )
                Text(
                    text = title,
                    color = RaTextPrimary,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = RaTextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (chips != null) {
                    Spacer(Modifier.size(2.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        chips()
                    }
                }
            }

            // Trailing action.
            if (trailing != null) {
                trailing()
            }
        }
    }

    val rowModifier = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = rowModifier,
            color = containerColor,
            contentColor = RaTextPrimary,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, borderColor),
            content = surfaceContent,
        )
    } else {
        Surface(
            modifier = rowModifier,
            color = containerColor,
            contentColor = RaTextPrimary,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, borderColor),
            content = surfaceContent,
        )
    }
}

/**
 * Organization row — the "All organisations" cards in the
 * browse-by-organisation screenshot. Logo + name + NPU pill +
 * "N models" pill + chevron. Used by [CatalogScreen] when
 * grouping models by provider / publisher.
 */
@Composable
fun OrganizationRow(
    logoIcon: androidx.compose.ui.graphics.vector.ImageVector,
    name: String,
    npuLabel: String? = null,
    modelsLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        color = MaterialTheme.colorScheme.surface,
        contentColor = RaTextPrimary,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, RaOutline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Leading colored square — caller picks the brand color.
            Surface(
                color = RaSurfaceVariant,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(40.dp),
            ) {
                Row(
                    modifier = Modifier
                        .size(40.dp)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = logoIcon,
                        contentDescription = null,
                        tint = MeshlitAmber,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    color = RaTextPrimary,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
                if (npuLabel != null) {
                    Spacer(Modifier.size(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RaPillChip(text = npuLabel, tone = RaPillTone.NPU)
                        Surface(
                            color = Color.Transparent,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, RaOutline),
                        ) {
                            Text(
                                text = modelsLabel,
                                color = RaTextSecondary,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(
                                    horizontal = 8.dp,
                                    vertical = 4.dp,
                                ),
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.size(4.dp))
                    Surface(
                        color = Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, RaOutline),
                    ) {
                        Text(
                            text = modelsLabel,
                            color = RaTextSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(
                                horizontal = 8.dp,
                                vertical = 4.dp,
                            ),
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = RaTextSecondary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}