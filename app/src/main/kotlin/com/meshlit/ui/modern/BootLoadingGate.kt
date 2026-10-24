package com.meshlit.ui.modern

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlit.di.koinInject
import com.meshlit.core.inference.*
import com.meshlit.models.ModelLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.flowOf

/** Actual SDK/library startup states; no timed completion or fabricated percentage. */
@Composable fun BootLoadingGate(content: @Composable () -> Unit) {
    val library by produceState<ModelLibrary?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { org.koin.core.context.GlobalContext.get().get<ModelLibrary>() }
    }
    val coordinator = koinInject<InferenceCoordinator>()
    val startup by (library?.startupStatus ?: flowOf("Preparing model library")).collectAsStateWithLifecycle(initialValue = "Preparing model library")
    val state by coordinator.state.collectAsStateWithLifecycle()
    var bypass by rememberSaveable { mutableStateOf(false) }
    val pending = state is CoordinatorState.Starting || startup == "Preparing model library" || startup == "Waiting for model library" || startup.startsWith("Loading ")
    if (bypass || !pending) { content(); return }
    val motion=rememberInfiniteTransition(label="boot-motion")
    val pulse by motion.animateFloat(0.6f,1f,infiniteRepeatable(tween(1100),RepeatMode.Reverse),label="boot-pulse")
    val turn by motion.animateFloat(0f,360f,infiniteRepeatable(tween(6000)),label="boot-turn")
    Surface(Modifier.fillMaxSize().testTag("boot-loading"), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(82.dp).graphicsLayer(alpha=pulse), strokeWidth = 3.dp)
                Icon(Icons.Default.AutoAwesome, null, Modifier.size(32.dp).graphicsLayer(rotationZ=turn), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(28.dp))
            Text("Starting Meshlit", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))
            Text(if (state is CoordinatorState.Starting) "Initializing the local runtime" else startup,
                style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(28.dp))
            TextButton(onClick = { bypass = true }, modifier = Modifier.testTag("boot-open-app")) { Text("Open app while loading") }
            Text("Model readiness and errors remain visible in Models.", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
    }
}
