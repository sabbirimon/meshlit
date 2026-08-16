package com.meshlit.ui.screens.help

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meshlit.R

/**
 * Phase Observability 1 — per-feature manual screen.
 *
 * Renders every entry from [ManualSection.all] as a card. Each
 * card shows the feature's title, intent, use case, config steps,
 * and troubleshooting. CTA buttons on config steps navigate into
 * the matching screen.
 *
 * Why we hand-roll the layout rather than use Markdown:
 *   - No Compose Markdown library is on the classpath. Adding one
 *     to ship a manual would be a one-trick dep.
 *   - Cards give us typed config buttons that actually navigate
 *     the user into the right screen — Markdown can't.
 *   - The structured shape forces every section to have a use
 *     case and troubleshooting entry, which is the point of the
 *     manual.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManualScreen(
    onBack: () -> Unit,
    onOpenSection: ((ManualSection) -> Unit)? = null,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.help_manual_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(ManualSection.all, key = { it.title }) { section ->
                ManualCard(section = section, onCta = { onOpenSection?.invoke(section) })
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ManualCard(
    section: ManualSection,
    onCta: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.height(6.dp))
            LabelValue(
                label = stringResource(R.string.help_tour_section_intent),
                value = section.intent,
            )
            Spacer(Modifier.height(4.dp))
            LabelValue(
                label = stringResource(R.string.help_tour_section_usecase),
                value = section.useCase,
            )

            if (section.configSteps.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.help_tour_section_config),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                section.configSteps.forEach { step ->
                    ConfigStepRow(step)
                }
            }

            if (section.troubleshooting.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.help_tour_section_trouble),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.tertiary,
                )
                Spacer(Modifier.height(4.dp))
                section.troubleshooting.forEach { entry ->
                    TroubleRow(entry)
                }
            }
        }
    }
}

@Composable
private fun LabelValue(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ConfigStepRow(step: ConfigStep) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = step.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        )
        Text(
            text = step.body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (step.ctaLabel != null && step.onClick != null) {
            TextButton(onClick = step.onClick) {
                Text(text = step.ctaLabel)
            }
        }
    }
}

@Composable
private fun TroubleRow(entry: TroubleEntry) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = entry.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        )
        Text(
            text = entry.body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}