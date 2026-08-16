package com.meshlit.feature.advanced.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.meshlit.core.advanced.engines.KokoroEngine
import com.meshlit.core.advanced.engines.KokoroRequest
import com.meshlit.core.advanced.engines.EngineRegistry
import com.meshlit.core.common.MeshlitResult
import kotlinx.coroutines.launch
import java.io.File

/**
 * Read-aloud (TTS) destination. Type text, hit Generate, the
 * KokoroEngine stub returns a placeholder. The registry owns the
 * engine singleton so first-use spins it up; subsequent uses reuse
 * the same instance.
 */
@Composable
fun ReadAloudScreen(
    accent: Color,
    accentDim: Color,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("Hello from Meshlit") }
    var lastOutput by remember { mutableStateOf<String?>(null) }
    val registry = remember { EngineRegistry() }
    LaunchedEffect(Unit) {
        val engine = KokoroEngine()
        engine.load(File("/tmp/kokoro.bin"))
        registry.register(engine)
    }
    SectionScreen(
        title = "Read aloud",
        subtitle = "Kokoro on-device TTS.",
        accent = accent,
        accentDim = accentDim,
        onBack = onBack,
        content = {
            SectionCard(title = "Text", accent = accent) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        scope.launch {
                            val engine = registry.firstFor(com.meshlit.core.advanced.engines.EngineCategory.TTS) as? KokoroEngine
                                ?: return@launch
                            val result = engine.run(KokoroRequest(text))
                            lastOutput = (result as? MeshlitResult.Success)?.value?.audioPath
                        }
                    },
                ) { Text("Generate") }
                if (lastOutput != null) {
                    Text("Output: $lastOutput", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
    )
}
