package com.meshlit.chat

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable

@Serializable enum class VoiceMode(val label:String) { LIVE("Live conversation"), SINGLE_TURN("One turn") }
internal class VoiceTurnInterrupted:CancellationException("Voice turn interrupted")
internal class NoVoiceDetected:IllegalStateException("No speech heard. Check microphone access and speak near the phone.")

/** PCM16 mono at 16 kHz: accept 100 ms of speech and end after 700 ms silence. */
internal class VoiceEndpointDetector {
    var bytes:Int=0; private set
    private var speechBytes=0
    private var silenceBytes=0
    val hasSpeech get()=speechBytes>=3200
    val complete get()=(hasSpeech && silenceBytes>=22400) || bytes>=960000
    val remaining get()=960000-bytes
    fun accept(count:Int,level:Double) {
        require(count in 2..remaining && count%2==0 && level.isFinite() && level>=0)
        bytes+=count
        if(level>0.008){speechBytes+=count;silenceBytes=0}else silenceBytes+=count
    }
}

/** Conservative admission estimate, not a benchmark or a promise of runtime residency. */
internal fun requireSpeechMemory(packBytes:Long,availableBytes:Long,lowMemory:Boolean) {
    require(packBytes in 1..1_073_741_824L && availableBytes>=0)
    val required=packBytes*2+224L*1024*1024
    check(!lowMemory && availableBytes>=required){"Not enough free memory for these speech models alongside chat. Close other apps, select a smaller chat model, or use an installed Android voice."}
}

/** Main-thread session control. A replacement waits for the canceled turn's cleanup. */
internal class VoiceTurnControl {
    val muted=MutableStateFlow(false)
    private var current:Job?=null
    suspend fun awaitUnmuted(){muted.first{!it}}
    suspend fun <T> run(block:suspend()->T):T=coroutineScope {
        check(current==null){"A voice turn is still releasing audio"}
        val turn=async(start=CoroutineStart.LAZY){block()}
        current=turn
        try {turn.await()} finally {if(current===turn) current=null}
    }
    fun interrupt(){current?.cancel(VoiceTurnInterrupted())}
    fun setMuted(value:Boolean,interruptCapture:Boolean=false) {
        muted.value=value
        if(value && interruptCapture) interrupt()
    }
}

/** Only the playback copy changes; the full answer remains in chat history. */
internal fun spokenReply(text:String):String = text.take(12000)
    .replace(Regex("```[\\s\\S]*?```")," Code is available in the chat.")
    .replace(Regex("\\[([^\\]]+)\\]\\(https?://[^)]+\\)"),"$1")
    .replace(Regex("(?m)^\\s{0,3}(?:#{1,6}|[-*])\\s+"),"")
    .replace("**","").replace("`","").trim().take(4000)

/** Phrase captions and bounded synthesis avoid waiting for an entire paragraph of audio. */
internal fun speechSegments(text:String):List<String> {
    val result=mutableListOf<String>()
    var rest=text.trim().take(4000)
    while(rest.isNotEmpty()) {
        val window=rest.take(240)
        val sentence=Regex("[.!?](?:\\s|$)").find(window)?.range?.last?.plus(1)
        val cut=sentence ?: if(rest.length<=240) rest.length else window.lastIndexOf(' ').takeIf{it>0} ?: 240
        val part=rest.take(cut).trim()
        if(part.isNotEmpty()) result+=part
        rest=rest.drop(cut).trimStart()
    }
    return result
}
