package com.meshlit.ui.screens.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Section header label used inside [ModelsScreen]. Renamed to
 * `ModelsSectionHeader` to avoid colliding with the file-local
 * `SectionHeader` defined in `ThemeCustomizationScreen.kt`.
 */
@Composable
internal fun ModelsSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}