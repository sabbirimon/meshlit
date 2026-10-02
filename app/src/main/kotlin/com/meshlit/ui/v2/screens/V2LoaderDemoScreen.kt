package com.meshlit.ui.v2.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meshlit.ui.v2.components.LoaderKind
import com.meshlit.ui.v2.components.LoaderOperation
import com.meshlit.ui.v2.components.MeshlitDeepLinkWrap
import com.meshlit.ui.v2.components.MeshlitLoaderModal

/**
 * Demo / preview surface for the v2 loader modal. Designers
 * (and the user) can pull this up to see how the modal will
 * look during a real download / scan / post / diagnostic.
 *
 * The screen shows 5 example operations at varied progress
 * levels (one indeterminate, three with progress bars, one
 * idle) so the modal reads as a realistic working state.
 *
 * Real callers (`MeshlitCatalogEngine`, `DiscoveryCoordinator`,
 * `RunAnywhereDiagnostics`, etc.) instantiate the modal
 * directly via [MeshlitLoaderModal] from the screen that owns
 * the long-running job. This demo exists so the design is
 * observable without writing fake business logic.
 */
@Composable
fun V2LoaderDemoScreen() {
    // The modal renders on first composition so designers can
    // see the chrome without an extra tap. `initiallyVisible`
    // skips the enter animation on the first frame.
    var visible by remember { mutableStateOf(true) }

    val ops = remember {
        listOf(
            LoaderOperation(
                id = "model-llama",
                kind = LoaderKind.Model,
                title = "Llama 3.2 3B Instruct · Q4_K_M",
                statusLine = "124.2 MB / 1.84 GB · 7.2 MB/s",
                progress = 0.067f,
            ),
            LoaderOperation(
                id = "net-scan",
                kind = LoaderKind.Network,
                title = "Scanning local network",
                statusLine = "12 peers found so far",
                indeterminate = true,
            ),
            LoaderOperation(
                id = "post-telemetry",
                kind = LoaderKind.Post,
                title = "Posting session · job-7f3a",
                progress = 0.78f,
                statusLine = "1.1 MB of 1.4 MB · 4.1 KB/s",
            ),
            LoaderOperation(
                id = "diag-cluster",
                kind = LoaderKind.Diagnostic,
                title = "Cluster self-test · 9 peers",
                progress = 0.42f,
                statusLine = "4 of 9 peers responded",
            ),
            LoaderOperation(
                id = "idle-tokenizer",
                kind = LoaderKind.Generic,
                title = "Warming tokenizer cache",
                indeterminate = true,
            ),
        )
    }

    MeshlitDeepLinkWrap(
        headline = "Loader preview",
        subtitle = "Modal for long-running operations",
    ) {
        // Wrap the demo content + modal in a Box so the modal
        // can `fillMaxSize` *over* the centered text, instead
        // of competing for the Column's remaining height.
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    color = androidx.compose.ui.graphics.Color.Transparent,
                    onClick = { visible = true },
                ) {
                    Text(
                        text = "Show loader modal",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
            MeshlitLoaderModal(
                visible = visible,
                operations = ops,
                onDismiss = { visible = false },
                onCancel = { /* demo — no-op */ },
            )
        }
    }
}