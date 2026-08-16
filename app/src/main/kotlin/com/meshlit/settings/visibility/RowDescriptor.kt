package com.meshlit.settings.visibility

import androidx.compose.runtime.Composable

/**
 * Visibility tier for a single Settings row.
 *
 *  - [SIMPLE] — always visible. The curated hub surface.
 *  - [ADVANCED] — only visible when [SimpleAdvancedStore.mode]
 *    is `false`. Power-user knobs (Tailscale auth key, OTLP
 *    endpoint, raw firewall ports, etc.).
 *
 * The tier tag is intentionally a flat enum, not a permission
 * or feature flag. "Advanced" rows are not gated by trust tier
 * — they're gated by user preference for a clean vs dense UI.
 */
enum class Visibility { SIMPLE, ADVANCED }

/**
 * A single Settings row, paired with its visibility tier.
 * The [content] lambda is invoked only when the row passes
 * the visibility filter, so an ADVANCED row that's hidden
 * never inflates composition cost.
 *
 * Use [SettingsVisibility.Render] to actually emit them; the
 * store-side tier is the only thing that decides visibility.
 */
data class RowDescriptor(
    val tier: Visibility,
    val content: @Composable () -> Unit,
)
