package com.meshlit.feature.advanced

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.meshlit.feature.advanced.components.HubCard

/**
 * "Speech lab" cards. TTS, STT, VAD, diarization in one place.
 */
@Composable
fun SpeechLabSection(
    accent: Color,
    onNavigate: (AdvancedDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HubCard(
            icon = AdvancedDestination.Diarization.icon,
            title = AdvancedDestination.Diarization.label,
            subtitle = AdvancedDestination.Diarization.description,
            accent = accent,
            onClick = { onNavigate(AdvancedDestination.Diarization) },
            badge = "Stub",
        )
        HubCard(
            icon = AdvancedDestination.ReadAloud.icon,
            title = AdvancedDestination.ReadAloud.label,
            subtitle = AdvancedDestination.ReadAloud.description,
            accent = accent,
            onClick = { onNavigate(AdvancedDestination.ReadAloud) },
        )
        HubCard(
            icon = AdvancedDestination.Transcription.icon,
            title = AdvancedDestination.Transcription.label,
            subtitle = AdvancedDestination.Transcription.description,
            accent = accent,
            onClick = { onNavigate(AdvancedDestination.Transcription) },
        )
        HubCard(
            icon = AdvancedDestination.VoiceActivity.icon,
            title = AdvancedDestination.VoiceActivity.label,
            subtitle = AdvancedDestination.VoiceActivity.description,
            accent = accent,
            onClick = { onNavigate(AdvancedDestination.VoiceActivity) },
        )
    }
}