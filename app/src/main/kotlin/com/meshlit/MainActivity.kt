package com.meshlit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.meshlit.core.common.logger
import com.meshlit.di.koinInject
import com.meshlit.ui.MeshlitApp
import com.meshlit.ui.theme.LocalMeshlitThemeConfig
import com.meshlit.ui.theme.MeshlitTheme
import com.meshlit.ui.v2.V2Root

class MainActivity:ComponentActivity() {
    companion object { @Volatile var foregroundActive:Boolean=false; private set }
    override fun onResume(){super.onResume();foregroundActive=true;(application as MeshlitApplication).refreshDiscovery()}
    override fun onPause(){foregroundActive=false;(application as MeshlitApplication).refreshDiscovery();super.onPause()}
    private val log=logger("MainActivity")
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState);enableEdgeToEdge()
        log.info("activity.create","MainActivity onCreate",mapOf("ui_version" to if(BuildConfig.USE_NEW_UI) "v2" else "v1"))
        setContent {
            val repository:com.meshlit.settings.SettingsRepository=koinInject()
            val config by repository.flow.collectAsState(initial=com.meshlit.ui.theme.MeshlitThemeConfig.Default)
            CompositionLocalProvider(LocalMeshlitThemeConfig provides config) {
                MeshlitTheme {
                    // Both flavors use the shared optional permission setup. A second
                    // legacy wizard must not race navigation or cover the first chat.
                    com.meshlit.legal.LegalAgreementGate(onDecline = { finishAndRemoveTask() }) {
                        com.meshlit.ui.modern.BootLoadingGate {
                            if(BuildConfig.USE_NEW_UI) V2Root() else MeshlitApp()
                        }
                    }
                }
            }
        }
    }
}
