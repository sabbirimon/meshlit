package com.meshlit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.meshlit.core.common.logger
import com.meshlit.permissions.PermissionHelper
import com.meshlit.setup.SetupCoordinator
import com.meshlit.ui.MeshlitApp
import com.meshlit.ui.screens.setup.SetupWizardScreen
import com.meshlit.ui.theme.LocalMeshlitThemeConfig
import com.meshlit.ui.theme.MeshlitTheme

class MainActivity : ComponentActivity() {
    private val log = logger("MainActivity")

    /**
     * Media / storage permission launcher. Used on cold-start so the
     * user sees the "Allow Meshlit to access photos, videos, and
     * audio?" dialog on first launch and the App Info screen
     * surfaces the "Files and media" entry.
     */
    private val mediaPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val anyGranted = grants.values.any { it }
            log.info(
                "perm.media.result",
                "media permission result",
                mapOf("any_granted" to anyGranted.toString()),
            )
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        log.info("activity.create", "MainActivity onCreate")
        val app = application as MeshlitApplication

        // Trigger the POST_NOTIFICATIONS runtime permission on API 33+.
        // On older devices the manifest grant is sufficient; the helper
        // is a no-op there. We call it eagerly here so the dialog
        // appears the first time the user opens the app.
        PermissionHelper.requestNotificationsIfNeeded(this)

        // One-shot media / storage permission request on first launch
        // so the App Info screen lists "Photos and videos", "Files and
        // media", and "Music and audio" rather than hiding them.
        if (!PermissionHelper.hasAllMediaPermissions(this)) {
            mediaPermissionLauncher.launch(PermissionHelper.mediaPermissions)
        }

        setContent {
            // Bind the live theme config to the CompositionLocal so any
            // control in the Settings panel that writes to DataStore
            // immediately re-themes the whole UI.
            val config by app.settingsRepository.flow.collectAsState(
                initial = com.meshlit.ui.theme.MeshlitThemeConfig.Default,
            )
            CompositionLocalProvider(LocalMeshlitThemeConfig provides config) {
                MeshlitTheme {
                    val navController = rememberNavController()
                    val profile = app.oemDetection.profile
                    val coordinator = remember {
                        SetupCoordinator(
                            context = app.applicationContext,
                            repository = app.firstRunSetupRepository,
                            notificationCenter = app.notificationCenter,
                        )
                    }
                    val firstRunDone by app.firstRunSetupRepository.hasFinishedFirstRunFlow
                        .collectAsState(initial = false)
                    var showWizard by remember { mutableStateOf(false) }

                    LaunchedEffect(profile, firstRunDone) {
                        // Show the wizard when:
                        //  - user has not yet finished first run, AND
                        //  - the OEM profile has at least one unfinished step
                        showWizard = !firstRunDone && coordinator.shouldShowWizard(profile)
                    }

                    NavHost(
                        navController = navController,
                        startDestination = if (showWizard) "setup" else "main",
                    ) {
                        composable("setup") {
                            SetupWizardScreen(
                                onFinish = {
                                    showWizard = false
                                    navController.navigate("main") {
                                        popUpTo("setup") { inclusive = true }
                                    }
                                },
                                onOpenSettings = {
                                    showWizard = false
                                    navController.navigate("main")
                                },
                            )
                        }
                        composable("main") {
                            MeshlitApp()
                        }
                    }
                }
            }
        }
    }
}