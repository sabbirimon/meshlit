package com.meshlit.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.meshlit.design.MeshlitBreathingGlowButton
import com.meshlit.design.MeshlitDesignPalette
import com.meshlit.design.MeshlitGlassCard
import com.meshlit.design.StitchPalette
import com.meshlit.design.glow

/**
 * Stitch glass card for the OpenRouter integration (Phase 4).
 *
 * Shown on the Meshlit settings screen under "Cloud providers".
 * Mirrors the design of [AlternativeModelsCard]: a glass surface
 * with a header, a primary action, and per-row status — but
 * tuned for a credential-management flow rather than downloads.
 *
 * UI states (driven by [OpenRouterStatus]):
 *  - **NotConfigured** — prompt + paste-key input + Save button
 *  - **Validating** — spinner + "Validating key…"
 *  - **Connected** — connected card with tier + usage summary
 *  - **Error** — error banner + retry / replace buttons
 *  - **Disconnected** — "Sign out" flow
 *
 * Backward-compat: the card takes a `keyVault: OpenRouterKeyVault`
 * but the actual key write happens in the host activity; this
 * card is purely presentational.
 */
@Composable
internal fun OpenRouterSettingsCard(
    palette: MeshlitDesignPalette,
    status: OpenRouterStatus,
    onSave: (rawKey: String) -> Unit,
    onDisconnect: () -> Unit,
    onRetryValidation: () -> Unit,
    onPickModel: () -> Unit,
    currentModelDisplayName: String?,
    modifier: Modifier = Modifier,
) {
    MeshlitGlassCard(
        palette = StitchPalette.DARK,
        cornerRadius = 24.dp,
        contentPadding = 20.dp,
        modifier = modifier
            .fillMaxWidth()
            .glow(palette.haloCyanSoft, radius = 18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header — icon + title + subtitle.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "OpenRouter",
                    style = MaterialTheme.typography.titleLarge,
                    color = palette.textPrimary,
                )
                Spacer(Modifier.width(8.dp))
                TierBadge(palette, status)
            }
            Text(
                text = "Access 500+ models across 80+ providers. Your key " +
                    "is encrypted with Android Keystore and never leaves " +
                    "this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary,
            )

            HorizontalDivider(color = palette.outline)

            when (status) {
                is OpenRouterStatus.NotConfigured -> KeyEntryBlock(
                    palette = palette,
                    onSave = onSave,
                    error = null,
                )
                is OpenRouterStatus.Validating -> ValidatingBlock(palette)
                is OpenRouterStatus.Connected -> ConnectedBlock(
                    palette = palette,
                    data = status.data,
                    onPickModel = onPickModel,
                    currentModelDisplayName = currentModelDisplayName,
                    onDisconnect = onDisconnect,
                )
                is OpenRouterStatus.Error -> ErrorBlock(
                    palette = palette,
                    message = status.message,
                    onRetry = onRetryValidation,
                    onReplace = { /* fallthrough to key entry by re-mount */ },
                )
            }
        }
    }
}

@Composable
private fun TierBadge(
    palette: MeshlitDesignPalette,
    status: OpenRouterStatus,
) {
    val (label, color) = when (status) {
        is OpenRouterStatus.Connected -> when {
            status.data.isFreeTier -> "Free tier" to palette.iridescentStart
            else -> "Connected" to palette.iridescentEnd
        }
        is OpenRouterStatus.Validating -> "Validating" to palette.iridescentMid
        is OpenRouterStatus.Error -> "Error" to palette.iridescentPink
        OpenRouterStatus.NotConfigured -> "Not set" to palette.textAmber
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun KeyEntryBlock(
    palette: MeshlitDesignPalette,
    onSave: (String) -> Unit,
    error: String?,
) {
    var rawKey by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Paste your OpenRouter API key",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textPrimary,
        )
        OutlinedTextField(
            value = rawKey,
            onValueChange = { rawKey = it.trim() },
            placeholder = { Text("sk-or-v1-…") },
            singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None
                else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
            ),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        imageVector = if (visible) Icons.Filled.VisibilityOff
                            else Icons.Filled.Visibility,
                        contentDescription = if (visible) "Hide" else "Show",
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = palette.iridescentPink,
            )
        }
        MeshlitBreathingGlowButton(
            palette = StitchPalette.DARK,
            enabled = rawKey.length >= 20,
            onClick = { onSave(rawKey) },
            label = "Save & Validate",
        )
    }
}

@Composable
private fun ValidatingBlock(palette: MeshlitDesignPalette) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Validating key with openrouter.ai…",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textSecondary,
        )
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
        )
    }
}

@Composable
private fun ConnectedBlock(
    palette: MeshlitDesignPalette,
    data: OpenRouterAuthDataUi,
    onPickModel: () -> Unit,
    currentModelDisplayName: String?,
    onDisconnect: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatRow(palette, "Tier", if (data.isFreeTier) "Free tier" else "Paid tier")
        StatRow(palette, "Usage this period", data.usageLabel)
        data.label?.let { StatRow(palette, "Key label", it) }
        Spacer(Modifier.height(8.dp))
        MeshlitBreathingGlowButton(
            palette = StitchPalette.DARK,
            onClick = onPickModel,
            label = if (currentModelDisplayName == null) "Pick a default model"
                else "Default: $currentModelDisplayName",
        )
        MeshlitBreathingGlowButton(
            palette = StitchPalette.DARK,
            onClick = onDisconnect,
            label = "Sign out",
        )
    }
}

@Composable
private fun ErrorBlock(
    palette: MeshlitDesignPalette,
    message: String,
    onRetry: () -> Unit,
    onReplace: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "OpenRouter rejected the key",
            style = MaterialTheme.typography.titleMedium,
            color = palette.iridescentPink,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MeshlitBreathingGlowButton(
                palette = StitchPalette.DARK,
                onClick = onRetry,
                label = "Retry",
            )
            MeshlitBreathingGlowButton(
                palette = StitchPalette.DARK,
                onClick = onReplace,
                label = "Replace key",
            )
        }
    }
}

@Composable
private fun StatRow(palette: MeshlitDesignPalette, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textSecondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textPrimary,
        )
    }
}

/**
 * UI-state model for [OpenRouterSettingsCard]. Mirrors the
 * data flow the host activity wires:
 *
 *  NotConfigured → user has not saved a key yet
 *  Validating    → key was saved; vault is hitting /auth/key
 *  Connected     → key validated; auth/key replied with usage data
 *  Error         → /auth/key returned 401 / 5xx / network error
 */
sealed interface OpenRouterStatus {
    data object NotConfigured : OpenRouterStatus
    data object Validating : OpenRouterStatus
    data class Connected(val data: OpenRouterAuthDataUi) : OpenRouterStatus
    data class Error(val message: String) : OpenRouterStatus
}

/**
 * Lightweight UI projection of `OpenRouterAuthKeyData` so the
 * Composable doesn't have to depend on the `core-net` package.
 */
data class OpenRouterAuthDataUi(
    val label: String?,
    val usageLabel: String,
    val isFreeTier: Boolean,
)