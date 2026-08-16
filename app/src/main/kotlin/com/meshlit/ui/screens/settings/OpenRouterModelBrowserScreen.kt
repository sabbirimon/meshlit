package com.meshlit.ui.screens.settings

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.meshlit.core.net.openrouter.OpenRouterModel
import com.meshlit.design.MeshlitDesignPalette
import com.meshlit.design.MeshlitDesignSystem
import com.meshlit.design.MeshlitGlassCard
import com.meshlit.design.MeshlitMeshGradientBackground
import com.meshlit.design.StitchPalette
import com.meshlit.design.glow

/**
 * Phase 4 — OpenRouter model browser.
 *
 * Pixel-for-pixel port of the "Model Browser" pattern from the
 * Stitch dashboard, tuned for the OpenRouter catalog. Shows the
 * full model list returned by `/api/v1/models` with:
 *
 *  - Search bar (filters on `name` + `id`)
 *  - Provider section headers (OpenAI, Anthropic, Meta, …) —
 *    sorted alphabetically
 *  - Per-row glass surface: name · context length · $X.XX / 1M
 *    prompt + $Y.YY / 1M completion
 *  - Selected-row glow + check
 *
 * The host activity passes the [viewModel] which holds the cached
 * model list + the current selected model id. Backward-compatible:
 * the screen renders the same surface whether the catalog was
 * fetched once at cold-start or refreshed every minute.
 */
@Composable
fun OpenRouterModelBrowserScreen(
    palette: StitchPalette = StitchPalette.DARK,
    viewModel: OpenRouterModelBrowserViewModel,
    onPick: (OpenRouterModel) -> Unit,
    onBack: () -> Unit,
) {
    val models by viewModel.models.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val selectedModelId by viewModel.selectedModelId.collectAsState()
    val designPalette: MeshlitDesignPalette = MeshlitDesignPalette

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    MeshlitDesignSystem(palette = palette) {
        Box(modifier = Modifier.fillMaxSize()) {
            MeshlitMeshGradientBackground(palette = palette)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp),
            ) {
                Spacer(Modifier.height(20.dp))
                Header(palette = designPalette, onBack = onBack)
                Spacer(Modifier.height(12.dp))

                SearchBar(
                    palette = designPalette,
                    onQueryChange = { viewModel.setQuery(it) },
                )
                Spacer(Modifier.height(12.dp))

                when {
                    loading && models.isEmpty() -> LoadingState(designPalette)
                    error != null && models.isEmpty() -> ErrorState(designPalette, error!!)
                    models.isEmpty() -> EmptyState(designPalette)
                    else -> ModelList(
                        palette = designPalette,
                        models = viewModel.groupedFiltered(),
                        selectedId = selectedModelId,
                        onPick = onPick,
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(palette: MeshlitDesignPalette, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "OpenRouter Models",
            style = MaterialTheme.typography.titleLarge,
            color = palette.textPrimary,
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onBack),
        )
        Text(
            text = "Back",
            style = MaterialTheme.typography.labelMedium,
            color = palette.iridescentStart,
            modifier = Modifier.clickable(onClick = onBack),
        )
    }
}

@Composable
private fun SearchBar(
    palette: MeshlitDesignPalette,
    onQueryChange: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    OutlinedTextField(
        value = query,
        onValueChange = {
            query = it
            onQueryChange(it)
        },
        placeholder = { Text("Search 500+ models") },
        singleLine = true,
        leadingIcon = {
            Icon(Icons.Filled.Search, contentDescription = "Search")
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .glow(palette.haloCyanSoft, radius = 14.dp),
    )
}

@Composable
private fun ModelList(
    palette: MeshlitDesignPalette,
    models: List<Pair<String, List<OpenRouterModel>>>,
    selectedId: String?,
    onPick: (OpenRouterModel) -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        models.forEach { (provider, rows) ->
            item(key = "h:$provider") {
                ProviderHeader(palette, provider)
            }
            items(rows, key = { "m:${it.id}" }) { model ->
                ModelRow(
                    palette = palette,
                    model = model,
                    isSelected = model.id == selectedId,
                    onPick = onPick,
                )
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun ProviderHeader(palette: MeshlitDesignPalette, label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = palette.iridescentStart,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun ModelRow(
    palette: MeshlitDesignPalette,
    model: OpenRouterModel,
    isSelected: Boolean,
    onPick: (OpenRouterModel) -> Unit,
) {
    MeshlitGlassCard(
        palette = StitchPalette.DARK,
        cornerRadius = 16.dp,
        contentPadding = 16.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPick(model) }
            .let {
                if (isSelected) {
                    it.glow(palette.haloCyanStrong, radius = 22.dp)
                } else {
                    it
                }
            },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (isSelected) {
                    Text(
                        text = "✓",
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.iridescentEnd,
                    )
                }
            }
            Text(
                text = model.id,
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${formatTokens(model.contextLength)} ctx",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                )
                Text(
                    text = priceLabel(model),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun LoadingState(palette: MeshlitDesignPalette) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = palette.iridescentStart)
    }
}

@Composable
private fun ErrorState(palette: MeshlitDesignPalette, message: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Failed to load models",
            style = MaterialTheme.typography.titleMedium,
            color = palette.iridescentPink,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textSecondary,
        )
    }
}

@Composable
private fun EmptyState(palette: MeshlitDesignPalette) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No models matched your search",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textSecondary,
        )
    }
}

private fun formatTokens(tokens: Long): String = when {
    tokens <= 0 -> "—"
    tokens < 1_000 -> tokens.toString()
    tokens < 1_000_000 -> "${tokens / 1_000}k"
    else -> "${"%.1f".format(tokens / 1_000_000.0)}M"
}

private fun priceLabel(model: OpenRouterModel): String {
    val promptPerMillion = pricePerMillion(model.pricing.prompt)
    val completionPerMillion = pricePerMillion(model.pricing.completion)
    return "$" + "%.2f".format(promptPerMillion) + " / $" + "%.2f".format(completionPerMillion) +
        " per 1M (in/out)"
}

private fun pricePerMillion(rawUsdPerToken: String): Double {
    val raw = rawUsdPerToken.toDoubleOrNull() ?: return 0.0
    return raw * 1_000_000.0
}