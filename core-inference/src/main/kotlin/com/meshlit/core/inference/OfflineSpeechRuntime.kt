package com.meshlit.core.inference

import com.runanywhere.sdk.foundation.bridge.extensions.CppBridgeModelRegistry
import com.runanywhere.sdk.core.onnx.ONNX
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.*
import ai.runanywhere.proto.v1.*
import kotlinx.coroutines.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/** Normalized signed PCM16, not the SDK's generic float PCM. */
data class OfflineSpeechAudio(val bytes:ByteArray,val sampleRate:Int)
/** Separately categorized ONNX models. No downloads, network fallback or text-model mutation. */
class OfflineSpeechRuntime {
    private val loaded=mutableMapOf<Boolean,String>()
    suspend fun load(id:String,name:String,path:File,recognition:Boolean)=withContext(Dispatchers.IO) {
        require(id.matches(Regex("[a-z0-9-]{1,80}")) && path.isDirectory)
        // The text engine registers llama.cpp only. Register the separately
        // packaged ONNX/Sherpa primitives without touching its loaded model.
        ONNX.register()
        val category=category(recognition)
        CppBridgeModelRegistry.save(ModelInfo(id="meshlit-speech-$id",name=name,category=category,
            format=ModelFormat.MODEL_FORMAT_FOLDER,framework=InferenceFramework.INFERENCE_FRAMEWORK_ONNX,
            local_path=path.absolutePath,is_downloaded=true,is_available=true))
        val result=RunAnywhere.loadModel(ModelLoadRequest(model_id="meshlit-speech-$id",category=category,framework=InferenceFramework.INFERENCE_FRAMEWORK_ONNX))
        check(result.success) { "Offline speech load failed: ${result.error_message ?: "unsupported model/runtime"}" }
        loaded[recognition]=id
    }
    suspend fun transcribe(pcm:ByteArray):String=withContext(Dispatchers.IO) {
        require(loaded.containsKey(true));require(pcm.size in 3200..960000 && pcm.size%2==0)
        val result=RunAnywhere.transcribe(pcm,STTOptions(sample_rate=16000,audio_format=AudioFormat.AUDIO_FORMAT_PCM_S16LE,language_code="en",detect_language=false))
        check(result.error_message.isNullOrBlank()) { "Offline recognition failed" }
        result.text.trim().takeIf{it.isNotEmpty() && it.length<=12000} ?: error("No usable speech recognized")
    }
    suspend fun synthesize(text:String,pitch:Float=1f,speed:Float=1f):OfflineSpeechAudio=withContext(Dispatchers.IO) {
        require(loaded.containsKey(false) && text.isNotBlank() && text.length<=4000 && pitch in 0.5f..2f && speed in 0.5f..2f)
        // Pinned SDK 0.20.12 returns float32 PCM even when WAV/S16LE is
        // requested. Ask for its real primitive, then normalize explicitly.
        val result=RunAnywhere.synthesize(text,TTSOptions(audio_format=AudioFormat.AUDIO_FORMAT_PCM,sample_rate=22050,speaking_rate=speed,pitch=pitch))
        check(result.error_message.isNullOrBlank()) { "Offline synthesis failed" }
        val bytes=result.audio_data?.toByteArray() ?: error("Offline synthesis returned no audio")
        require(result.sample_rate in 8000..48000 && bytes.size in 2..16*1024*1024) {
            "Unsupported offline synthesis output: ${result.audio_format}, ${result.sample_rate} Hz, ${bytes.size} bytes"
        }
        val pcm=when(result.audio_format) {
            AudioFormat.AUDIO_FORMAT_PCM->speechFloatPcm16(bytes,result.sample_rate)
            AudioFormat.AUDIO_FORMAT_PCM_S16LE->bytes.also{require(it.size%2==0 && it.size.toLong()<=result.sample_rate*2L*180)}
            else->error("Unsupported offline synthesis format: ${result.audio_format}")
        }
        OfflineSpeechAudio(pcm,result.sample_rate)
    }
    suspend fun close() {
        withContext(NonCancellable+Dispatchers.IO){
            var failed=false
            loaded.toMap().forEach{(recognition,id)->
                val result=runCatching{RunAnywhere.unloadModel(ModelUnloadRequest(model_id="meshlit-speech-$id",category=category(recognition)))}
                if(result.getOrNull()?.success!=true) failed=true
            }
            loaded.clear();check(!failed) { "Offline speech model cleanup failed; inspect runtime diagnostics" }
        }
    }
    private fun category(recognition:Boolean)=if(recognition) ModelCategory.MODEL_CATEGORY_SPEECH_RECOGNITION else ModelCategory.MODEL_CATEGORY_SPEECH_SYNTHESIS
}

/** Sherpa's float32 mono samples are little-endian. Bound duration/allocation,
 * reject non-finite samples and saturate real audio peaks before signed output. */
internal fun speechFloatPcm16(bytes:ByteArray,rate:Int):ByteArray {
    require(rate in 8000..48000 && bytes.size in 4..16*1024*1024 && bytes.size%4==0 && bytes.size.toLong()<=rate*4L*180)
    val input=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val output=ByteBuffer.allocate(bytes.size/2).order(ByteOrder.LITTLE_ENDIAN)
    while(input.hasRemaining()) {
        val value=input.float;require(value.isFinite()) { "Offline synthesis returned non-finite audio" }
        output.putShort((value.coerceIn(-1f,1f)*32768f).roundToInt().coerceIn(-32768,32767).toShort())
    }
    return output.array()
}
