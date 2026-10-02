package com.meshlit.ui.v2.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meshlit.ui.theme.MeshlitInk
import com.meshlit.ui.theme.MeshlitOutlineV2
import com.meshlit.ui.theme.MeshlitPulseViolet
import com.meshlit.ui.theme.MeshlitSurface
import com.meshlit.ui.theme.MeshlitSurfaceContainer
import com.meshlit.ui.theme.MeshlitTextPrimaryV2
import com.meshlit.ui.theme.MeshlitTextSecondaryV2
import com.meshlit.ui.theme.MeshlitTextTertiaryV2

/**
 * The five categories of long-running operation the v2
 * chrome recognises. The icon + label are picked from this
 * enum so the loader modal stays consistent across screens.
 *
 *  - [Model] — downloading a model file (catalog download)
 *  - [Network] — scanning / discovering peers
 *  - [Post] — uploading / publishing data
 *  - [Diagnostic] — running diagnostics on a peer / device
 *  - [Generic] — anything else (default)
 */
enum class LoaderKind(
    val icon: ImageVector,
    val label: String,
) {
    Model(Icons.Filled.CloudDownload, "Downloading model"),
    Network(Icons.Filled.WifiTethering, "Scanning network"),
    Post(Icons.Filled.Send, "Posting data"),
    Diagnostic(Icons.Filled.Hub, "Running diagnostics"),
    Generic(Icons.Filled.Devices, "Working"),
}

/**
 * State of one operation being tracked by the modal. Each
 * operation has an [id] (caller-defined), a [kind], an
 * optional [progress] (0f..1f), an optional [statusLine]
 * shown under the headline, and a [phase] flag for the
 * indeterminate (spinner) variant.
 *
 * Pass a `List<LoaderOperation>` to [MeshlitLoaderModal];
 * the modal renders one row per operation and a footer
 * with overall progress + cancel/dismiss.
 */
data class LoaderOperation(
    val id: String,
    val kind: LoaderKind,
    val title: String,
    val progress: Float? = null,
    val statusLine: String? = null,
    val indeterminate: Boolean = false,
)

/**
 * Full-screen modal loader used by the v2 build for any
 * long-running operation: model downloads, network scans,
 * data posts, diagnostics. Lives above the v2 chrome
 * (drawer, top bar, bottom bar) and dims everything
 * underneath so the user can't accidentally navigate away.
 *
 * Visual:
 *   - Ink-scrim background (MeshlitInk @ 0.78 alpha) so the
 *     Pulse backdrop stops being visible and the modal
 *     reads as the only surface.
 *   - 24 dp rounded card on MeshlitSurfaceContainer.
 *   - Pulsing violet ring (CircularProgressIndicator with
 *     MeshlitPulseViolet) at 88 dp so it's clearly the
 *     "loading" affordance.
 *   - Headline `headlineMedium` Bold + subtitle `bodyLarge`.
 *   - Operation list — each row is a small Surface card
 *     with the icon, title, status, and progress bar.
 *   - Footer with "Cancel" + "Done" buttons.
 *
 * Pass `visible = false` to hide; pass an empty list to
 * show the "no work in progress" empty state.
 */
@Composable
fun MeshlitLoaderModal(
    visible: Boolean,
    operations: List<LoaderOperation>,
    onDismiss: () -> Unit,
    onCancel: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    headline: String = "Working",
    subtitle: String = "Hold tight — we'll be right back.",
) {
    // Plain if/else instead of AnimatedVisibility so the modal
    // renders on the first frame without requiring the (1.5+)
    // `initiallyVisible` parameter. Subsequent toggles still
    // get the fade transition via the visible state change.
    if (visible) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(color = MeshlitInk.copy(alpha = 0.78f)),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                color = MeshlitSurface,
                shape = RoundedCornerShape(28.dp),
                tonalElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 28.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(
                                color = MeshlitPulseViolet.copy(alpha = 0.18f),
                                shape = CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = MeshlitPulseViolet,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = headline,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        color = MeshlitTextPrimaryV2,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MeshlitTextSecondaryV2,
                    )

                    // Scrollable middle: spinner + headline +
                    // operations. Footer stays pinned.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        if (operations.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp))
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                operations.forEach { op ->
                                    LoaderRow(
                                        op = op,
                                        onCancel = { onCancel(op.id) },
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = "Dismiss",
                                color = MeshlitTextSecondaryV2,
                            )
                        }
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MeshlitPulseViolet,
                                contentColor = Color.White,
                            ),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = if (operations.isEmpty()) "Done" else "Hide",
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoaderRow(
    op: LoaderOperation,
    onCancel: () -> Unit,
) {
    Surface(
        color = MeshlitSurfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = MeshlitPulseViolet.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = op.kind.icon,
                        contentDescription = op.kind.label,
                        tint = MeshlitPulseViolet,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = op.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MeshlitTextPrimaryV2,
                    )
                    if (op.statusLine != null) {
                        Text(
                            text = op.statusLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = MeshlitTextTertiaryV2,
                        )
                    }
                }
                TextButton(onClick = onCancel) {
                    Icon(
                        imageVector = Icons.Filled.Cancel,
                        contentDescription = "Cancel",
                        tint = MeshlitTextSecondaryV2,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            if (op.progress != null) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { op.progress.coerceIn(0f, 1f) },
                    color = MeshlitPulseViolet,
                    trackColor = MeshlitOutlineV2.copy(alpha = 0.4f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                )
            } else if (op.indeterminate) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    color = MeshlitPulseViolet,
                    trackColor = MeshlitOutlineV2.copy(alpha = 0.4f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                )
            }
        }
    }
}