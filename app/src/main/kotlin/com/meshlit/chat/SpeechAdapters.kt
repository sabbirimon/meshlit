package com.meshlit.chat

import android.content.Context
import android.media.*
import com.meshlit.core.common.control.ManagedFeature
import com.meshlit.operations.OperationsControl
import com.meshlit.providers.OnlineProviders
import com.meshlit.core.inference.models.*
import com.meshlit.core.inference.OfflineSpeechRuntime
import kotlinx.coroutines.*
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Speech is a separate modality: never loads, replaces or generates through the text LLM. */
internal class OfflineSpeechAdapter(private val models:VoiceModels) {
    private val runtime=OfflineSpeechRuntime()
    suspend fun load(id:String,kind:SpeechKind) {
        val (pack,path)=models.verified(id);require(pack.kind==kind)
        runtime.load(pack.id,pack.name,path,kind==SpeechKind.WHISPER_STT)
    }
    suspend fun transcribe(pcm:ByteArray)=runtime.transcribe(pcm)
    suspend fun synthesize(text:String,style:VoiceStyle):PcmAudio {
        val audio=runtime.synthesize(text,style.pitch,style.speed)
        return PcmAudio(audio.bytes,audio.sampleRate).also{it.validate()}
    }
    suspend fun close()=runtime.close()
}
internal data class PcmAudio(val bytes:ByteArray,val rate:Int) {
    fun validate(){require(rate in 8000..48000 && bytes.size in 2..16*1024*1024 && bytes.size%2==0);require(bytes.size.toLong()<=rate*2L*180) { "Voice reply exceeds 3 minutes" }}
}
internal fun voiceWav(pcm:ByteArray):ByteArray {
    require(pcm.size in 2..960000 && pcm.size%2==0)
    val out=ByteBuffer.allocate(44+pcm.size).order(ByteOrder.LITTLE_ENDIAN)
    out.put("RIFF".toByteArray());out.putInt(36+pcm.size);out.put("WAVEfmt ".toByteArray());out.putInt(16);out.putShort(1);out.putShort(1)
    out.putInt(16000);out.putInt(32000);out.putShort(2);out.putShort(16);out.put("data".toByteArray());out.putInt(pcm.size);out.put(pcm);return out.array()
}
/** Strict PCM WAV decoder: compressed/float/stereo/oversized output is not silently played. */
internal fun readVoiceWav(bytes:ByteArray):PcmAudio {
    require(bytes.size in 44..16*1024*1024)
    val b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    fun label(at:Int)=String(bytes,at,4,Charsets.US_ASCII)
    require(label(0)=="RIFF" && label(8)=="WAVE" && b.getInt(4).toLong()+8==bytes.size.toLong())
    var p=12;var rate:Int?=null;var pcm:ByteArray?=null
    while(p+8<=bytes.size){val size=b.getInt(p+4);require(size>=0 && size<=bytes.size-p-8)
        when(label(p)){
            "fmt "->{require(rate==null && size>=16 && b.getShort(p+8).toInt()==1 && b.getShort(p+10).toInt()==1 && b.getShort(p+22).toInt()==16);rate=b.getInt(p+12);require(b.getInt(p+16)==rate*2 && b.getShort(p+20).toInt()==2)}
            "data"->{require(pcm==null);pcm=bytes.copyOfRange(p+8,p+8+size)}
        };p+=8+size+(size%2)
    }
    require(p==bytes.size);return PcmAudio(checkNotNull(pcm),checkNotNull(rate)).also{it.validate()}
}
/** Bounded microphone turn with a local energy/silence end detector. No audio is saved. */
@android.annotation.SuppressLint("MissingPermission")
internal suspend fun captureVoiceTurn(onLevel:(Double,Boolean)->Unit = { _,_ -> }):ByteArray=withContext(Dispatchers.IO) {
    val min=AudioRecord.getMinBufferSize(16000,android.media.AudioFormat.CHANNEL_IN_MONO,android.media.AudioFormat.ENCODING_PCM_16BIT)
    require(min>0)
    val recorder=AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,16000,android.media.AudioFormat.CHANNEL_IN_MONO,android.media.AudioFormat.ENCODING_PCM_16BIT,maxOf(min,8192))
    try {
        check(recorder.state==AudioRecord.STATE_INITIALIZED);recorder.startRecording();check(recorder.recordingState==AudioRecord.RECORDSTATE_RECORDING){"Microphone could not start"}
        val result=ByteArrayOutputStream();val buffer=ByteArray(2048);val detector=VoiceEndpointDetector()
        withTimeoutOrNull(30_000){while(!detector.complete){currentCoroutineContext().ensureActive()
            val n=recorder.read(buffer,0,minOf(buffer.size,detector.remaining),AudioRecord.READ_NON_BLOCKING);check(n>=0) { "Microphone read failed" }
            if(n==0){delay(20);continue};require(n%2==0);result.write(buffer,0,n)
            val level=voiceEnergy(buffer,n)
            detector.accept(n,level)
            onLevel(level,detector.hasSpeech)
        };true}
        currentCoroutineContext().ensureActive()
        if(!detector.hasSpeech) throw NoVoiceDetected()
        result.toByteArray()
    }finally{runCatching{recorder.stop()};recorder.release()}
}
internal fun voiceEnergy(bytes:ByteArray,count:Int):Double {
    require(count in 2..bytes.size && count%2==0);var energy=0.0
    for(i in 0 until count step 2){val sample=((bytes[i].toInt() and 255) or (bytes[i+1].toInt() shl 8)).toShort().toDouble()/32768;energy+=sample*sample}
    return kotlin.math.sqrt(energy/(count/2))
}
internal suspend fun playVoice(audio:PcmAudio,onLevel:(Double)->Unit = {})=withContext(Dispatchers.IO) {
    audio.validate()
    val min=AudioTrack.getMinBufferSize(audio.rate,android.media.AudioFormat.CHANNEL_OUT_MONO,android.media.AudioFormat.ENCODING_PCM_16BIT);require(min>0)
    val track=AudioTrack(AudioManager.STREAM_MUSIC,audio.rate,android.media.AudioFormat.CHANNEL_OUT_MONO,android.media.AudioFormat.ENCODING_PCM_16BIT,maxOf(min,8192),AudioTrack.MODE_STREAM)
    try {
        check(track.state==AudioTrack.STATE_INITIALIZED);track.play();var offset=0
        val meter=launch {
            while(isActive) {
                val position=(track.playbackHeadPosition.toLong()*2).coerceIn(0,audio.bytes.size.toLong()).toInt()
                val end=minOf(position+1024,audio.bytes.size)
                if(end-position>=2) onLevel(voiceEnergy(audio.bytes.copyOfRange(position,end),end-position))
                delay(50)
            }
        }
        try { withTimeout(185_000){while(offset<audio.bytes.size){currentCoroutineContext().ensureActive()
            val n=track.write(audio.bytes,offset,minOf(8192,audio.bytes.size-offset),AudioTrack.WRITE_NON_BLOCKING);check(n>=0) { "Audio playback write failed" }
            if(n==0) delay(10) else offset+=n
        }
        while(track.playbackHeadPosition.toLong()<audio.bytes.size/2){currentCoroutineContext().ensureActive();delay(20)}}
        }finally{withContext(NonCancellable){meter.cancelAndJoin()}}
    }finally{onLevel(0.0);runCatching{track.pause();track.flush()};track.release()}
}
internal class ProviderSpeechAdapter(context:Context,private val providers:OnlineProviders) {
    private val gate=OperationsControl.get(context).gate
    private val client=OnlineMediaClient()
    private val cache=File(context.cacheDir,"voice-turns").apply{mkdirs();listFiles().orEmpty().filter{System.currentTimeMillis()-it.lastModified()>86_400_000}.forEach{it.delete()}}
    private fun profile(id:String)=providers.profiles.value.singleOrNull{it.id==id && it.enabled && it.protocol in setOf(OnlineProtocol.OPENAI,OnlineProtocol.OPENAI_COMPATIBLE)} ?: error("Enable an OpenAI-format speech provider first")
    suspend fun preflight(id:String,sttModel:String?,ttsModel:String?,voice:String) {
        val p=profile(id);p.validate()
        listOfNotNull(sttModel,ttsModel).forEach{require(it.matches(Regex("[A-Za-z0-9_./:-]{1,160}"))) { "Enter supported speech model IDs" }}
        if(ttsModel!=null) require(voice.matches(Regex("[A-Za-z0-9_-]{1,80}"))) { "Enter a supported provider voice ID" }
        val key=providers.resolveToken(p)
        require(!p.requiresApiKey || key.isNotBlank()) { "Save the speech provider credential before starting" }
    }
    private suspend fun <T> request(id:String,work:suspend(OnlineProfile,String)->T)=gate.run(ManagedFeature.MEDIA){gate.run(ManagedFeature.CLOUD){val p=profile(id);work(p,providers.resolveToken(p))}}
    suspend fun transcribe(id:String,model:String,pcm:ByteArray)=request(id){p,key->client.transcribe(p,key,model,voiceWav(pcm))}
    suspend fun synthesize(id:String,model:String,voice:String,text:String)=request(id){p,key->
        val file=File(cache,"${java.util.UUID.randomUUID()}.wav")
        try{client.speech(p,key,model,text,voice,file);withContext(Dispatchers.IO){require(file.length()<=16*1024*1024);readVoiceWav(file.readBytes())}}
        finally{file.delete();File(file.path+".part").delete()}
    }
}
