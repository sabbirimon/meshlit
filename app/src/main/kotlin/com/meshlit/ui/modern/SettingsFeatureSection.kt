package com.meshlit.ui.modern

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** A feature boundary, not a separate container around every control. Titles wrap at large font sizes. */
@Composable internal fun SettingsFeatureSection(title:String,modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    Surface(modifier.fillMaxWidth().padding(vertical=4.dp),shape=RoundedCornerShape(12.dp),
        color=MaterialTheme.colorScheme.surfaceContainerLow,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(title,style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary)
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            content()
        }
    }
}
