package com.meshlit.chat

import android.content.Context
import android.content.Intent
import android.os.*
import android.speech.*
import android.speech.tts.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.meshlit.core.common.control.ManagedFeature
import com.meshlit.operations.OperationsControl
import com.meshlit.providers.OnlineProviders
import java.util.Locale
import kotlinx.serialization.Serializable

@Serializable
enum class VoiceStyle(val label:String,val pitch:Float,val speed:Float) {
    NATURAL("Natural",1f,1f),DEEP("Lower voice",0.8f,0.95f),BRIGHT("Higher voice",1.2f,1f),ROBOTIC("Robotic style",0.85f,0.8f),YOUTHFUL("Youthful style",1.35f,1.05f)
}
@Serializable enum class SpeechInput(val label:String) { OFFLINE_MODEL("Offline speech model"),ANDROID("Android recognizer"),PROVIDER("Online speech model") }
@Serializable enum class SpeechOutput(val label:String) { ANDROID("Installed Android voice"),OFFLINE_MODEL("Offline voice model"),PROVIDER("Online voice model") }
@Serializable
data class VoiceOptions(val input:SpeechInput=SpeechInput.OFFLINE_MODEL,val output:SpeechOutput=SpeechOutput.ANDROID,
    val sttPack:String="",val ttsPack:String="",val providerId:String="",val sttModel:String="",val ttsModel:String="",val providerVoice:String="coral",val mode:VoiceMode=VoiceMode.LIVE,val captions:Boolean=true,val network:Boolean=false,val androidVoice:String?=null,val style:VoiceStyle=VoiceStyle.NATURAL)
data class VoiceState(val phase:String="Stopped",val active:Boolean=false,val error:String?=null,val ready:Boolean=false,val voices:List<Voice> = emptyList(),val busy:Boolean=false,val testTranscript:String?=null,val inputLevel:Float=0f,val speechDetected:Boolean=false,val muted:Boolean=false,val elapsedSeconds:Int=0,val completedTurns:Int=0,val lastHeard:String?=null,val lastReply:String?=null,val outputLevel:Float?=null,val caption:String?=null)
internal fun androidRecognitionFailure(code:Int):Exception = when(code) {
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT,SpeechRecognizer.ERROR_NO_MATCH -> NoVoiceDetected()
    else -> IllegalStateException("Android recognition failed (code $code)")
}
/** Foreground speech adapters feed the current chat controller. Model weights, routes,
 * remembered preferences and chat tool grants remain owned by that controller. */
class HandsFreeVoice(private val context:Context,private val chat:ChatController,private val models:VoiceModels,private val providers:OnlineProviders) {
    private val main=Handler(Looper.getMainLooper())
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val gate=OperationsControl.get(context).gate
    private val offline=OfflineSpeechAdapter(models)
    private val online=ProviderSpeechAdapter(context,providers)
    private val _state=MutableStateFlow(VoiceState());val state=_state.asStateFlow()
    private var recognizer:SpeechRecognizer?=null
    private var tts:TextToSpeech?=null
    private var options=VoiceOptions()
    private var epoch=0
    private var ownedGeneration:String?=null
    private var session:Job?=null
    private var recognized:CompletableDeferred<String>?=null
    private var spoken:CompletableDeferred<Unit>?=null
    private var utterance:String?=null
    private var capturing=false
    private val turns=VoiceTurnControl()
    private val audioManager=context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    private var focusRequest:android.media.AudioFocusRequest?=null
    private val focusListener=android.media.AudioManager.OnAudioFocusChangeListener { change ->
        if(change<0) main.post { if(state.value.busy) {stop();_state.update{it.copy(error="Voice paused because another app needs audio")}} }
    }
    private fun acquireAudioFocus() {
        val result=if(Build.VERSION.SDK_INT>=26) {
            val request=android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setOnAudioFocusChangeListener(focusListener,main).build()
            focusRequest=request;audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(focusListener,android.media.AudioManager.STREAM_MUSIC,android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
        check(result==android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED){"Another app is using audio. Pause it and try again."}
    }
    private fun releaseAudioFocus() {
        if(Build.VERSION.SDK_INT>=26) focusRequest?.let{audioManager.abandonAudioFocusRequest(it)}
        else { @Suppress("DEPRECATION") audioManager.abandonAudioFocus(focusListener) }
        focusRequest=null
    }
    init {
        tts=TextToSpeech(context){status->main.post{
            if(status==TextToSpeech.SUCCESS) _state.value=state.value.copy(ready=true,voices=tts?.voices.orEmpty().sortedBy{it.name}.take(128))
            else _state.value=state.value.copy(error="Android TTS is unavailable; select an offline or online voice model")
        }}
        tts?.setOnUtteranceProgressListener(object:UtteranceProgressListener(){
            override fun onStart(id:String?)=Unit
            override fun onDone(id:String?){main.post{if(id==utterance) spoken?.complete(Unit)}}
            @Deprecated("Android callback") override fun onError(id:String?){main.post{if(id==utterance) spoken?.completeExceptionally(IllegalStateException("Android speech playback failed"))}}
        })
    }
    fun configure(value:VoiceOptions){check(!state.value.busy);options=value}
    private fun configureAndroid():TextToSpeech {
        val engine=checkNotNull(tts);check(state.value.ready){"Android text-to-speech is starting or unavailable"}
        val eligible=state.value.voices.filter{options.network || !it.isNetworkConnectionRequired}
        val voice=if(options.androidVoice!=null) eligible.singleOrNull{it.name==options.androidVoice}
            else eligible.firstOrNull{it.locale.language==Locale.getDefault().language} ?: eligible.firstOrNull()
        checkNotNull(voice){"Install an offline Android TTS voice or explicitly allow a network voice"}
        check(engine.setVoice(voice)==TextToSpeech.SUCCESS){"Voice data is unavailable"}
        check(engine.setPitch(options.style.pitch)==TextToSpeech.SUCCESS && engine.setSpeechRate(options.style.speed)==TextToSpeech.SUCCESS)
        return engine
    }
    private suspend fun prepare(input:Boolean,output:Boolean=true) {
        val selected=buildSet {
            if(input && options.input==SpeechInput.OFFLINE_MODEL) add(options.sttPack)
            if(output && options.output==SpeechOutput.OFFLINE_MODEL) add(options.ttsPack)
        }
        if(selected.isNotEmpty()) {
            val packs=selected.map{id->models.models.value.singleOrNull{it.id==id} ?: error("Select an installed offline speech pack in Voice settings")}
            val info=android.app.ActivityManager.MemoryInfo()
            (context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager).getMemoryInfo(info)
            requireSpeechMemory(packs.sumOf{pack->pack.files.sumOf{it.bytes}},info.availMem,info.lowMemory)
        }
        if((input && options.input==SpeechInput.PROVIDER) || (output && options.output==SpeechOutput.PROVIDER)) {
            check(options.network) { "Allow network audio explicitly before using an online speech model" }
            check(providers.profiles.value.any{it.id==options.providerId && it.enabled}) { "Select an enabled speech provider" }
            online.preflight(options.providerId,options.sttModel.takeIf{input && options.input==SpeechInput.PROVIDER},options.ttsModel.takeIf{output && options.output==SpeechOutput.PROVIDER},options.providerVoice)
        }
        if(input && options.input==SpeechInput.OFFLINE_MODEL) withContext(Dispatchers.IO){offline.load(options.sttPack,SpeechKind.WHISPER_STT)}
        if(output && options.output==SpeechOutput.OFFLINE_MODEL) withContext(Dispatchers.IO){offline.load(options.ttsPack,SpeechKind.PIPER_TTS)}
        else if(output && options.output==SpeechOutput.ANDROID) configureAndroid()
        if(input && options.input==SpeechInput.ANDROID) {
            val local=Build.VERSION.SDK_INT>=31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            check(local || options.network) { "This Android recognizer cannot guarantee offline recognition. Select an offline speech pack, or explicitly allow the recognition service." }
            check(local || SpeechRecognizer.isRecognitionAvailable(context)) { "No Android recognition service is installed" }
        }
    }
    fun preview(sample:String="Hi, I'm Meshlit. It's good to hear from you. What would you like to explore today?")=
        launchSession(false) { speak(sample.take(200)) }
    fun testRecognition()=launchSession(false,needsInput=true,needsOutput=false) {
        _state.value=state.value.copy(phase="Listening for a recognition test")
        val text=withTimeout(90_000){recognize()}
        _state.value=state.value.copy(testTranscript=text.take(12000),phase="Recognition test complete")
    }
    fun start()=launchSession(true) {
        check(!chat.state.value.running) { "Wait for the current reply" }
        if(chat.state.value.current==null) chat.newChat()
        val selected=checkNotNull(chat.state.value.selectedId){"Chat is still starting"}
        val deadline=SystemClock.elapsedRealtime()+300_000
        var completed=0
        withTimeout(300_000) {
            while(voiceTurnAllowed(completed,SystemClock.elapsedRealtime(),deadline)) {
                if(turns.muted.value) _state.update{it.copy(phase="Microphone muted",inputLevel=0f,speechDetected=false)}
                turns.awaitUnmuted()
                check(chat.state.value.selectedId==selected) { "Conversation changed; start a new voice session" }
                try {
                    turns.run {
                        _state.update{it.copy(phase="Listening",inputLevel=0f,speechDetected=false,outputLevel=null)}
                        val text=withTimeout(90_000){recognize()}
                        currentCoroutineContext().ensureActive();check(text.isNotBlank() && text.length<=12000)
                        _state.update{it.copy(phase="Thinking",lastHeard=text,inputLevel=0f,speechDetected=false)}
                        try {
                            ownedGeneration=chat.send(text,voiceConversation=true)
                            check(ownedGeneration!=null) { chat.state.value.error ?: "Load or select a chat model first" }
                            withTimeout(180_000){chat.state.first{!it.running}}
                            currentCoroutineContext().ensureActive()
                            val current=chat.state.value
                            check(current.error==null && current.selectedId==selected){current.error ?: "Conversation changed"}
                            val reply=current.current?.messages?.lastOrNull()?.takeIf{it.role=="assistant"}?.text.orEmpty()
                            check(reply.isNotBlank()) { "The model returned no reply" }
                            _state.update{it.copy(lastReply=reply)}
                            ownedGeneration=null
                            speak(spokenReply(reply))
                        } finally {
                            chat.stopIfMatching(ownedGeneration)
                            if(ownedGeneration!=null) withContext(NonCancellable){withTimeout(15_000){chat.state.first{!it.running}}}
                            ownedGeneration=null
                        }
                    }
                    completed++
                    _state.update{it.copy(completedTurns=completed)}
                    if(options.mode==VoiceMode.SINGLE_TURN) break
                } catch(interrupted:VoiceTurnInterrupted) {
                    currentCoroutineContext().ensureActive()
                    // The canceled turn has released microphone/playback before another begins.
                } catch(silence:NoVoiceDetected) {
                    if(options.mode==VoiceMode.SINGLE_TURN) throw silence
                    _state.update{it.copy(phase="Listening",inputLevel=0f,speechDetected=false)}
                    delay(250)
                }
            }
        }
    }
    fun interrupt() {
        if(!state.value.active || state.value.phase !in setOf("Transcribing","Thinking","Preparing voice","Speaking")) return
        _state.update{it.copy(phase="Interrupting",outputLevel=null)}
        turns.interrupt()
    }
    fun setMuted(value:Boolean) {
        if(!state.value.active) return
        turns.setMuted(value,interruptCapture=capturing)
        _state.update{it.copy(muted=value,inputLevel=if(value) 0f else it.inputLevel)}
    }
    private fun onMicLevel(level:Double,detected:Boolean) {
        _state.update { it.copy(inputLevel=(level*8).toFloat().coerceIn(0f,1f),speechDetected=detected) }
    }
    private suspend fun recognize():String {
        capturing=true
        if(options.input==SpeechInput.ANDROID) {
            return try { androidTranscript() } finally {capturing=false}
        }
        val audio=try {withContext(Dispatchers.IO){captureVoiceTurn(::onMicLevel)}} finally {capturing=false}
        _state.update{it.copy(phase="Transcribing",inputLevel=0f,speechDetected=false)}
        return when(options.input) {
            SpeechInput.OFFLINE_MODEL->withContext(Dispatchers.IO){offline.transcribe(audio)}
            SpeechInput.PROVIDER->online.transcribe(options.providerId,options.sttModel,audio)
            SpeechInput.ANDROID->error("Recognition adapter changed during a voice session")
        }
    }
    private fun launchSession(conversation:Boolean,needsInput:Boolean=conversation,needsOutput:Boolean=true,work:suspend()->Unit) {
        check(Looper.myLooper()==Looper.getMainLooper());check(!state.value.busy)
        val started=++epoch;turns.setMuted(false)
        _state.value=state.value.copy(active=conversation,busy=true,phase="Preparing speech adapters",error=null,testTranscript=null,
            muted=false,elapsedSeconds=0,completedTurns=0,lastHeard=null,lastReply=null,caption=null,inputLevel=0f,outputLevel=null,speechDetected=false)
        session=scope.launch {
            val began=SystemClock.elapsedRealtime()
            val ticker=launch {while(isActive){delay(1000);_state.update{it.copy(elapsedSeconds=((SystemClock.elapsedRealtime()-began)/1000).toInt())}}}
            try {
                acquireAudioFocus()
                gate.run(ManagedFeature.MEDIA) { speechLock.withLock {try{
                    if(options.network) gate.run(ManagedFeature.CLOUD){withTimeout(180_000){prepare(needsInput,needsOutput)};work()}
                    else {withTimeout(180_000){prepare(needsInput,needsOutput)};work()}
                }finally{offline.close()}} }
            }catch(e:CancellationException){if(started==epoch && e is TimeoutCancellationException) _state.value=state.value.copy(error="Voice session reached its time limit")}
            catch(e:Exception){if(started==epoch) _state.value=state.value.copy(error=e.message ?: "Voice adapter failed")}
            finally {
                ticker.cancel();chat.stopIfMatching(ownedGeneration);ownedGeneration=null;releaseAndroid();releaseAudioFocus()
                if(started==epoch){session=null;_state.value=state.value.copy(active=false,busy=false,phase="Stopped",inputLevel=0f,outputLevel=null,speechDetected=false)}
            }
        }
    }
    private suspend fun speak(text:String) {
        for(segment in speechSegments(text)) {
            currentCoroutineContext().ensureActive()
            _state.update{it.copy(phase="Preparing voice",outputLevel=null)}
            when(options.output){
                SpeechOutput.OFFLINE_MODEL->{
                    val audio=withContext(Dispatchers.IO){offline.synthesize(segment,options.style)}
                    _state.update{it.copy(phase="Speaking",caption=segment)}
                    playVoice(audio,::onPlaybackLevel)
                }
                SpeechOutput.PROVIDER->{
                    val audio=online.synthesize(options.providerId,options.ttsModel,options.providerVoice,segment)
                    _state.update{it.copy(phase="Speaking",caption=segment)}
                    playVoice(audio,::onPlaybackLevel)
                }
                SpeechOutput.ANDROID->{
                    val done=CompletableDeferred<Unit>();spoken=done;utterance="voice-$epoch-${java.util.UUID.randomUUID()}"
                    _state.update{it.copy(phase="Speaking",caption=segment)}
                    try {
                        check(configureAndroid().speak(segment,TextToSpeech.QUEUE_FLUSH,null,utterance)==TextToSpeech.SUCCESS)
                        withTimeout(185_000){done.await()}
                    } finally {spoken=null;utterance=null;tts?.stop()}
                }
            }
        }
    }
    private fun onPlaybackLevel(value:Double) { _state.update{it.copy(outputLevel=(value*4).toFloat().coerceIn(0f,1f))} }
    private suspend fun androidTranscript():String {
        val local=Build.VERSION.SDK_INT>=31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        val done=CompletableDeferred<String>();recognized=done;val started=epoch
        recognizer=if(Build.VERSION.SDK_INT>=31 && local) SpeechRecognizer.createOnDeviceSpeechRecognizer(context) else SpeechRecognizer.createSpeechRecognizer(context)
        recognizer!!.setRecognitionListener(object:RecognitionListener {
            override fun onResults(results:Bundle?){if(started==epoch && recognized===done) done.complete(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty())}
            override fun onError(error:Int){if(started==epoch && recognized===done) done.completeExceptionally(androidRecognitionFailure(error))}
            override fun onReadyForSpeech(params:Bundle?)=Unit
            override fun onBeginningOfSpeech()=Unit
            override fun onRmsChanged(value:Float){if(started==epoch && recognized===done) onMicLevel((value.coerceAtLeast(0f)/40f).toDouble(),value>2f)}
            override fun onBufferReceived(buffer:ByteArray?)=Unit
            override fun onEndOfSpeech(){if(started==epoch && recognized===done){capturing=false;_state.update{it.copy(phase="Transcribing",inputLevel=0f,speechDetected=false)}}}
            override fun onPartialResults(results:Bundle?)=Unit
            override fun onEvent(type:Int,params:Bundle?)=Unit
        })
        recognizer!!.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE,Locale.getDefault().toLanguageTag());putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,!options.network);putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1)
        })
        try{return done.await()}finally{recognized=null;recognizer?.cancel();recognizer?.destroy();recognizer=null}
    }
    private fun releaseAndroid(){recognized?.cancel();recognized=null;spoken?.cancel();spoken=null;utterance=null;recognizer?.cancel();recognizer?.destroy();recognizer=null;tts?.stop()}
    fun stop(){epoch++;session?.cancel();chat.stopIfMatching(ownedGeneration);ownedGeneration=null;releaseAndroid();_state.value=state.value.copy(active=false,phase=if(session==null) "Stopped" else "Stopping")
        // Keep controls locked until cancellation releases microphone, playback and native models.
        val pending=session;scope.launch{pending?.join();if(session===pending){session=null;_state.value=state.value.copy(active=false,busy=false,phase="Stopped",inputLevel=0f,outputLevel=null,speechDetected=false)}}
    }
    fun close(){stop();scope.launch{session?.join();tts?.shutdown();tts=null;scope.cancel()}}
    companion object { private val speechLock=Mutex() }
}
internal fun voiceTurnAllowed(turns:Int,now:Long,deadline:Long)=turns<10 && now<deadline
