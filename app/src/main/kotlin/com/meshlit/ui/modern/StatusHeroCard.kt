package com.meshlit.ui.modern

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

/** Compact, theme-aware surface. Callers provide observed state and real actions. */
@Composable fun StatusHeroCard(title: String, description: String, action: (@Composable () -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(24.dp), color = colors.primaryContainer) {
        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(colors.primaryContainer, colors.secondaryContainer)))
            .padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = colors.onPrimaryContainer)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = colors.onPrimaryContainer)
            action?.invoke()
        }
    }
}
