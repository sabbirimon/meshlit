package com.meshlit.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Controls actual knowledge retrieval; legacy backend/display selectors are no longer presented. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun RagSettingsScreen(onBack:(()->Unit)?=null,onOpenConnected:(()->Unit)?=null) {
    Scaffold(topBar={if(onBack!=null)TopAppBar(title={Text("Knowledge and memory")},navigationIcon={TextButton(onClick=onBack){Text("Back")}})}){padding->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("Give Meshlit useful reference material. Choose what it can recall and where excerpts may be sent.")
            KnowledgeLibraryPanel()
            if(onOpenConnected!=null){HorizontalDivider();Text("Connected memory",style=MaterialTheme.typography.titleMedium)
                Text("Use an existing OpenViking service alongside your private local knowledge. Its connection and recall permissions are separate.")
                OutlinedButton(onClick=onOpenConnected){Text("Manage connected memory")}
            }
        }
    }
}
