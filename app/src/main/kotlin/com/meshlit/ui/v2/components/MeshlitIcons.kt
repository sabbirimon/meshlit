package com.meshlit.ui.v2.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.ModelTraining
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * v2-only icon catalogue. Mirrors the v1 destination icons in
 * `MeshlitApp.kt` so the v2 chrome uses the same Material 3
 * filled glyphs — but kept in a dedicated file so the v1 build
 * never pulls in `MeshlitAppV2`'s `MeshlitV2Destination` enum.
 *
 * The 17 entries cover every destination listed in the plan's
 * §"Drawer" bullet (17 destinations, full-width rows in the
 * drawer) plus the 9 entries used by the bottom bar.
 *
 * The mapping intentionally lives here (not on the enum) so
 * the enum can stay a value type — the icons are presentation,
 * the enum is data.
 */
object MeshlitIcons {
    val Devices: ImageVector = Icons.Filled.Devices
    val Jobs: ImageVector = Icons.Filled.WorkOutline
    val Voice: ImageVector = Icons.Filled.Mic
    val Agent: ImageVector = Icons.Filled.Assistant
    val Models: ImageVector = Icons.Filled.ModelTraining
    val Structured: ImageVector = Icons.Filled.Code
    val Vision: ImageVector = Icons.Filled.Image
    val Catalog: ImageVector = Icons.Filled.Category
    val Advanced: ImageVector = Icons.Filled.Build
    val Cluster: ImageVector = Icons.Filled.Hub
    val Files: ImageVector = Icons.Filled.Storage
    val Sessions: ImageVector = Icons.Filled.AccountTree
    val Network: ImageVector = Icons.Filled.Dashboard
    val Users: ImageVector = Icons.Filled.People
    val Cloud: ImageVector = Icons.Filled.Cloud
    val Settings: ImageVector = Icons.Filled.Settings
    val Help: ImageVector = Icons.Filled.GraphicEq
    val Menu: ImageVector = Icons.Filled.Menu
}
