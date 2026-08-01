package com.meshlit.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.meshlit.ui.nav.TopLevelDestination
import com.meshlit.ui.screens.JobsScreen
import com.meshlit.ui.screens.ScreenStub
import com.meshlit.ui.screens.settings.CategoryScreen
import com.meshlit.ui.screens.settings.ForwardingPeersScreen
import com.meshlit.ui.screens.settings.SettingsCategory
import com.meshlit.ui.screens.settings.SettingsScreen

/**
 * Root composable for the app. Hosts the bottom navigation and routes
 * to per-screen composables. The Settings tab gets a real hub (not a
 * stub) which then routes into a [CategoryScreen] per category.
 *
 * Navigation hierarchy:
 *   NavHost
 *   ├── top-level destination 1..9 (stubs or full screens)
 *   └── settings/category/{DEVICE|THEME|...} (deep links from search)
 */
@Composable
fun MeshlitApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: TopLevelDestination.Devices.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopLevelDestination.all.forEach { dest ->
                    val selected = backStackEntry?.destination
                        ?.hierarchy
                        ?.any { it.route == dest.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (currentRoute != dest.route) {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = dest.icon,
                                contentDescription = stringResource(dest.labelRes),
                            )
                        },
                        label = { Text(stringResource(dest.labelRes)) },
                        colors = NavigationBarItemDefaults.colors(),
                    )
                }
            }
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
                    // to the inference foreground service. Everything else
                    // remains a stub for now.
                    when (dest) {
                        TopLevelDestination.Settings -> SettingsScreen(
                            onOpenCategory = { cat ->
                                navController.navigate("settings/category/${cat.name}")
                            },
                        )
                        TopLevelDestination.Jobs -> JobsScreen()
                        else -> ScreenStub(destination = dest, icon = dest.icon)
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
        }
    }
}