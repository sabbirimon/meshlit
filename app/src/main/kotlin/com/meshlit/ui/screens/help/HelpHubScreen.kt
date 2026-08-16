package com.meshlit.ui.screens.help

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meshlit.R

/**
 * Root destination for the drawer's Help tile and the About quick
 * action. Three large rows that route to the User Manual, UI
 * Tour, and Feedback screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpHubScreen(
    onBack: () -> Unit,
    onOpenManual: () -> Unit,
    onOpenTour: () -> Unit,
    onOpenFeedback: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.help_hub_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            HelpEntry(
                icon = Icons.Outlined.Book,
                title = stringResource(R.string.help_hub_manual),
                subtitle = stringResource(R.string.help_hub_manual_subtitle),
                onClick = onOpenManual,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            HelpEntry(
                icon = Icons.Outlined.Map,
                title = stringResource(R.string.help_hub_tour),
                subtitle = stringResource(R.string.help_hub_tour_subtitle),
                onClick = onOpenTour,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            HelpEntry(
                icon = Icons.Outlined.BugReport,
                title = stringResource(R.string.help_hub_feedback),
                subtitle = stringResource(R.string.help_hub_feedback_subtitle),
                onClick = onOpenFeedback,
            )
        }
    }
}

@Composable
private fun HelpEntry(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.size(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}