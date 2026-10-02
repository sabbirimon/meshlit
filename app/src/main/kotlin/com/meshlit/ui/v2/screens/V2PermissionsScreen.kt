package com.meshlit.ui.v2.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.meshlit.ui.theme.MeshlitInk
import com.meshlit.ui.theme.MeshlitPulseViolet
import com.meshlit.ui.theme.MeshlitSurface
import com.meshlit.ui.theme.MeshlitSurfaceContainer
import com.meshlit.ui.theme.MeshlitTextPrimaryV2
import com.meshlit.ui.theme.MeshlitTextSecondaryV2
import com.meshlit.ui.theme.MeshlitTextTertiaryV2
import com.meshlit.ui.v2.components.MeshlitDeepLinkWrap

/**
 * First-run permission gate for the v2 build. Lights up on
 * the first launch (or whenever Settings → Reset first-run is
 * tapped) and walks the user through the 4–5 Android runtime
 * permissions the cluster needs before it can do anything
 * useful:
 *
 *  - **Microphone** — voice capture (STT) + agent mic tool
 *  - **Location** — nearby device discovery (Wifi-Direct /
 *    BLE both require it on Android 12+)
 *  - **Nearby Wi-Fi devices** — peer-to-peer link
 *  - **Storage / Media** — model file read/write + import
 *  - **Notifications** (Android 13+) — job completion alerts
 *
 * Each row has a colored 40 dp icon container, the permission
 * name, a one-line reason, and a "Grant" / "Granted" pill on
 * the right. The bottom "Continue" button stays disabled
 * (visually obvious — alpha 0.4) until at least
 * microphone + storage are granted (minimum viable cluster).
 * "Skip" links to [onSkip] and never blocks the rest of the
 * app — it just parks the gate behind a Settings entry the
 * user can return to.
 *
 * The screen uses [ActivityResultContracts.RequestMultiplePermissions]
 * so the system dialog cascades — granting one permission
 * doesn't dismiss the rest.
 */
@Composable
fun V2PermissionsScreen(
    onContinue: () -> Unit,
    onSkip: () -> Unit,
) {
    val context = LocalContext.current

    // Build the permission list dynamically: notifications
    // only on Android 13+; storage on <= Android 12; media
    // perms on Android 13+; READ_MEDIA_* split per type.
    val perms = remember {
        buildList {
            add(PermissionRow(
                id = "mic",
                icon = Icons.Filled.Mic,
                name = "Microphone",
                reason = "Voice input + agent mic tool",
                manifestPerm = Manifest.permission.RECORD_AUDIO,
            ))
            add(PermissionRow(
                id = "location",
                icon = Icons.Filled.LocationOn,
                name = "Location",
                reason = "Discover nearby peers over Wi-Fi + BLE",
                manifestPerm = Manifest.permission.ACCESS_FINE_LOCATION,
            ))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(PermissionRow(
                    id = "notif",
                    icon = Icons.Filled.Notifications,
                    name = "Notifications",
                    reason = "Job completion + cluster alerts",
                    manifestPerm = Manifest.permission.POST_NOTIFICATIONS,
                ))
                add(PermissionRow(
                    id = "media",
                    icon = Icons.Filled.Folder,
                    name = "Media & files",
                    reason = "Import GGUF + save exports",
                    manifestPerm = Manifest.permission.READ_MEDIA_IMAGES,
                ))
            } else {
                add(PermissionRow(
                    id = "storage",
                    icon = Icons.Filled.Folder,
                    name = "Storage",
                    reason = "Read + write model files",
                    manifestPerm = Manifest.permission.READ_EXTERNAL_STORAGE,
                ))
            }
            add(PermissionRow(
                id = "nearby",
                icon = Icons.Filled.WifiTethering,
                name = "Nearby devices",
                reason = "Wi-Fi Direct + Bluetooth peer links",
                // NEARBY_WIFI_DEVICES is API 33+; older devices
                // already authorise Wi-Fi Direct via location.
                manifestPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.NEARBY_WIFI_DEVICES
                } else {
                    Manifest.permission.ACCESS_FINE_LOCATION
                },
            ))
        }
    }

    var granted by remember {
        mutableStateOf(
            perms.associate { it.id to isGranted(context, it.manifestPerm) },
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { results ->
            granted = perms.associate { row ->
                row.id to (results[row.manifestPerm] ?: granted[row.id] ?: false)
            }
        },
    )

    val micOk = granted["mic"] == true
    val storageOk = granted["storage"] == true || granted["media"] == true
    val canContinue = micOk && storageOk

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MeshlitInk),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            // Lead bar with the v2 bold-lead treatment.
            MeshlitDeepLinkWrap(
                headline = "Permissions",
                subtitle = "Grant what you need to bring the cluster online",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    perms.forEach { row ->
                        PermissionRowCard(
                            row = row,
                            granted = granted[row.id] == true,
                            onGrant = {
                                val toAsk = perms
                                    .filter { !isGranted(context, it.manifestPerm) }
                                    .map { it.manifestPerm }
                                    .distinct()
                                    .toTypedArray()
                                if (toAsk.isNotEmpty()) {
                                    launcher.launch(toAsk)
                                }
                            },
                        )
                    }
                }
            }

            // Bottom bar — fixed at bottom with help + Continue.
            // The continue button stays disabled until mic + storage
            // are both granted.
            Surface(
                color = MeshlitSurface,
                shape = RoundedCornerShape(20.dp),
                tonalElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = if (canContinue) "Ready when you are." else "Microphone + storage are required to start the cluster.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MeshlitTextSecondaryV2,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TextButton(
                            onClick = onSkip,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = "Skip for now",
                                color = MeshlitTextSecondaryV2,
                            )
                        }
                        Button(
                            onClick = onContinue,
                            enabled = canContinue,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MeshlitPulseViolet,
                                contentColor = Color.White,
                                disabledContainerColor = MeshlitPulseViolet.copy(alpha = 0.35f),
                                disabledContentColor = Color.White.copy(alpha = 0.7f),
                            ),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = "Continue",
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class PermissionRow(
    val id: String,
    val icon: ImageVector,
    val name: String,
    val reason: String,
    val manifestPerm: String,
)

@Composable
private fun PermissionRowCard(
    row: PermissionRow,
    granted: Boolean,
    onGrant: () -> Unit,
) {
    Surface(
        color = if (granted) MeshlitPulseViolet.copy(alpha = 0.10f) else MeshlitSurfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = if (granted) MeshlitPulseViolet else MeshlitSurface,
                        shape = RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = row.icon,
                    contentDescription = row.name,
                    tint = if (granted) Color.White else MeshlitPulseViolet,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.name,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MeshlitTextPrimaryV2,
                )
                Text(
                    text = row.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MeshlitTextTertiaryV2,
                )
            }
            TextButton(
                onClick = onGrant,
                enabled = !granted,
            ) {
                Text(
                    text = if (granted) "Granted" else "Grant",
                    color = if (granted) MeshlitTextSecondaryV2 else MeshlitPulseViolet,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private fun isGranted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED