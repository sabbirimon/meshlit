package com.meshlit.ui.modern

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Direct entry to the same real worker controls used by Network and pairing. */
@Composable internal fun ShardingSettingsScreen(onBack: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().testTag("sharding-settings-list"), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick = onBack) { Text("Back to settings") } }
        item { Text("Sharding and workers", style = MaterialTheme.typography.headlineSmall) }
        item { Text("Select a group, approve live workers, then negotiate a compatible layer placement. The coordinator still needs the complete model file. At least two approved workers are required for cluster load.") }
        item { com.meshlit.pipeline.PipelinePanel() }
    }
}
