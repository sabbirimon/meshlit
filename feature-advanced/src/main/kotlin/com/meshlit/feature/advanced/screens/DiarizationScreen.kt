package com.meshlit.feature.advanced.screens

import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.meshlit.core.advanced.engines.DiarizationRequest
import com.meshlit.core.advanced.engines.EngineCategory
import com.meshlit.core.advanced.engines.EngineRegistry
import com.meshlit.core.advanced.engines.SortformerEngine
import com.meshlit.core.common.MeshlitResult
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun DiarizationScreen(
    accent: Color,
    accentDim: Color,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var speakers by remember { mutableStateOf<String?>(null) }
    val registry = remember { EngineRegistry() }
    LaunchedEffect(Unit) {
        val engine = SortformerEngine()
        engine.load(File("/tmp/sortformer.bin"))
        registry.register(engine)
    }
    SectionScreen(
        title = "Diarization",
        subtitle = "NVIDIA Sortformer — speaker labels.",
        accent = accent,
        accentDim = accentDim,
        onBack = onBack,
        badge = "Stub",
        content = {
            SectionCard(title = "Run", accent = accent) {
                Button(onClick = {
                    scope.launch {
                        val engine = registry.firstFor(EngineCategory.DIARIZATION) as? SortformerEngine ?: return@launch
                        val res = engine.run(DiarizationRequest(audioPath = "/tmp/meeting.wav"))
                        speakers = (res as? MeshlitResult.Success)?.value?.segments?.joinToString { "spk${it.speakerId}@${it.startMs}ms" }
                    }
                }) { Text("Diarize") }
                if (speakers != null) {
                    Text("Speakers: $speakers", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
    )
}
