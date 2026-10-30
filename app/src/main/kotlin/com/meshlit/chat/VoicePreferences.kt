package com.meshlit.chat

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement

/** Device-local choices, never provider credentials or recorded microphone audio. */
object VoicePreferences {
    private const val FILE="voice-preferences"
    private const val KEY="options"
    private val codec=Json{ignoreUnknownKeys=true}
    data class Read(val options:VoiceOptions,val problem:String?=null)
    fun read(context:Context,packs:List<SpeechPack>)=decode(context.getSharedPreferences(FILE,Context.MODE_PRIVATE).getString(KEY,null),defaults(packs))
    internal fun decode(saved:String?,fallback:VoiceOptions):Read {
        if(saved==null) return Read(fallback)
        return try {
            require(saved.toByteArray().size<=64*1024)
            Read(codec.decodeFromString<VoiceOptions>(saved))
        }catch(_:Exception){Read(fallback,"Saved voice settings could not be read. They have been kept unchanged. Reset them explicitly to continue; a local backup will be retained.")}
    }
    private fun defaults(packs:List<SpeechPack>):VoiceOptions {
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
    @Synchronized fun save(context:Context,options:VoiceOptions) {
        val prefs=context.getSharedPreferences(FILE,Context.MODE_PRIVATE)
        val encoded=encodePreservingUnknown(prefs.getString(KEY,null),options)
        check(prefs.edit().putString(KEY,encoded).commit()) {"Voice settings could not be saved. Check available storage and retry."}
    }
    internal fun encodePreservingUnknown(previous:String?,options:VoiceOptions):String {
        check(decode(previous,options).problem==null){"Unreadable voice settings are preserved; reset them explicitly before saving."}
        fun merge(old:JsonElement?,next:JsonElement,depth:Int):JsonElement {
            require(depth<=16){"Voice settings nesting exceeds the supported limit"}
            if(old !is JsonObject || next !is JsonObject) return next
            return JsonObject(old+next.mapValues{(key,value)->merge(old[key],value,depth+1)})
        }
        val encoded=merge(previous?.let{codec.parseToJsonElement(it)},codec.parseToJsonElement(codec.encodeToString(options)),0).toString()
        require(encoded.toByteArray().size<=64*1024){"Voice settings exceed the supported size"}
        return encoded
    }
    @Synchronized fun resetUnreadable(context:Context,packs:List<SpeechPack>):VoiceOptions {
        val prefs=context.getSharedPreferences(FILE,Context.MODE_PRIVATE)
        val previous=prefs.getString(KEY,null)
        val next=defaults(packs)
        val edit=prefs.edit().putString(KEY,codec.encodeToString(next))
        if(previous!=null) edit.putString("unreadable-options-backup",previous)
        check(edit.commit()) {"Could not reset voice settings. The prior settings remain on disk."}
        return next
    }
}
