package com.meshlit.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.meshlit.MeshlitApplication
import com.meshlit.core.net.openrouter.OpenRouterClient
import com.meshlit.core.net.openrouter.OpenRouterKeyVault
import com.meshlit.design.MeshlitDesignPalette

/**
 * Phase 4 — OpenRouter settings screen.
 *
 * Reachable from the legacy Settings hub via the
 * [SettingsCategory.OPENROUTER] category card. Hosts the
 * [OpenRouterSettingsCard] and the [OpenRouterModelBrowserScreen]
 * flow for picking a default model.
 *
 * Owns an [OpenRouterModelBrowserViewModel] tied to the application's
 * [OpenRouterKeyVault]. The vault is process-singleton (constructed
 * once on the [MeshlitApplication] instance) so two screens binding
 * to it share the same encrypted prefs.
 *
 * Backward-compat: if the user has never opened this screen, the
 * vault is created lazily on first visit.
 */
@Composable
fun OpenRouterSettingsScreen(
    onBack: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onOpenDrawer: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as MeshlitApplication

    // Vault is created per-screen-instance so the test suite can
    // swap it for an InMemoryVault without touching the app
    // singleton. Production code reuses the same prefs file
    // (`openrouter_key_vault`) regardless of which screen owns the
    // instance, so persistence is shared.
    val vault = remember { OpenRouterKeyVault(context) }
    val viewModel = remember(vault) {
        OpenRouterModelBrowserViewModel(
            vault = vault,
            client = OpenRouterClient(),
        )
    }
    val uiStatus by viewModel.status.collectAsState()
    val selectedModelId by viewModel.selectedModelId.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(16.dp),
    ) {
        item {
            OpenRouterSettingsCard(
                palette = MeshlitDesignPalette,
                status = uiStatus.toComposable(),
                onSave = { rawKey ->
                    viewModel.saveKey(rawKey)
                },
                onDisconnect = {
                    viewModel.disconnect()
                },
                onRetryValidation = {
                    viewModel.retryValidation()
                },
                onPickModel = {
                    // Wired by host activity; on this screen the
                    // model browser is launched separately by the
                    // bottom CTA when needed.
                },
                currentModelDisplayName = selectedModelId,
            )
        }
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}