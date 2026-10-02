package com.meshlit.ui.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.ModelTraining
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.meshlit.ui.v2.components.MeshlitQuickActionSheet
import com.meshlit.ui.v2.components.QuickAction
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.window.core.layout.WindowWidthSizeClass
import com.meshlit.ui.theme.MeshlitInk
import com.meshlit.ui.theme.MeshlitOutlineV2
import com.meshlit.ui.theme.MeshlitPulseViolet
import com.meshlit.ui.theme.MeshlitSurface
import com.meshlit.ui.theme.MeshlitSurfaceContainer
import com.meshlit.ui.theme.MeshlitSurfaceHigh
import com.meshlit.ui.theme.MeshlitTextPrimaryV2
import com.meshlit.ui.theme.MeshlitTextSecondaryV2
import com.meshlit.ui.theme.MeshlitTextTertiaryV2
import com.meshlit.ui.v2.screens.AgentScreen
import com.meshlit.ui.v2.screens.ClusterScreen
import com.meshlit.ui.v2.screens.DevicesScreen
import com.meshlit.ui.screens.settings.SettingsCategory
import kotlinx.coroutines.launch

/**
 * The v2 app shell. Adaptive: `PermanentNavigationDrawer` on
 * tablets (`WindowWidthSizeClass.EXPANDED`), `ModalNavigationDrawer`
 * on phones. Both branches own the same `NavHost` + `Scaffold`
 * + bottom-bar + pill-input layout so the chrome is identical.
 *
 * Per the design brief, the drawer is "fullscreen" — on phones
 * it covers the whole width (slide-over, ~ 80% width via M3
 * defaults), on tablets it sits beside the content as a
 * permanent 280 dp rail.
 *
 * For build no. 1 the nav graph is minimal — a single
 * "placeholder" destination so the chrome renders. The real
 * per-screen Composables (Devices / Cluster / Agent / Settings /
 * Bootstrap) wire in step 4.
 *
 * @param startRoute the initial destination. Defaults to
 *        "devices". First-run wizards set this to "setup" and
 *        `V2Root` switches on the same condition as the v1 root.
 */
@Composable
fun MeshlitAppV2(
    startRoute: String = "devices",
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: startRoute
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val isExpandedWidth = adaptiveInfo.windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED

    // The modal drawer's close-on-select callback is wired
    // inside the `else` branch below — drawerState lives in
    // the modal-only branch, so we can't pass `scope.launch`
    // from here. Instead the close callback is plumbed via a
    // mutable function reference that the modal branch sets.
    val closeDrawerRef = remember { CloseDrawerRef() }

    val drawerContent: @Composable () -> Unit = {
        MeshlitFullDrawerContent(
            currentRoute = currentRoute,
            onSelect = { route ->
                navController.navigate(route) {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
                // Close the modal drawer after the user picks
                // a destination so the new screen is visible
                // immediately. Without this the drawer stays
                // open and the new route renders behind the
                // drawer sheet.
                closeDrawerRef.fn()
            },
        )
    }

    val scaffold: @Composable () -> Unit = {
        Scaffold(
            // System bar insets (status + nav) are kept on the
            // Scaffold so the top bar's content sits below the
            // status icons and the bottom bar sits above the
            // system nav buttons. The Compose Material3 default
            // (Consumed by default) is OK, but the v2 chrome
            // overrides only the side insets — the bottom inset
            // is consumed by [MeshlitBottomBarV2] so the
            // indicator pill never collides with the gesture
            // pill.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                Column {
                    MeshlitBottomBarV2(
                        currentRoute = currentRoute,
                        destinations = MeshlitV2Destination.barItems,
                        onSelect = { dest ->
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = startRoute,
                modifier = Modifier.padding(innerPadding),
            ) {
                // Build no. 1 ships with the four real screens wired
                // (Devices, Cluster, Agent, Settings) plus the five
                // bottom-bar destinations backed by v1 screens
                // wrapped via `MeshlitDeepLinkWrap` (Jobs, Models,
                // Structured, Vision, Catalog) and the Advanced
                // hub placeholder. The drawer-only destinations
                // (Files, Sessions, Network, Users, Cloud, Help,
                // Voice) keep the placeholder until step 4 wires
                // each via its own wrapper.
                // The bottom-bar "Devices" tab now lands on the
                // v2 hub (scan / info / cluster / local group /
                // networks map / device management) instead of
                // the legacy "0 endpoints" page — the v2 hub
                // owns its own routing into the per-action
                // screens (V2ScanScreen, V2ClusterScreen, etc.)
                // once they ship. The legacy v1 DevicesScreen
                // stays reachable via the v1 build.
                composable("devices") {
                    com.meshlit.ui.v2.screens.V2DevicesHubScreen(
                        onCardNavigate = { route ->
                            navController.navigate(route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
                // Device Info — the real screen behind the Devices
                // hub `Info` / `Identity` / `QR identity` / `Export`
                // cards. Renders role + hardware + identity +
                // bootstrap health + services, with copy / edit /
                // re-probe / re-bootstrap controls.
                composable("device_info") {
                    com.meshlit.ui.v2.screens.DeviceInfoScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
                composable("cluster") { ClusterScreen() }
                composable("agent") { AgentScreen() }
                composable("settings") {
                    com.meshlit.ui.v2.screens.SettingsScreen(
                        onOpenCategory = { cat ->
                            navController.navigate("settings/category/${cat.name}")
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable("settings/category/{name}") { entry ->
                    val name = entry.arguments?.getString("name") ?: return@composable
                    val cat = runCatching { SettingsCategory.valueOf(name) }.getOrNull()
                        ?: return@composable
                    com.meshlit.ui.v2.screens.SettingsCategoryScreen(
                        category = cat,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable("jobs") { com.meshlit.ui.v2.screens.ChatScreen() }
                composable("models") { com.meshlit.ui.v2.screens.V2ModelsScreen() }
                composable("structured") { com.meshlit.ui.v2.screens.V2StructuredScreen() }
                composable("vision") { com.meshlit.ui.v2.screens.V2VisionScreen() }
                composable("catalog") { com.meshlit.ui.v2.screens.V2CatalogScreen() }
                // Peer-discovery + classification. Reads the
                // shared `PeerRepository` (NSD-backed, started in
                // MeshlitApplication.onCreate) and groups peers
                // into LOCAL/CLUSTER/GROUP/INTERNET for the
                // user. Tapping the bottom-bar `Scan` chip on
                // the Devices hub routes here. The QR pairing
                // sheet is self-contained (the v2 screen owns
                // its own `var sheetVisible by remember`); the
                // route just hooks onBack.
                composable("scan") {
                    com.meshlit.ui.v2.screens.V2ScanScreen(
                        onOpenQrPairing = { /* sheet state lives inside the composable */ },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable("advanced") {
                    com.meshlit.ui.v2.screens.V2AdvancedScreen(
                        onCardNavigate = { route ->
                            navController.navigate(route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
                // Drawer-only destinations — each one delegates
                // to a v1 screen wrapped in MeshlitDeepLinkWrap
                // so the lead bar reads with the v2 design
                // language. Previously these routes fell through
                // to the catch-all placeholder, so tapping a
                // drawer entry like "Voice" or "Files" only
                // showed a label.
                composable("voice") { com.meshlit.ui.v2.screens.V2VoiceScreen(onBack = { navController.popBackStack() }) }
                composable("files") { com.meshlit.ui.v2.screens.V2FilesScreen(onBack = { navController.popBackStack() }) }
                composable("sessions") { com.meshlit.ui.v2.screens.V2SessionsScreen(onBack = { navController.popBackStack() }) }
                composable("network") { com.meshlit.ui.v2.screens.V2NetworkScreen() }
                composable("users") { com.meshlit.ui.v2.screens.V2UsersScreen(onBack = { navController.popBackStack() }) }
                composable("cloud") { com.meshlit.ui.v2.screens.V2CloudScreen(onBack = { navController.popBackStack() }) }
                composable("help") { com.meshlit.ui.v2.screens.V2HelpScreen(onBack = { navController.popBackStack() }) }
                // "theme" is not a top-level destination — it's
                // only reachable from the drawer footer. We use
                // a regular `composable` here so the back button
                // pops to the previous tab without resetting the
                // graph.
                composable("theme") { com.meshlit.ui.v2.screens.V2ThemeScreen() }
                // First-run permission gate. Reachable from the
                // drawer (Permissions destination) and from the
                // setup wizard. The screen is harmless to visit
                // even after the user has granted everything —
                // it just shows the granted state.
                composable("permissions") {
                    com.meshlit.ui.v2.screens.V2PermissionsScreen(
                        onContinue = { navController.navigate("devices") },
                        onSkip = { navController.navigate("devices") },
                    )
                }
                // Loader demo — reachable from the drawer's
                // "Loader" destination so designers can preview
                // the modal without triggering a real download.
                composable("loader") {
                    com.meshlit.ui.v2.screens.V2LoaderDemoScreen()
                }
                // Devices hub — scan / info / cluster / local
                // group / networks map / device management cards.
                composable("devices_hub") {
                    com.meshlit.ui.v2.screens.V2DevicesHubScreen(
                        onCardNavigate = { route ->
                            navController.navigate(route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
                // Routes explicitly registered above (`devices`,
                // `cluster`, `agent`, `settings`, `jobs`, `models`,
                // `structured`, `vision`, `catalog`, `advanced`,
                // `voice`, `files`, `sessions`, `network`, `users`,
                // `cloud`, `help`, `theme`, `permissions`,
                // `loader`, `devices_hub`) are excluded from the
                // catch-all placeholder loop so each route is
                // registered exactly once in the NavHost graph.
                MeshlitV2Destination.all
                    .filter {
                        it.route !in setOf(
                            "devices", "device_info", "cluster", "agent", "settings",
                            "jobs", "models", "structured", "vision", "catalog", "advanced",
                            "voice", "files", "sessions", "network", "users", "cloud", "help",
                            "theme", "permissions", "loader", "devices_hub",
                        )
                    }
                    .forEach { dest ->
                        composable(dest.route) { V2PlaceholderScreen(dest.label) }
                    }
            }
        }
    }

    if (isExpandedWidth) {
        PermanentNavigationDrawer(
            modifier = modifier,
            drawerContent = drawerContent,
        ) { scaffold() }
    } else {
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        // Wire the close-on-select callback so any `onSelect`
        // call from the drawer (top-level destination, footer
        // "Customize palette", or "Permissions" link) auto-
        // closes the drawer sheet.
        closeDrawerRef.fn = { scope.launch { drawerState.close() } }
        // Quick-action sheet state — the "More" icon in the
        // top bar opens MeshlitQuickActionSheet. Each tap
        // dispatches to a placeholder handler (logging only)
        // until the v2 home screen subscribes to its own
        // SyncViewModel / BoostViewModel.
        var showQuickActions by remember { mutableStateOf(false) }
        ModalNavigationDrawer(
            modifier = modifier,
            drawerState = drawerState,
            // Edge-swipe gestures are disabled because phones
            // trigger accidental drawer opens during normal
            // scrolling near the screen edge — the user reported
            // "side menu popped up randomly". Hamburger button is
            // the only entry point; tapping a destination closes
            // the drawer (closeDrawerRef.fn).
            gesturesEnabled = false,
            drawerContent = {
                // On phones we expose a top-bar hamburger in the
                // placeholder so the drawer can be opened. The
                // real per-screen headers (MeshlitLeadBar) are
                // added in step 4 — each LeadBar will dispatch a
                // drawer open via a callback the scaffold wires.
                drawerContent()
            },
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(
                    color = MeshlitInk,
                    modifier = Modifier
                        .fillMaxWidth()
                        // Status bar inset. The top bar is the
                        // v2 chrome's only status-bar surface; the
                        // Scaffold's `contentWindowInsets = 0` rule
                        // means we have to add the inset here.
                        .statusBarsPadding(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.IconButton(onClick = {
                            scope.launch { drawerState.open() }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = "Open navigation",
                                tint = MeshlitTextSecondaryV2,
                            )
                        }
                        androidx.compose.material3.IconButton(onClick = {
                            showQuickActions = true
                        }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "Open quick actions",
                                tint = MeshlitTextSecondaryV2,
                            )
                        }
                        Text(
                            text = "Meshlit",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = MeshlitTextPrimaryV2,
                        )
                    }
                }
                scaffold()
            }
            if (showQuickActions) {
                // Quick-action sheet host. The SYNC / BOOST / ABOUT
                // handlers are placeholders until the v2 home screen
                // subscribes to its own SyncViewModel /
                // BoostViewModel; for build no. 1 we just log the
                // chosen action so the sheet is observable in
                // Compose previews + instrumentation tests.
                MeshlitQuickActionSheet(
                    onDismiss = { showQuickActions = false },
                    onAction = { action ->
                        // Placeholder handlers — the v2 home screen
                        // will subscribe to SyncViewModel /
                        // BoostViewModel + a nav push to the About
                        // hub in a follow-up PR. Logging here so
                        // the sheet is observable in instrumentation
                        // tests + Compose previews.
                        android.util.Log.i(
                            "MeshlitV2.QuickAction",
                            "tap: ${action.name}",
                        )
                    },
                )
            }
        }
    }
}

/**
 * v2 destination enum. Mirrors `TopLevelDestination` in the v1
 * tree but ships with Pulse-tinted icons and a fixed `barItems`
 * subset (the 9 bottom-bar destinations the v1 chrome exposes).
 *
 * Routes match the v1 routes so the same nav graph works
 * eventually — for build no. 1 the placeholder destination
 * just renders the route's label.
 */
enum class MeshlitV2Destination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Devices("devices", "Devices", Icons.Filled.Devices),
    Jobs("jobs", "Jobs", Icons.Filled.WorkOutline),
    Voice("voice", "Voice", Icons.Filled.Mic),
    Agent("agent", "Agent", Icons.Filled.Assistant),
    Models("models", "Models", Icons.Filled.ModelTraining),
    Structured("structured", "Structured", Icons.Filled.Code),
    Vision("vision", "Vision", Icons.Filled.Image),
    Catalog("catalog", "Catalog", Icons.Filled.Category),
    Permissions("permissions", "Permissions", Icons.Filled.Check),
    Loader("loader", "Loader preview", Icons.Filled.Cloud),
    Hub("devices_hub", "Devices hub", Icons.Filled.Hub),
    Advanced("advanced", "Advanced", Icons.Filled.Build),
    Cluster("cluster", "Cluster", Icons.Filled.Hub),
    Files("files", "Files", Icons.Filled.Storage),
    Sessions("sessions", "Sessions", Icons.Filled.AccountTree),
    Network("network", "Network", Icons.Filled.Dashboard),
    Users("users", "Users", Icons.Filled.People),
    Cloud("cloud", "Cloud", Icons.Filled.Cloud),
    Settings("settings", "Settings", Icons.Filled.Settings),
    Help("help", "Help", Icons.Filled.GraphicEq),
    ;

    companion object {
        val all: List<MeshlitV2Destination> = entries
        // Voice is NOT in the bottom bar — it's a mode/tool, not
        // a destination. The agent's pill input already exposes
        // the mic as a trailing action (see MeshlitPillInput
        // onMic), so a separate bottom-bar tab is duplicate UI.
        // Voice stays in the drawer as a full-screen STT
        // workspace (transcribe long audio, export .txt/.srt)
        // and in the PillInput as a quick mic tap.
        val barItems: List<MeshlitV2Destination> = listOf(
            Devices, Jobs, Agent, Models, Structured, Vision, Catalog, Advanced,
        )
        val drawerOnly: List<MeshlitV2Destination> = all - barItems.toSet()
    }
}

/**
 * Fullscreen drawer content (v2). Identity card at the top,
 * then a column of 17 destinations with the active one
 * showing a 4 dp violet rail. The compact quick-action tiles
 * are inline (not a sheet) — a follow-up PR may move them
 * into the `MeshlitQuickActionSheet` for parity with the pill
 * input's menu button.
 */
@Composable
fun MeshlitFullDrawerContent(
    currentRoute: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The side menu needs a solid backdrop so destinations read
    // on their own surface and don't bleed into the page behind.
    // The drawer root sits at `MeshlitSurface` (one step above
    // the underlying `MeshlitInk` Scaffold) which gives the
    // destination rows a slightly lifted canvas to sit on, while
    // the hero identity card keeps its own `MeshlitSurfaceHigh`
    // tone for emphasis. Without this `Surface` wrapper the
    // drawer was transparent — the destination cards floated on
    // top of whatever was rendered behind the modal scrim, which
    // made the side menu feel like a floating toolbar rather
    // than a full-bleed side panel.
    androidx.compose.material3.Surface(
        modifier = modifier.fillMaxSize(),
        color = MeshlitSurface,
        tonalElevation = 4.dp,
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            // The ModalNavigationDrawer slides the content
            // sheet from the leading edge. Without an
            // explicit `navigationBarsPadding` the sheet's
            // last row falls behind the system gesture pill
            // and the "Customize palette" footer becomes
            // untappable. Padding here pushes the entire
            // scroll column up above the gesture pill so
            // every row, including the footer, stays in the
            // touch area.
            .navigationBarsPadding(),
    ) {
    com.meshlit.ui.v2.components.MeshlitHeroIdentity(
        role = "Brain",
        confidence = 0.83f,
        nodeId = "node-7f3a9b2c-1e4d-4a8b-9c2f-3e5d7a8b9c0d",
        modifier = Modifier.fillMaxWidth(),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MeshlitV2Destination.all.forEach { dest ->
            val isSelected = currentRoute == dest.route
            // Each row gets its own card surface so the icon +
            // label sit on top of a clearly-defined pill rather
            // than competing with adjacent rows. The selected
            // row uses MeshlitSurfaceContainer (lifted) plus a
            // 4 dp violet rail; inactive rows use transparent +
            // MeshlitSurface (slightly darker than the drawer
            // background) so the list still reads as a column
            // of "next stops" rather than a flat wall.
            androidx.compose.material3.Surface(
                // Selected rows lift to MeshlitSurfaceHigh (one
                // step above the drawer's MeshlitSurface) so the
                // active destination sits in a clearly carved-out
                // card. Inactive rows are transparent so they read
                // as part of the side menu surface — not a row of
                // floating pills. The 4 dp violet rail on the
                // leading edge is what tells the eye which row is
                // selected; the row background just confirms the
                // choice.
                color = if (isSelected) MeshlitSurfaceHigh else androidx.compose.ui.graphics.Color.Transparent,
                shape = RoundedCornerShape(16.dp),
                tonalElevation = if (isSelected) 2.dp else 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .clickable { onSelect(dest.route) },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = if (isSelected) MeshlitSurfaceHigh else androidx.compose.ui.graphics.Color.Transparent,
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Leading rail (selected only).
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(28.dp)
                            .background(
                                color = if (isSelected) MeshlitPulseViolet else androidx.compose.ui.graphics.Color.Transparent,
                                shape = RoundedCornerShape(2.dp),
                            ),
                    )
                    // Icon container — sized at 36 dp so the glyph
                    // never overlaps the label baseline.
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = if (isSelected) MeshlitPulseViolet.copy(alpha = 0.18f) else MeshlitSurfaceContainer,
                                shape = RoundedCornerShape(10.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = dest.icon,
                            contentDescription = dest.label,
                            tint = if (isSelected) MeshlitPulseViolet else MeshlitTextSecondaryV2,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Text(
                        text = dest.label,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                        color = if (isSelected) MeshlitTextPrimaryV2 else MeshlitTextSecondaryV2,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        // Drawer footer — taps land on the live theme
        // customization screen (accent / palette / scale).
        // The footer sits below the destination list and
        // reads as a separate "settings shortcut" row so
        // the user doesn't have to scroll through 17
        // destinations to find theme controls.
        androidx.compose.material3.Surface(
            color = MeshlitSurface,
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 1.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .clickable { onSelect("theme") },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                androidx.compose.material3.Icon(
                    imageVector = androidx.compose.material.icons.Icons.Filled.Settings,
                    contentDescription = null,
                    tint = MeshlitPulseViolet,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "Customize palette",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MeshlitTextPrimaryV2,
                )
            }
        }
    }
    }
    }
}

/**
 * Build no. 1 placeholder screen. Replaced by the real
 * DevicesScreen / ClusterScreen / AgentScreen / SettingsScreen
 * in step 4. Keeps the chrome (drawer, bottom bar, page-enter)
 * visible so design review can iterate without waiting for the
 * real screens to land.
 */
@Composable
private fun V2PlaceholderScreen(label: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = MeshlitTextPrimaryV2,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "v2 build no. 1 — placeholder",
                style = MaterialTheme.typography.bodyLarge,
                color = MeshlitTextSecondaryV2,
            )
        }
    }
}

/** Holder for the modal drawer's close-on-select callback.
 *  The drawer state lives in the modal branch only, but the
 *  drawer content (and its `onSelect`) is shared with the
 *  permanent drawer branch. We plumb the close callback via
 *  this ref instead of restructuring the tree. The reference
 *  is a no-op until the modal branch captures its
 *  `rememberCoroutineScope` + drawerState. */
private class CloseDrawerRef {
    var fn: () -> Unit = {}
}
