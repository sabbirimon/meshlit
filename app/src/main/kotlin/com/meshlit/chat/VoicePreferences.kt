package com.meshlit.chat

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Device-local choices, never provider credentials or recorded microphone audio. */
object VoicePreferences {
    private const val FILE="voice-preferences"
    private const val KEY="options"
    fun load(context:Context,packs:List<SpeechPack>):VoiceOptions {
        val saved=context.getSharedPreferences(FILE,Context.MODE_PRIVATE).getString(KEY,null)
        if(saved!=null) runCatching { Json.decodeFromString<VoiceOptions>(saved) }.getOrNull()?.let { return it }
        val offlineRecognition=packs.firstOrNull { it.kind==SpeechKind.WHISPER_STT }
        val offlineVoice=packs.firstOrNull { it.kind==SpeechKind.PIPER_TTS }
        return VoiceOptions(
            input=if(offlineRecognition!=null) SpeechInput.OFFLINE_MODEL else SpeechInput.ANDROID,
            sttPack=offlineRecognition?.id.orEmpty(),
            output=if(offlineVoice!=null) SpeechOutput.OFFLINE_MODEL else SpeechOutput.ANDROID,
            ttsPack=offlineVoice?.id.orEmpty(),
            network=false,
        )
    }
    fun save(context:Context,options:VoiceOptions) {
        context.getSharedPreferences(FILE,Context.MODE_PRIVATE).edit()
            .putString(KEY,Json.encodeToString(options)).apply()
    }
}
