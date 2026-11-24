package com.meshlit.ui.modern

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Compact, theme-aware surface. Callers provide observed state and real actions. */
@Composable fun StatusHeroCard(title: String, description: String, action: (@Composable () -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),color=colors.surfaceContainerLow,border=BorderStroke(1.dp,colors.outlineVariant)) {
        Column(Modifier.fillMaxWidth()
            .padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = colors.primary)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            action?.invoke()
        }
    }
}
