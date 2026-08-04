package com.meshlit.core.inference

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat as AndroidAudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.meshlit.core.common.logger
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.detectVoiceActivity
import com.runanywhere.sdk.public.extensions.streamVAD
import com.runanywhere.sdk.public.extensions.resetVAD
import com.runanywhere.sdk.public.extensions.transcribeStream
import com.runanywhere.sdk.public.extensions.synthesizeStream
import com.runanywhere.sdk.public.extensions.stopSynthesis
import ai.runanywhere.proto.v1.AudioFormat
import ai.runanywhere.proto.v1.STTLanguage
import ai.runanywhere.proto.v1.STTOptions
import ai.runanywhere.proto.v1.STTPartialResult
import ai.runanywhere.proto.v1.TTSOptions
import ai.runanywhere.proto.v1.TTSOutput
import ai.runanywhere.proto.v1.VADOptions
import ai.runanywhere.proto.v1.VADResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Phase 2.x — voice I/O surface backed by the RunAnywhere SDK's
 * STT/TTS/VAD trio. Used by the Voice screen to capture speech,
 * transcribe it, and synthesize a reply.
 *
 * Three flows:
 *
 *  - **[startCapture]** — emits a `Flow<VoiceFrame>` of PCM
 *    16 kHz mono 16-bit buffers from `AudioRecord`. The Voice
 *    screen forwards each frame to [transcribeStream] which
 *    surfaces partial transcripts via [StreamEvents].
 *
 *  - **[transcribe]** — wraps `RunAnywhere.transcribeStream(audioFlow,
 *    options)`. Each `STTPartialResult` event flips `is_final` when
 *    the SDK has confirmed the segment; the UI swaps the partial
 *    chip for a finalised one and re-enables the speak button.
 *
 *  - **[synthesize]** — wraps `RunAnywhere.synthesizeStream(text,
 *    options)`. Audio bytes are accumulated in an `AudioTrack`
 *    buffer and `play()` is called once the SDK signals
 *    `is_final = true`. A [stop] companion calls
 *    `RunAnywhere.stopSynthesis()` and flushes the track.
 *
 * Why this engine wraps the mic and audio device instead of leaving
 * them in the screen: `AudioRecord` and `AudioTrack` need to share a
 * lifecycle with the surrounding flow (start on subscribe, release
 * on cancel). Encoding that in the screen would leak the device
 * across recompositions; encoding it here keeps the resource scope
 * to the collector.
 *
 * Permission:
 *
 *  - The Voice screen checks `Manifest.permission.RECORD_AUDIO`
 *    before subscribing to [startCapture]. Without that permission
 *    `AudioRecord` throws `SecurityException` on construction.
 *    This engine doesn't request the permission itself — the host
 *    owns the user-visible prompt.
 */
class RunAnywhereVoiceEngine(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private val log = logger("RunAnywhereVoiceEngine")

    /** A single PCM frame emitted by [startCapture]. 16 kHz mono
     *  16-bit, ~32 ms per buffer (1024 frames at 16 kHz). The
     *  SDK's STT expects the same layout — keep both ends in
     *  sync if you change this. */
    data class VoiceFrame(
        val pcmBytes: ByteArray,
        val timestampMs: Long,
    ) {
        override fun equals(other: Any?): Boolean =
            this === other || (other is VoiceFrame && other.timestampMs == timestampMs)
        override fun hashCode(): Int = timestampMs.hashCode()
    }

    /** One partial or final transcript emitted by [transcribe]. */
    sealed interface TranscriptEvent {
        /** Incremental update from the STT model. `text` is the
         *  cumulative segment so far; `isFinal` is false. */
        data class Partial(val text: String, val stability: Float) : TranscriptEvent
        /** SDK has confirmed the segment. `finalText` may differ
         *  from the last partial due to punctuation/casing fixes. */
        data class Final(val finalText: String, val confidence: Float) : TranscriptEvent
        /** STT failed. The screen falls back to typed input. */
        data class Failed(val message: String) : TranscriptEvent
    }

    /** A VAD event — used to drive the visual activity meter on
     *  the Voice screen. */
    sealed interface VadEvent {
        data class Speech(
            val confidence: Float,
            val energyDb: Float,
            val timestampMs: Long,
        ) : VadEvent
        data class Silence(
            val durationMs: Int,
            val timestampMs: Long,
        ) : VadEvent
    }

    /** Whether the engine can start capture right now — the screen
     *  binds this to enable/disable the mic button. */
    fun hasMicPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Start microphone capture. Returns a cold flow that owns the
     * `AudioRecord` instance for its lifetime: opening on first
     * collect, releasing on cancel. The collector receives PCM
     * frames at the cadence the SDK expects.
     *
     * Throws [IllegalStateException] if the host hasn't granted
     * `RECORD_AUDIO` yet — the screen should check
     * [hasMicPermission] first.
     */
    fun startCapture(): Flow<VoiceFrame> = callbackFlow {
        if (!hasMicPermission(currentContextOrThrow())) {
            close(SecurityException("RECORD_AUDIO not granted"))
            return@callbackFlow
        }
        val sampleRate = 16_000
        val channelConfig = AndroidAudioFormat.CHANNEL_IN_MONO
        val audioFormat = AndroidAudioFormat.ENCODING_PCM_16BIT
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = maxOf(minBuffer, BYTES_PER_FRAME * FRAMES_PER_BUFFER)
        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize,
            )
        } catch (t: Throwable) {
            close(t)
            return@callbackFlow
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            close(IllegalStateException("AudioRecord failed to initialize"))
            return@callbackFlow
        }
        recorder.startRecording()
        log.info("runanywhere.voice.capture.started", "Mic capture started")
        val readBuffer = ByteArray(BYTES_PER_FRAME * FRAMES_PER_BUFFER)
        try {
            // Run the blocking read loop on a dedicated thread so
            // we don't tie up the dispatcher while the user is
            // speaking.
            val thread = Thread {
                try {
                    while (!Thread.currentThread().isInterrupted) {
                        val read = recorder.read(readBuffer, 0, readBuffer.size)
                        if (read > 0) {
                            val frame = VoiceFrame(
                                pcmBytes = readBuffer.copyOf(read),
                                timestampMs = System.currentTimeMillis(),
                            )
                            val result = trySend(frame)
                            if (result.isClosed) break
                        } else if (read < 0) {
                            // ERROR_INVALID_OPERATION / ERROR_BAD_VALUE
                            // etc — surface and stop.
                            close(IllegalStateException("AudioRecord.read returned $read"))
                            break
                        }
                    }
                } catch (t: Throwable) {
                    close(t)
                }
            }.apply {
                name = "RunAnywhereVoiceEngine.capture"
                isDaemon = true
                start()
            }
            awaitClose {
                thread.interrupt()
                recorder.stop()
                recorder.release()
                log.info("runanywhere.voice.capture.stopped", "Mic capture stopped")
            }
        } catch (t: Throwable) {
            recorder.release()
            close(t)
            awaitClose { /* nothing else to release */ }
        }
    }.flowOn(dispatcher)

    /**
     * Run STT on an audio flow. Returns a cold flow that emits
     * [TranscriptEvent]s as the model produces them. The audio
     * flow is collected on [dispatcher]; the resulting
     * transcript events flow on the same dispatcher.
     *
     * @param audio flow of PCM frames — typically [startCapture].
     * @param language spoken language hint — defaults to AUTO so
     *   the model detects from the first second of audio.
     */
    fun transcribe(
        audio: Flow<VoiceFrame>,
        language: STTLanguage = STTLanguage.STT_LANGUAGE_AUTO,
    ): Flow<TranscriptEvent> = flow {
        val options = STTOptions(
            language = language,
            enable_punctuation = true,
            enable_diarization = false,
            max_speakers = 1,
            enable_word_timestamps = false,
            beam_size = 5,
            language_code = "",
            detect_language = language == STTLanguage.STT_LANGUAGE_AUTO,
            audio_format = AudioFormat.AUDIO_FORMAT_PCM_S16LE,
            sample_rate = 16_000,
            max_alternatives = 1,
            chunk_duration_ms = 1024,
            endpoint_silence_ms = 700,
            suppress_blank = true,
            translate_to_english = false,
            vocabulary_list = emptyList(),
        )
        val audioByteFlow = audio.map { it.pcmBytes }
        try {
            val sdkFlow: Flow<STTPartialResult> = RunAnywhere.transcribeStream(
                audioByteFlow,
                options,
            )
            sdkFlow.collect { event ->
                val text = event.text.orEmpty()
                if (event.is_final) {
                    emit(TranscriptEvent.Final(text, event.confidence))
                } else if (text.isNotEmpty()) {
                    emit(TranscriptEvent.Partial(text, event.stability))
                }
            }
        } catch (t: Throwable) {
            log.warn(
                "runanywhere.voice.stt.failed",
                "STT failed",
                mapOf("error" to (t.message ?: t.javaClass.simpleName)),
            )
            emit(TranscriptEvent.Failed(t.message ?: t.javaClass.simpleName))
        }
    }.flowOn(dispatcher)

    /**
     * Detect voice activity on an audio flow. Used by the Voice
     * screen to drive an activity meter so the user knows the
     * mic is hearing them. The SDK's `streamVAD` returns a
     * `Flow<VADResult>` carrying per-frame confidence.
     */
    fun detectActivity(
        audio: Flow<VoiceFrame>,
        threshold: Float = 0.5f,
    ): Flow<VadEvent> = flow {
        val options = VADOptions(
            threshold = threshold,
            min_speech_duration_ms = 200,
            min_silence_duration_ms = 300,
            max_speech_duration_ms = 30_000,
            include_statistics = false,
        )
        try {
            val audioByteFlow = audio.map { it.pcmBytes }
            val sdkFlow: Flow<VADResult> = RunAnywhere.streamVAD(
                audioByteFlow,
                options,
            )
            sdkFlow.collect { result ->
                val ts = result.timestamp_ms
                if (result.is_speech) {
                    emit(VadEvent.Speech(result.confidence, result.energy, ts))
                } else {
                    emit(VadEvent.Silence(result.duration_ms, ts))
                }
            }
        } catch (t: Throwable) {
            log.warn(
                "runanywhere.voice.vad.failed",
                "VAD failed",
                mapOf("error" to (t.message ?: t.javaClass.simpleName)),
            )
            // Don't emit Failed — VAD is best-effort. STT will
            // still produce transcripts; the activity meter just
            // goes dark.
        }
    }.flowOn(dispatcher)

    /**
     * Run TTS on a text string. The SDK emits a stream of
     * `TTSOutput` events carrying PCM bytes. We accumulate the
     * bytes and write them to an `AudioTrack` for playback.
     * Returns when playback completes (or [stop] is called).
     *
     * The screen typically only needs to call this once and
     * await its completion; partial-event observation is rarely
     * useful for a "tap and listen" UI.
     */
    suspend fun synthesize(
        text: String,
        voice: String = "",
        speakingRate: Float = 1.0f,
    ): Boolean {
        val options = TTSOptions(
            voice = voice,
            language_code = "",
            speaking_rate = speakingRate,
            pitch = 1.0f,
            volume = 1.0f,
            enable_ssml = false,
            audio_format = AudioFormat.AUDIO_FORMAT_PCM_S16LE,
            sample_rate = 22_050,
            speaker_id = 0,
            style = "",
        )
        val sampleRate = options.sample_rate
        val channelMask = AndroidAudioFormat.CHANNEL_OUT_MONO
        val encoding = AndroidAudioFormat.ENCODING_PCM_16BIT
        val minBuffer = AudioTrack.getMinBufferSize(sampleRate, channelMask, encoding)
        val bufferSize = maxOf(minBuffer, 4096)
        val track = try {
            AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                channelMask,
                encoding,
                bufferSize,
                AudioTrack.MODE_STREAM,
            )
        } catch (t: Throwable) {
            log.warn(
                "runanywhere.voice.tts.track_failed",
                "AudioTrack init failed",
                mapOf("error" to (t.message ?: t.javaClass.simpleName)),
            )
            return false
        }
        return try {
            track.play()
            // Pin the playback loop to [dispatcher]; the SDK's
            // collect runs suspend, so we need a coroutine
            // context — this method is itself a suspend fun on
            // the caller's context, but pinning keeps the audio
            // callback off the main thread.
            val sdkFlow: Flow<TTSOutput> = RunAnywhere.synthesizeStream(text, options)
            var completed = false
            withContext(dispatcher) {
                sdkFlow.collect { event ->
                    val bytes = event.audio_data
                    if (bytes != null && bytes.size > 0) {
                        track.write(bytes.toByteArray(), 0, bytes.size)
                    }
                    if (event.is_final) {
                        completed = true
                        return@collect
                    }
                }
            }
            // Drain the track so the user hears the tail of the
            // last buffer.
            try { track.stop() } catch (_: Throwable) { /* ignore */ }
            completed
        } catch (t: Throwable) {
            log.warn(
                "runanywhere.voice.tts.failed",
                "TTS failed",
                mapOf("error" to (t.message ?: t.javaClass.simpleName)),
            )
            false
        } finally {
            try { track.release() } catch (_: Throwable) { /* ignore */ }
        }
    }

    /**
     * Halt an in-flight TTS playback. Safe to call from any
     * coroutine; flushes the local `AudioTrack` and asks the SDK
     * to stop synthesizing.
     */
    suspend fun stop() {
        try {
            RunAnywhere.stopSynthesis()
        } catch (t: Throwable) {
            log.warn(
                "runanywhere.voice.tts.stop_failed",
                "stopSynthesis failed",
                mapOf("error" to (t.message ?: t.javaClass.simpleName)),
            )
        }
    }

    /** Reset the VAD model between sessions. Called when the
     *  user toggles the mic off and back on so per-frame
     *  statistics don't carry across sessions. */
    suspend fun resetVad() {
        try {
            RunAnywhere.resetVAD()
        } catch (t: Throwable) {
            log.warn(
                "runanywhere.voice.vad.reset_failed",
                "VAD reset failed",
                mapOf("error" to (t.message ?: t.javaClass.simpleName)),
            )
        }
    }

    companion object {
        /** PCM sample rate the SDK expects on the audio flow. */
        const val SAMPLE_RATE_HZ = 16_000
        /** Mono 16-bit PCM: 2 bytes per sample, 1 channel. */
        const val BYTES_PER_FRAME = 2
        /** Frames per read — 1024 frames at 16 kHz = 64 ms per
         *  chunk. Tuned to the SDK's STT chunk size default. */
        const val FRAMES_PER_BUFFER = 1024

        private val INSTANCE = java.util.concurrent.atomic.AtomicReference<RunAnywhereVoiceEngine?>(null)

        fun install() {
            INSTANCE.compareAndSet(null, RunAnywhereVoiceEngine())
        }

        fun get(): RunAnywhereVoiceEngine =
            INSTANCE.get() ?: error(
                "RunAnywhereVoiceEngine not installed — call install() from MeshlitApplication.onCreate",
            )
    }
}

/**
 * Resolve the application context. We don't want to leak an
 * Activity into the engine — the engine's permission check uses
 * the application context so it survives configuration changes
 * (rotation, locale switch).
 */
private fun currentContextOrThrow(): Context {
    return ContextProvider.require()
}

/**
 * Lightweight hook so the engine can grab the application context
 * without us threading a `Context` through every callsite. Wired in
 * `MeshlitApplication.onCreate` before any UI binds to the engine.
 */
object ContextProvider {
    @Volatile
    private var appContext: Context? = null

    fun install(context: Context) {
        appContext = context.applicationContext
    }

    fun require(): Context =
        appContext ?: error(
            "ContextProvider not installed — call install() from MeshlitApplication.onCreate",
        )
}
