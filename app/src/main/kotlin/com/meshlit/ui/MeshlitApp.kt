package com.meshlit.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.meshlit.MeshlitApplication
import com.meshlit.agent.AgentScreen
import com.meshlit.feature.advanced.AdvancedScreen
import com.meshlit.feature.advanced.GhostyHost
import com.meshlit.feature.advanced.LocalGhostyHost
import com.meshlit.feature.ghosty.GhostyOverlayService
import com.meshlit.mcp.AppMcpHost
import com.meshlit.ui.components.MeshlitBottomBar
import com.meshlit.ui.components.MeshlitDrawerContent
import com.meshlit.ui.components.QuickAction
import com.meshlit.ui.components.tierAccentColor
import com.meshlit.ui.nav.TopLevelDestination
import com.meshlit.ui.screens.CatalogScreen
import com.meshlit.ui.screens.DevicesScreen
import com.meshlit.ui.screens.FilesScreen
import com.meshlit.ui.screens.JobsScreen
import com.meshlit.ui.screens.LogScreen
import com.meshlit.ui.screens.MetricsScreen
import com.meshlit.terminal.TerminalScreen
import com.meshlit.ui.screens.ScreenStub
import com.meshlit.ui.screens.StructuredScreen
import com.meshlit.ui.screens.VisionScreen
import com.meshlit.ui.screens.VoiceScreen
import com.meshlit.ui.screens.cloud.AgentLoopMode
import com.meshlit.ui.screens.cloud.AgentTerminalScreen
import com.meshlit.ui.screens.cloud.AddCustomCloudResult
import com.meshlit.ui.screens.cloud.AddCustomCloudScreen
import com.meshlit.ui.screens.cloud.CloudHubScreen
import com.meshlit.ui.quickactions.BoostViewModel
import com.meshlit.ui.quickactions.SyncViewModel
import com.meshlit.ui.screens.settings.CategoryScreen
import com.meshlit.ui.screens.help.FeedbackScreen
import com.meshlit.ui.screens.help.HelpHubScreen
import com.meshlit.ui.screens.help.UiTourScreen
import com.meshlit.ui.screens.help.UserManualScreen
import com.meshlit.ui.screens.network.NetworkMonitorScreen
import com.meshlit.ui.screens.settings.ForwardingPeersScreen
import com.meshlit.ui.screens.settings.RagSettingsScreen
import com.meshlit.ui.screens.settings.SettingsCategory
import com.meshlit.ui.screens.settings.ModelsScreen
import com.meshlit.ui.screens.settings.SettingsScreen
import kotlinx.coroutines.launch

/**
 * Root composable for the app. Hosts the bottom navigation and routes
 * to per-screen composables. The Settings tab gets a real hub (not a
 * stub) which then routes into a [CategoryScreen] per category.
 *
 * Navigation hierarchy:
 *   ModalNavigationDrawer
 *   └── Scaffold (bottom nav)
 *       ├── NavHost
 *       │   ├── top-level destination 1..9 (stubs or full screens)
 *       │   └── settings/category/{DEVICE|THEME|...} (deep links)
 *       └── NavigationBar (bottom)
 *
 * The drawer is opened by left-edge swipe or by tapping the hamburger
 * icon in the per-screen header. Each screen opts in by accepting the
 * [onOpenDrawer] callback exposed via the screen's first parameter.
 */
@Composable
fun MeshlitApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: TopLevelDestination.Devices.route
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val capabilityTier = (context.applicationContext as MeshlitApplication).capabilityTier
    val app: MeshlitApplication = context.applicationContext as MeshlitApplication
    val mcpHost = remember { AppMcpHost(app) }
    val ghostyHost = remember {
        object : GhostyHost {
            override fun setEnabled(enabled: Boolean) {
                if (enabled) {
                    GhostyOverlayService.start(context)
                } else {
                    GhostyOverlayService.stop(context)
                }
            }
        }
    }

    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
    val closeDrawer: () -> Unit = { scope.launch { drawerState.close() } }

    // Quick-action view models. Created lazily so the drawer can
    // call into them without rebuilding the whole nav tree.
    val syncVm = remember { SyncViewModel(app) }
    val boostVm = remember { BoostViewModel(app) }

    val navigateTo: (TopLevelDestination) -> Unit = { dest ->
        navController.navigate(dest.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
        closeDrawer()
    }

    // Phase Observability 1 — drawer quick actions. Sync resyncs
    // the model catalog, Boost toggles inference-boost (thread
    // priority + NPU/GPU engine preference), About opens the
    // new Help root which hosts the manual + tour + feedback.
    val onQuickAction: (QuickAction) -> Unit = { action ->
        closeDrawer()
        when (action) {
            QuickAction.SYNC -> syncVm.sync(context)
            QuickAction.BOOST -> boostVm.boost(context)
            QuickAction.ABOUT -> navController.navigate(TopLevelDestination.Help.route)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            MeshlitDrawerContent(
                currentRoute = currentRoute,
                tier = capabilityTier,
                onSelectDestination = navigateTo,
                onQuickAction = onQuickAction,
                modifier = Modifier.padding(end = 0.dp),
            )
        },
    ) {
        Scaffold(
            // Child screens own their status-bar insets through their
            // own topBar / MeshlitHeader. The root scaffold only owns
            // the bottom navigation; consuming status bars here caused
            // a double top inset (a large empty gap above every header).
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                MeshlitBottomBar(
                    destinations = TopLevelDestination.barItems,
                    currentRoute = currentRoute,
                    accentColor = tierAccentColor(capabilityTier),
                    onSelect = { dest ->
                        if (currentRoute != dest.route) {
                            navigateTo(dest)
                        }
                    },
                )
            }
        ) { innerPadding: PaddingValues ->
            NavHost(
                navController = navController,
                startDestination = TopLevelDestination.Devices.route,
                modifier = Modifier.padding(innerPadding),
            ) {
                TopLevelDestination.all.forEach { dest ->
                    composable(dest.route) {
                        // Settings tab uses the real Settings hub; Jobs tab binds
                        // to the inference foreground service; Cluster tab hosts
                        // the metrics screen. Everything else remains a stub
                        // for now.
                        when (dest) {
                            TopLevelDestination.Settings -> SettingsScreen(
                                onOpenDrawer = openDrawer,
                                onOpenCategory = { cat ->
                                    navController.navigate("settings/category/${cat.name}")
                                },
                            )
                            TopLevelDestination.Devices -> DevicesScreen(onOpenDrawer = openDrawer)
                            TopLevelDestination.Jobs -> JobsScreen(
                                onOpenDrawer = openDrawer,
                                onOpenModels = {
                                    navController.navigate(TopLevelDestination.Models.route) {
                                        launchSingleTop = true
                                    }
                                },
                            )
                            TopLevelDestination.Agent -> AgentScreen(
                                onOpenDrawer = openDrawer,
                                onOpenModels = {
                                    navController.navigate(TopLevelDestination.Models.route) {
                                        launchSingleTop = true
                                    }
                                },
                            )
                            TopLevelDestination.Models -> ModelsScreen(onBack = openDrawer)
                            TopLevelDestination.Advanced -> AdvancedScreen(
                                accent = tierAccentColor(capabilityTier),
                                accentDim = tierAccentColor(capabilityTier),
                                onNavigate = { dest ->
                                    navController.navigate(dest.route)
                                },
                            )
                            TopLevelDestination.Sessions -> TerminalScreen(onOpenDrawer = openDrawer)
                            TopLevelDestination.Files -> FilesScreen(onOpenDrawer = openDrawer)
                            TopLevelDestination.Cluster -> MetricsScreen(
                                onOpenDrawer = openDrawer,
                                onBack = { navController.popBackStack() },
                            )
                            // Phase 2.x — full SDK surface screens
                            TopLevelDestination.Voice -> VoiceScreen(onOpenDrawer = openDrawer)
                            TopLevelDestination.Structured -> StructuredScreen(onOpenDrawer = openDrawer)
                            TopLevelDestination.Catalog -> CatalogScreen(onOpenDrawer = openDrawer)
                            TopLevelDestination.Vision -> VisionScreen(onOpenDrawer = openDrawer)
                            TopLevelDestination.Cloud -> CloudHubScreen(
                                onOpenDrawer = openDrawer,
                                onOpenAddCustom = { navController.navigate("cloud/add") },
                                onOpenTerminal = { providerId ->
                                    val arg = providerId ?: ""
                                    navController.navigate("cloud/terminal?providerId=$arg")
                                },
                            )
                            TopLevelDestination.Network -> NetworkMonitorScreen(
                                onBack = { navController.popBackStack() },
                                onOpenDrawer = openDrawer,
                            )
                            TopLevelDestination.Help -> HelpHubScreen(
                                onBack = { navController.popBackStack() },
                                onOpenManual = { navController.navigate("help/manual") },
                                onOpenTour = { navController.navigate("help/tour") },
                                onOpenFeedback = { navController.navigate("help/feedback") },
                            )
                            else -> ScreenStub(
                                destination = dest,
                                icon = dest.icon,
                                onOpenDrawer = openDrawer,
                            )
                        }
                    }
                }

                // Deep links from Settings → Category.
                SettingsCategory.entries.forEach { cat ->
                    composable("settings/category/${cat.name}") {
                        CategoryScreen(
                            category = cat,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }

                // Forwarding peers screen (Phase 1, task #7).
                composable("settings/forwarding-peers") {
                    ForwardingPeersScreen(
                        onBack = { navController.popBackStack() },
                    )
                }

                // Cluster metrics (Phase M.3).
                composable("metrics") {
                    MetricsScreen(
                        onBack = { navController.popBackStack() },
                    )
                }

                // Log viewer (Phase M.4).
                composable("logs") {
                    LogScreen(
                        onBack = { navController.popBackStack() },
                    )
                }

                // Cloud MCP — Add Custom provider form.
                composable("cloud/add") {
                    AddCustomCloudScreen(
                        onBack = { navController.popBackStack() },
                        onSave = { result: AddCustomCloudResult ->
                            // Wire to coordinator. For now we just pop
                            // back; the coordinator will be installed in
                            // MeshlitApplication and picked up via the
                            // ViewModel layer in the follow-up phase.
                            val app = context.applicationContext as MeshlitApplication
                            app.cloudCoordinator.connect(
                                com.meshlit.core.cloudmcp.ProviderConfig(
                                    id = result.name.lowercase().replace(" ", "-"),
                                    name = result.name,
                                    kind = com.meshlit.core.cloudmcp.ProviderKind.Custom,
                                    baseUrl = result.endpoint,
                                    authKind = result.authKind,
                                    credentialRef = if (result.token.isNotBlank()) {
                                        "${result.name.lowercase()}/token"
                                    } else {
                                        ""
                                    },
                                    ragNamespace = result.ragNamespace,
                                    openApiSpecUrl = result.openApiUrl,
                                ),
                            )
                            if (result.token.isNotBlank()) {
                                app.cloudCredentialStore.put(
                                    result.name.lowercase(),
                                    "token",
                                    result.token,
                                )
                            }
                            navController.popBackStack()
                        },
                    )
                }

                // Cloud MCP — Agent Terminal (Live / Step log).
                composable(
                    route = "cloud/terminal?providerId={providerId}",
                    arguments = listOf(
                        androidx.navigation.navArgument("providerId") {
                            type = androidx.navigation.NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    ),
                ) { backStack ->
                    val providerId = backStack.arguments?.getString("providerId")
                    val app = context.applicationContext as MeshlitApplication
                    AgentTerminalScreen(
                        providerId = providerId,
                        loopMode = app.settingsRepository.loopModeFlowNow(),
                        ragMode = app.settingsRepository.ragModeFlowNow(),
                        ragDecision = null,
                        onBack = { navController.popBackStack() },
                        onLoopModeChange = { mode ->
                            app.appScope.launch {
                                app.settingsRepository.setLoopMode(mode)
                            }
                        },
                        onSend = { prompt ->
                            app.runAgentPrompt(
                                providerId = providerId,
                                prompt = prompt,
                            )
                        },
                    )
                }

                // Cloud MCP — Settings → RAG / Loop mode.
                composable("settings/rag") {
                    val app = context.applicationContext as MeshlitApplication
                    RagSettingsScreen(
                        initialRagMode = app.settingsRepository.ragModeFlowNow(),
                        initialLoopMode = app.settingsRepository.loopModeFlowNow(),
                        onRagModeChange = { mode ->
                            app.appScope.launch {
                                app.settingsRepository.setRagMode(mode)
                            }
                        },
                        onLoopModeChange = { mode ->
                            app.appScope.launch {
                                app.settingsRepository.setLoopMode(mode)
                            }
                        },
                    )
                }

                // Phase Observability 1 — Help sub-routes. The Help
                // tile and the About quick action land on the
                // HelpHubScreen which dispatches here.
                composable("help/manual") {
                    UserManualScreen(
                        onBack = { navController.popBackStack() },
                    )
                }

                composable("help/tour") {
                    val app = context.applicationContext as MeshlitApplication
                    UiTourScreen(
                        firstRun = app.firstRunSetupRepository,
                        onBack = { navController.popBackStack() },
                        onOpenDestination = { dest ->
                            navController.navigate(dest.route) {
                                launchSingleTop = true
                            }
                        },
                    )
                }

                composable("help/feedback") {
                    val app = context.applicationContext as MeshlitApplication
                    FeedbackScreen(
                        settings = app.settingsRepository,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}