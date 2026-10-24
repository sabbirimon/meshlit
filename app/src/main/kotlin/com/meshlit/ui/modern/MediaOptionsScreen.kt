package com.meshlit.ui.modern
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
@Composable fun MediaOptionsScreen(onBack:()->Unit) {
    var page by remember{mutableStateOf<String?>(null)}
    BackHandler(page!=null){page=null}
    if(page=="studio"){MediaGenerationScreen{page=null};return}
    if(page!=null){Column(Modifier.fillMaxSize().safeDrawingPadding()){TextButton(onClick={page=null}){Text("Back to media")};Box(Modifier.weight(1f)){if(page=="vision") com.meshlit.ui.screens.VisionScreen({page=null},omitHeader=true) else com.meshlit.ui.screens.VoiceScreen({page=null},omitHeader=true)}};return}
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        TextButton(onClick=onBack){Text("Back to settings")};Text("Camera, vision and audio",style=MaterialTheme.typography.headlineSmall)
        Text("Use actual phone-camera thumbnail capture or selected images, and the existing microphone/WAV/STT/TTS controls. A successful capture is separate from model readiness; missing SDK media backends and models must report unavailable.")
        Button(onClick={page="studio"}){Text("Online vision and media generation")}
        Button(onClick={page="vision"}){Text("Camera / image input and vision")}
        Button(onClick={page="voice"}){Text("Microphone, audio files and speech")}
        Text("Authorized CCTV/RTSP, web cameras, USB UVC and remote microphone feeds need source adapters with bounded frame/audio buffers. They are planned in docs/media-iot-and-radio-nodes.md; no live stream is started by this page.")
    }
}
