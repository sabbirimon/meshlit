package com.meshlit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.meshlit.core.common.logger
import com.meshlit.di.koinInject
import com.meshlit.ui.MeshlitApp
import com.meshlit.ui.theme.LocalMeshlitThemeConfig
import com.meshlit.ui.theme.MeshlitTheme
import com.meshlit.ui.v2.V2Root

internal val LocalSharedPrompt = androidx.compose.runtime.staticCompositionLocalOf<androidx.compose.runtime.MutableState<String?>?> { null }

class MainActivity:ComponentActivity() {
    private val sharedPrompt = androidx.compose.runtime.mutableStateOf<String?>(null)

    private fun readShare(source: android.content.Intent?) {
        if (source?.action != android.content.Intent.ACTION_SEND || source.type != "text/plain") return
        val value = source.getCharSequenceExtra(android.content.Intent.EXTRA_TEXT)
        if (value != null && value.length in 1..12000 && value.isNotBlank()) sharedPrompt.value = value.toString()
        else android.widget.Toast.makeText(this,"Share up to 12,000 characters of text.",android.widget.Toast.LENGTH_SHORT).show()
    }
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        readShare(intent)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("pendingSharedText", sharedPrompt.value)
    }
    companion object { @Volatile var foregroundActive:Boolean=false; private set }
    override fun onResume(){super.onResume();foregroundActive=true;(application as MeshlitApplication).refreshDiscovery()}
    override fun onPause(){foregroundActive=false;(application as MeshlitApplication).refreshDiscovery();super.onPause()}
    private val log=logger("MainActivity")
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) readShare(intent) else sharedPrompt.value = savedInstanceState.getString("pendingSharedText")
        log.info("activity.create","MainActivity onCreate",mapOf("ui_version" to if(BuildConfig.USE_NEW_UI) "v2" else "v1"))
        setContent {
            val repository:com.meshlit.settings.SettingsRepository=koinInject()
            val config by repository.flow.collectAsState(initial=com.meshlit.ui.theme.MeshlitThemeConfig.Default)
            CompositionLocalProvider(LocalMeshlitThemeConfig provides config, LocalSharedPrompt provides sharedPrompt) {
                MeshlitTheme {
                    ThemedAppWindow {
                        // Both flavors use the shared optional permission setup. A second
                        // legacy wizard must not race navigation or cover the first chat.
                        com.meshlit.legal.LegalAgreementGate(onDecline = { finishAndRemoveTask() }) {
                            if(BuildConfig.USE_NEW_UI) V2Root() else MeshlitApp()
                        }
                    }
                }
            }
        }
    }
}

/** Match Android's status icons, navigation controls and inset backgrounds to the in-app palette. */
@Composable
internal fun ComponentActivity.ThemedAppWindow(content: @Composable () -> Unit) {
    val background = MaterialTheme.colorScheme.background
    DisposableEffect(this, background) {
        val color = background.toArgb()
        // Before API 26 navigation buttons are always white and need a dark fallback.
        val style = if (background.luminance() > 0.179f)
            SystemBarStyle.light(color, android.graphics.Color.BLACK)
        else
            SystemBarStyle.dark(color)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose { }
    }
    Box(Modifier.fillMaxSize().background(background)) { content() }
}
