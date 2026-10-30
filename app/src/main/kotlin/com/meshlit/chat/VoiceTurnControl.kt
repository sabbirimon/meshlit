package com.meshlit.chat

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable

@Serializable enum class VoiceMode(val label:String) { LIVE("Live conversation"), SINGLE_TURN("One turn") }
internal class VoiceTurnInterrupted:CancellationException("Voice turn interrupted")
internal class NoVoiceDetected(message:String="No speech heard. Check microphone access and speak near the phone."):IllegalStateException(message)

/** Saved local PCM capture and Android playback tuning. Values are validated on import too. */
@Serializable
data class VoiceTuning(
    val threshold:Double=0.008,
    val silenceMs:Int=700,
    val speechRate:Float=1f,
    val pitch:Float=1f,
    val automaticThreshold:Boolean=false,
) {
    init {
        require(threshold.isFinite() && threshold in 0.001..0.1)
        require(silenceMs in 300..2000)
        require(speechRate.isFinite() && speechRate in 0.5f..2f)
        require(pitch.isFinite() && pitch in 0.5f..2f)
    }
}

/** PCM16 mono at 16 kHz: accept 100 ms of speech; always cap capture at 30 seconds. */
internal class VoiceEndpointDetector(private val tuning:VoiceTuning=VoiceTuning()) {
    var bytes:Int=0; private set
    private var speechBytes=0
    private var silenceBytes=0
    private var audibleBytes=0
    private var quietestLevel=Double.POSITIVE_INFINITY
    var peakLevel:Double=0.0; private set
    /** Optional calibration can only lower the chosen threshold, with a fixed noise floor. */
    val threshold get()=if(tuning.automaticThreshold && bytes>=16000 && quietestLevel.isFinite())
        minOf(tuning.threshold,maxOf(0.001,quietestLevel*3)) else tuning.threshold
    val hasSpeech get()=speechBytes>=3200
    val canFinishManually get()=bytes>=16000 && audibleBytes>=3200
    val complete get()=(hasSpeech && silenceBytes>=tuning.silenceMs*32) || (!hasSpeech && bytes>=384000) || bytes>=960000
    val remaining get()=960000-bytes
    fun accept(count:Int,level:Double) {
        require(count in 2..remaining && count%2==0 && level.isFinite() && level>=0)
        bytes+=count
        peakLevel=maxOf(peakLevel,level)
        quietestLevel=minOf(quietestLevel,level)
        if(level>0.0005) audibleBytes+=count
        if(level>threshold){speechBytes+=count;silenceBytes=0}else silenceBytes+=count
    }
}

/** dBFS displays quiet real input clearly; it is not a speech-confidence score. */
internal fun voiceMeterLevel(rms:Double):Float {
    require(rms.isFinite() && rms>=0)
    if(rms==0.0) return 0f
    return ((20*kotlin.math.log10(rms)+72)/60).toFloat().coerceIn(0f,1f)
}
internal fun voiceNoSpeechMessage(peak:Double)=if(peak<=0.0005)
    "The microphone delivered silence or very low audio. Check Android microphone access, disconnect other recording apps or a headset, then tap Retry microphone."
else "Audio reached the microphone, but did not pass the speech threshold. Tap Retry microphone and speak closer, use Send voice when finished, or lower the threshold in Voice settings."

/** A failed native unload leaves residency uncertain until the app process restarts. */
internal class VoiceCleanupGuard {
    private var failed=false
    fun requireReady(){check(!failed){"Native speech cleanup failed. Restart Meshlit before starting another voice session."}}
    suspend fun cleanup(action:suspend()->Unit) {
        try{action()}catch(e:Exception){failed=true;throw e}
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
