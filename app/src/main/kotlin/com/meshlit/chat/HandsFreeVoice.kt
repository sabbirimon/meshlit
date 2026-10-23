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

enum class VoiceStyle(val label:String,val pitch:Float,val speed:Float) {
    NATURAL("Natural",1f,1f),DEEP("Lower voice",0.8f,0.95f),BRIGHT("Higher voice",1.2f,1f),ROBOTIC("Robotic style",0.85f,0.8f),YOUTHFUL("Youthful style",1.35f,1.05f)
}
enum class SpeechInput(val label:String) { OFFLINE_MODEL("Offline speech model"),ANDROID("Android recognizer"),PROVIDER("Online speech model") }
enum class SpeechOutput(val label:String) { ANDROID("Installed Android voice"),OFFLINE_MODEL("Offline voice model"),PROVIDER("Online voice model") }
data class VoiceOptions(val input:SpeechInput=SpeechInput.OFFLINE_MODEL,val output:SpeechOutput=SpeechOutput.ANDROID,
    val sttPack:String="",val ttsPack:String="",val providerId:String="",val sttModel:String="",val ttsModel:String="",val providerVoice:String="coral",val network:Boolean=false,val androidVoice:String?=null,val style:VoiceStyle=VoiceStyle.NATURAL)
data class VoiceState(val phase:String="Stopped",val active:Boolean=false,val error:String?=null,val ready:Boolean=false,val voices:List<Voice> = emptyList(),val busy:Boolean=false)
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
    private suspend fun prepare(input:Boolean) {
        if((input && options.input==SpeechInput.PROVIDER) || options.output==SpeechOutput.PROVIDER) {
            check(options.network) { "Allow network audio explicitly before using an online speech model" }
            check(providers.profiles.value.any{it.id==options.providerId && it.enabled}) { "Select an enabled speech provider" }
            online.preflight(options.providerId,options.sttModel.takeIf{input && options.input==SpeechInput.PROVIDER},options.ttsModel.takeIf{options.output==SpeechOutput.PROVIDER},options.providerVoice)
        }
        if(input && options.input==SpeechInput.OFFLINE_MODEL) withContext(Dispatchers.IO){offline.load(options.sttPack,SpeechKind.WHISPER_STT)}
        if(options.output==SpeechOutput.OFFLINE_MODEL) withContext(Dispatchers.IO){offline.load(options.ttsPack,SpeechKind.PIPER_TTS)}
        else if(options.output==SpeechOutput.ANDROID) configureAndroid()
        if(input && options.input==SpeechInput.ANDROID) {
            val local=Build.VERSION.SDK_INT>=31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            check(local || options.network) { "This Android recognizer cannot guarantee offline recognition. Select an offline speech pack, or explicitly allow the recognition service." }
            check(local || SpeechRecognizer.isRecognitionAvailable(context)) { "No Android recognition service is installed" }
        }
    }
    fun preview(sample:String="Hi, I'm Meshlit. It's good to hear from you. What would you like to explore today?")=
        launchSession(false) { speak(sample.take(200)) }
    fun start()=launchSession(true) {
        check(!chat.state.value.running) { "Wait for the current reply" }
        if(chat.state.value.current==null) chat.newChat()
        val selected=chat.state.value.selectedId;val deadline=SystemClock.elapsedRealtime()+300_000;var turns=0
        withTimeout(300_000) {while(voiceTurnAllowed(turns,SystemClock.elapsedRealtime(),deadline)) {
            check(chat.state.value.selectedId==selected) { "Conversation changed; start a new voice session" }
            _state.value=state.value.copy(phase="Listening")
            val text=withTimeout(90_000){when(options.input){
                SpeechInput.ANDROID->androidTranscript()
                SpeechInput.OFFLINE_MODEL->withContext(Dispatchers.IO){offline.transcribe(captureVoiceTurn())}
                SpeechInput.PROVIDER->online.transcribe(options.providerId,options.sttModel,captureVoiceTurn())
            }}
            currentCoroutineContext().ensureActive();check(text.isNotBlank() && text.length<=12000)
            _state.value=state.value.copy(phase="Thinking");ownedGeneration=chat.send(text)
            check(ownedGeneration!=null) { chat.state.value.error ?: "Load or select a chat model first" }
            withTimeout(180_000){chat.state.first{!it.running}}
            ownedGeneration=null;currentCoroutineContext().ensureActive()
            val current=chat.state.value;check(current.error==null && current.selectedId==selected){current.error ?: "Conversation changed"}
            val reply=current.current?.messages?.lastOrNull()?.takeIf{it.role=="assistant"}?.text.orEmpty()
            check(reply.isNotBlank()) { "The model returned no reply" };speak(reply.take(4000));turns++
        }}
    }
    private fun launchSession(conversation:Boolean,work:suspend()->Unit) {
        check(Looper.myLooper()==Looper.getMainLooper());check(!state.value.busy)
        val started=++epoch;_state.value=state.value.copy(active=conversation,busy=true,phase="Preparing speech adapters",error=null)
        session=scope.launch {
            try {
                gate.run(ManagedFeature.MEDIA) { speechLock.withLock {try{
                    if(options.network) gate.run(ManagedFeature.CLOUD){withTimeout(180_000){prepare(conversation)};work()}
                    else {withTimeout(180_000){prepare(conversation)};work()}
                }finally{offline.close()}} }
            }catch(e:CancellationException){if(started==epoch && e is TimeoutCancellationException) _state.value=state.value.copy(error="Voice session reached its time limit")}
            catch(e:Exception){if(started==epoch) _state.value=state.value.copy(error=e.message ?: "Voice adapter failed")}
            finally {
                chat.stopIfMatching(ownedGeneration);ownedGeneration=null;releaseAndroid()
                if(started==epoch){session=null;_state.value=state.value.copy(active=false,busy=false,phase="Stopped")}
            }
        }
    }
    private suspend fun speak(text:String) {
        _state.value=state.value.copy(phase="Speaking")
        when(options.output){
            SpeechOutput.OFFLINE_MODEL->playVoice(withContext(Dispatchers.IO){offline.synthesize(text,options.style)})
            SpeechOutput.PROVIDER->playVoice(online.synthesize(options.providerId,options.ttsModel,options.providerVoice,text))
            SpeechOutput.ANDROID->{val done=CompletableDeferred<Unit>();spoken=done;utterance="voice-$epoch"
                check(configureAndroid().speak(text,TextToSpeech.QUEUE_FLUSH,null,utterance)==TextToSpeech.SUCCESS)
                try{withTimeout(185_000){done.await()}}finally{spoken=null;utterance=null;tts?.stop()}}
        }
    }
    private suspend fun androidTranscript():String {
        val local=Build.VERSION.SDK_INT>=31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        val done=CompletableDeferred<String>();recognized=done;val started=epoch
        recognizer=if(Build.VERSION.SDK_INT>=31 && local) SpeechRecognizer.createOnDeviceSpeechRecognizer(context) else SpeechRecognizer.createSpeechRecognizer(context)
        recognizer!!.setRecognitionListener(object:RecognitionListener {
            override fun onResults(results:Bundle?){if(started==epoch) done.complete(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty())}
            override fun onError(error:Int){if(started==epoch) done.completeExceptionally(IllegalStateException("Android recognition failed (code $error)"))}
            override fun onReadyForSpeech(params:Bundle?)=Unit
            override fun onBeginningOfSpeech()=Unit
            override fun onRmsChanged(value:Float)=Unit
            override fun onBufferReceived(buffer:ByteArray?)=Unit
            override fun onEndOfSpeech()=Unit
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
        val pending=session;scope.launch{pending?.join();if(session===pending){session=null;_state.value=state.value.copy(active=false,busy=false,phase="Stopped")}}
    }
    fun close(){stop();scope.launch{session?.join();tts?.shutdown();tts=null;scope.cancel()}}
    companion object { private val speechLock=Mutex() }
}
internal fun voiceTurnAllowed(turns:Int,now:Long,deadline:Long)=turns<10 && now<deadline
