package com.meshlit.ui.modern

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.meshlit.chat.*
import com.meshlit.providers.OnlineProviders
import com.meshlit.core.inference.models.OnlineProtocol
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable private fun VoiceChoice(label:String,selected:String,items:List<Pair<String,String>>,enabled:Boolean,onSelect:(String)->Unit) {
    var expanded by remember{mutableStateOf(false)}
    Box(Modifier.fillMaxWidth()){OutlinedButton(enabled=enabled,onClick={expanded=true},modifier=Modifier.fillMaxWidth()){Text("$label: ${items.firstOrNull{it.first==selected}?.second ?: "Select"}",maxLines=1,overflow=TextOverflow.Ellipsis)}
        DropdownMenu(expanded,{expanded=false}){items.forEach{(id,name)->DropdownMenuItem(text={Text(name)},onClick={onSelect(id);expanded=false})}}}
}
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun HandsFreeVoiceDialog(chat:ChatController,maxHeight:Dp=Dp.Infinity,onClose:()->Unit) {
    val context=LocalContext.current;val models=koinInject<VoiceModels>();val providers=koinInject<OnlineProviders>()
    val voice=remember{HandsFreeVoice(context,chat,models,providers)}
    val chatState by chat.state.collectAsState();val state by voice.state.collectAsState();val packs by models.models.collectAsState();val profiles by providers.profiles.collectAsState()
    val coordinator=koinInject<com.meshlit.core.inference.InferenceCoordinator>()
    val replyRuntime by coordinator.state.collectAsState()
    val scope=rememberCoroutineScope()
    val naturalAdapter=remember{ProviderSpeechAdapter(context,providers)}
    var naturalEngines by remember{mutableStateOf(emptyList<com.meshlit.core.inference.models.NaturalEngine>())}
    var naturalVoices by remember{mutableStateOf(emptyList<com.meshlit.core.inference.models.NaturalVoice>())}
    val connectionOutputs=setOf(SpeechOutput.PROVIDER,SpeechOutput.OPENLIVE,SpeechOutput.ELEVENLABS)
    val storedPreferences=remember(context){VoicePreferences.read(context,models.models.value)}
    var preferenceProblem by remember{mutableStateOf(storedPreferences.problem)}
    var options by remember(context){mutableStateOf(storedPreferences.options)}
    var showSettings by remember { mutableStateOf(false) }
    var previewText by remember { mutableStateOf("Hi, I'm Meshlit. It's good to hear from you. What would you like to explore today?") }
    var importing by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
    var testPending by remember{mutableStateOf(false)}
    val enabled=!state.busy && !importing && preferenceProblem==null
    val owner=LocalLifecycleOwner.current
    fun start(){voice.configure(options);voice.start()}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){allowed->if(allowed) {
        if(testPending){voice.configure(options);voice.testRecognition()}else start()
    } else error="Microphone permission was not granted"}
    val importer=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->if(uri!=null) scope.launch {
        importing=true;error=null
        try {
            val pack=models.install(uri)
            options=if(pack.kind==SpeechKind.WHISPER_STT) options.copy(sttPack=pack.id) else options.copy(ttsPack=pack.id)
        } catch(e:Exception){error=e.message ?: "Voice pack import failed"}finally{importing=false}
    }}
    val missingRecognition=options.input==SpeechInput.OFFLINE_MODEL && packs.none { it.id==options.sttPack && it.kind==SpeechKind.WHISPER_STT }
    val missingVoice=options.output==SpeechOutput.OFFLINE_MODEL && packs.none { it.id==options.ttsPack && it.kind==SpeechKind.PIPER_TTS }
    val usesProvider=options.input==SpeechInput.PROVIDER || options.output in connectionOutputs
    val providerReady=options.network && profiles.any{it.id==options.providerId && it.enabled && it.protocol in setOf(OnlineProtocol.OPENAI,OnlineProtocol.OPENAI_COMPATIBLE)}
    val missingProvider=usesProvider && (!providerReady ||
        options.input==SpeechInput.PROVIDER && options.sttModel.isBlank() || options.output==SpeechOutput.PROVIDER && options.ttsModel.isBlank() || options.output in setOf(SpeechOutput.OPENLIVE,SpeechOutput.ELEVENLABS) && options.naturalVoice.isBlank())
    val missingOutputProvider=options.output in connectionOutputs && (!providerReady || if(options.output==SpeechOutput.PROVIDER) options.ttsModel.isBlank() else options.naturalVoice.isBlank())
    val recognitionReady=!missingRecognition && (options.input!=SpeechInput.PROVIDER || providerReady && options.sttModel.isNotBlank())
    val offlineInputReady=when(options.input) {
        SpeechInput.OFFLINE_MODEL -> !missingRecognition
        SpeechInput.ANDROID -> android.os.Build.VERSION.SDK_INT>=31 && runCatching { android.speech.SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)
        SpeechInput.PROVIDER -> false
    }
    LaunchedEffect(options,preferenceProblem) {
        if(preferenceProblem==null) try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){VoicePreferences.save(context,options)}
        }catch(c:kotlinx.coroutines.CancellationException){throw c}catch(e:Exception){error=e.message ?: "Voice settings could not be saved"}
    }
    DisposableEffect(voice,owner){val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_PAUSE) voice.stop()}
        owner.lifecycle.addObserver(observer);onDispose{owner.lifecycle.removeObserver(observer);voice.close()}}
    val replyProblem=remember(chatState,profiles,replyRuntime){chat.voiceRouteProblem()}
    val canStart=preferenceProblem==null && replyProblem==null && !missingRecognition && !missingVoice && !missingProvider && (options.network || offlineInputReady)
    Surface(Modifier.fillMaxSize().heightIn(max=maxHeight).testTag("voice-panel"),color=MaterialTheme.colorScheme.surface) {
    Column(Modifier.fillMaxSize().padding(horizontal=16.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if(showSettings) "Voice settings" else "Live conversation",style=MaterialTheme.typography.titleLarge)
                Text(if(options.network) "Network audio allowed" else "Audio stays on this phone",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick={showSettings=!showSettings},modifier=Modifier.testTag("voice-settings-toggle")) {
                Icon(if(showSettings) Icons.Default.Close else Icons.Default.Tune,if(showSettings) "Hide voice settings" else "Voice settings")
            }
            IconButton(onClick={voice.stop();onClose()}){Icon(Icons.Default.Close,"End and close voice")}
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("voice-options"),
            verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if(!showSettings) {
            Column(Modifier.fillMaxWidth().padding(vertical=16.dp),horizontalAlignment=Alignment.CenterHorizontally,
                verticalArrangement=Arrangement.spacedBy(12.dp)) {
                VoiceActivity(state,Modifier.size(144.dp))
                Text(if(!state.busy && state.error==null) "Ready when you are" else state.phase,
                    modifier=Modifier.testTag("voice-phase"),style=MaterialTheme.typography.headlineSmall,textAlign=TextAlign.Center)
                val hint=when {
                    state.muted -> if(state.micNotice!=null) "Microphone paused. Retry when ready." else "Your microphone is muted."
                    state.phase=="Listening" -> if(state.speechDetected) "I hear you. Pause or tap Send voice." else "Speak naturally, then pause."
                    state.phase=="Thinking" -> "Your chat model is preparing a reply."
                    state.phase=="Transcribing" -> "Turning your speech into text."
                    state.phase=="Preparing voice" -> "Preparing spoken audio."
                    state.phase=="Speaking" -> "Tap Interrupt to speak again."
                    !state.busy -> "Listen, talk, and hear a reply from your selected chat model."
                    else -> "Preparing this voice session."
                }
                Text(hint,style=MaterialTheme.typography.bodyMedium,textAlign=TextAlign.Center,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(state.busy) Text("${state.elapsedSeconds/60}:${(state.elapsedSeconds%60).toString().padStart(2,'0')} · ${state.completedTurns} replies",style=MaterialTheme.typography.labelMedium)
            }
            if(!state.busy) FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                VoiceMode.entries.forEach{mode->FilterChip(options.mode==mode,{options=options.copy(mode=mode)},label={Text(mode.label)},enabled=enabled)}
            }
            if(state.phase=="Listening" || state.phase=="Listening for a recognition test") {
                LinearProgressIndicator(progress={state.inputLevel.coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().testTag("voice-input-level"))
                Text(if(state.speechDetected) "Speech threshold reached" else if(state.inputLevel>0f) "Audio detected · below speech threshold" else "Waiting for microphone audio",style=MaterialTheme.typography.labelMedium)
            }
            if(options.captions) {
                state.lastHeard?.let{Text("You: $it",style=MaterialTheme.typography.bodyMedium,modifier=Modifier.testTag("voice-user-caption"))}
                val generating=chatState.current?.messages?.lastOrNull()?.takeIf{it.role=="assistant"}?.text
                val caption=when(state.phase) {
                    "Thinking" -> generating?.takeIf{it.isNotBlank()}
                    "Speaking","Preparing voice" -> state.caption
                    else -> state.lastReply?.let(::spokenReply)
                }
                caption?.takeIf{it.isNotBlank()}?.let{Text(it,style=MaterialTheme.typography.bodyLarge,modifier=Modifier.testTag("voice-reply-caption"))}
            }
            state.testTranscript?.let{Text("Heard: $it",style=MaterialTheme.typography.bodyMedium)}
            Text("Recognition: ${if(options.input==SpeechInput.OFFLINE_MODEL) packs.firstOrNull{it.id==options.sttPack}?.let{"${it.name} · ${it.language}"} ?: "Select an offline speech pack" else options.input.label}",style=MaterialTheme.typography.labelMedium)
            Text("Voice: ${options.output.label}",style=MaterialTheme.typography.labelMedium)
            if(!canStart && !state.busy) {
                Text(replyProblem ?: "Voice setup needs attention. Choose an installed recognition pack and voice, or configure your speech connection.",color=MaterialTheme.colorScheme.error)
                OutlinedButton(onClick={showSettings=true}) {Text("Set up voice")}
            }
            Text("Live mode waits until each reply finishes before listening again. Automatic interruption by speaking is not available; use Interrupt.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
        SettingsFeatureSection("Speech connections") {
        Row(verticalAlignment=Alignment.CenterVertically){Text("Audio conversation captions",Modifier.weight(1f));Switch(options.captions,{options=options.copy(captions=it)},enabled=enabled)}
        Text("Live mode listens again after each reply, for up to 10 replies or five minutes. Tap Interrupt to stop a reply and speak. Mute releases capture; End releases the session. Automatic acoustic interruption is not yet qualified.",style=MaterialTheme.typography.bodySmall)
        VoiceChoice("Recognition",options.input.name,SpeechInput.entries.map{it.name to it.label},enabled){options=options.copy(input=SpeechInput.valueOf(it))}
        VoiceChoice("Voice reply",options.output.name,SpeechOutput.entries.map{it.name to it.label},enabled){naturalEngines=emptyList();naturalVoices=emptyList();options=options.copy(output=SpeechOutput.valueOf(it),style=VoiceStyle.NATURAL,naturalVoice="",naturalEngine=if(it==SpeechOutput.ELEVENLABS.name) "eleven_multilingual_v2" else "kokoro-en-v0_19-int8")}
        Row{Text("Allow network audio",Modifier.weight(1f));Switch(options.network,{options=options.copy(network=it)},enabled=enabled,modifier=Modifier.testTag("voice-network"))}
        Text("Off means no online voice request or network Android voice. Android's guaranteed on-device recognizer needs supported Android 12+. On Android 11 or earlier, choose an installed offline recognition pack for offline input.")
        if(options.input==SpeechInput.OFFLINE_MODEL) VoiceChoice("Offline recognition pack",options.sttPack,packs.filter{it.kind==SpeechKind.WHISPER_STT}.map{it.id to "${it.name} · ${it.language}"},enabled){options=options.copy(sttPack=it)}
        if(options.output==SpeechOutput.OFFLINE_MODEL) VoiceChoice("Offline voice pack",options.ttsPack,packs.filter{it.kind==SpeechKind.PIPER_TTS}.map{it.id to "${it.name} · ${it.language}"},enabled){options=options.copy(ttsPack=it)}
        if(missingRecognition) Text("No Whisper recognition pack selected. Import a compatible ZIP below, or choose Android recognizer / online speech.",color=MaterialTheme.colorScheme.error)
        if(missingVoice) Text("No Piper voice pack selected. Import a compatible ZIP below, or choose an installed Android voice / online speech.",color=MaterialTheme.colorScheme.error)
        if(missingProvider) Text("Choose an enabled speech connection, allow network audio, and select the model and voice for the chosen adapter.",color=MaterialTheme.colorScheme.error)
        if(options.output==SpeechOutput.ANDROID) VoiceChoice("Installed voice",options.androidVoice.orEmpty(),state.voices.filter{options.network || !it.isNetworkConnectionRequired}.map{it.name to "${it.name} · ${it.locale.displayName} · ${if(it.isNetworkConnectionRequired) "network" else "offline"}"},enabled){options=options.copy(androidVoice=it)}
        if(usesProvider) {
            VoiceChoice("Speech provider",options.providerId,profiles.filter{it.enabled && it.protocol in setOf(OnlineProtocol.OPENAI,OnlineProtocol.OPENAI_COMPATIBLE)}.map{it.id to it.name},enabled){naturalEngines=emptyList();naturalVoices=emptyList();options=options.copy(providerId=it,naturalVoice="")}
            Text("Only selected online recognition sends microphone audio. Speech synthesis sends reply text to the selected connection. Configure credentials in Online providers. This can still use your offline chat LLM. Provider fees and supported models vary.")
            if(options.input==SpeechInput.PROVIDER) OutlinedTextField(options.sttModel,{if(it.length<=160)options=options.copy(sttModel=it)},label={Text("Recognition model ID")},singleLine=true,enabled=enabled)
            if(options.output==SpeechOutput.PROVIDER) {
                OutlinedTextField(options.ttsModel,{if(it.length<=160)options=options.copy(ttsModel=it)},label={Text("Speech synthesis model ID")},singleLine=true,enabled=enabled)
                OutlinedTextField(options.providerVoice,{if(it.length<=80)options=options.copy(providerVoice=it)},label={Text("Provider voice ID")},singleLine=true,enabled=enabled)
            }
        }
        if(options.output in setOf(SpeechOutput.OPENLIVE,SpeechOutput.ELEVENLABS)) {
            Text(if(options.output==SpeechOutput.OPENLIVE) "Kokoro and Pocket run on your OpenLive host, not inside this phone. Create a dedicated connection in Online providers with the web proxy base ending /api/voice (HTTPS or a literal-loopback tunnel). Install the engine explicitly in OpenLive Settings. Your chat LLM may remain on this phone." else "Create a dedicated credential profile with https://api.elevenlabs.io/v1. Its API key stays in encrypted provider storage. This adapter uses xi-api-key, not OpenAI's speech protocol. Voice previews and replies can incur provider charges. Speech calls are outside chat token-budget enforcement.")
            OutlinedButton(enabled=enabled && providerReady,onClick={scope.launch {
                importing=true;error=null
                try {
                    if(options.output==SpeechOutput.OPENLIVE){
                        naturalEngines=naturalAdapter.naturalEngines(options.providerId)
                        val selected=naturalEngines.firstOrNull{it.id==options.naturalEngine && it.ready} ?: naturalEngines.firstOrNull{it.ready}
                        naturalVoices=selected?.voices.orEmpty()
                        if(selected!=null)options=options.copy(naturalEngine=selected.id,naturalVoice=selected.voices.firstOrNull{it.id==options.naturalVoice}?.id ?: selected.voices.firstOrNull()?.id.orEmpty())
                    } else naturalVoices=naturalAdapter.elevenVoices(options.providerId)
                }catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){error=e.message ?: "Voice discovery failed"}finally{importing=false}
            }}){Text("Load available engines / voices")}
            if(options.output==SpeechOutput.OPENLIVE){
                VoiceChoice("Local engine",options.naturalEngine,naturalEngines.filter{it.ready}.map{it.id to it.name},enabled){id->
                    val selected=naturalEngines.first{it.id==id};naturalVoices=selected.voices;options=options.copy(naturalEngine=id,naturalVoice=selected.voices.firstOrNull()?.id.orEmpty())
                }
                naturalEngines.firstOrNull{it.id==options.naturalEngine}?.let{Text("Model licence: ${it.license}",style=MaterialTheme.typography.bodySmall)}
                Text("Local voice speed: ${"%.2f".format(java.util.Locale.ROOT,options.tuning.speechRate)}×")
                Slider(value=options.tuning.speechRate,onValueChange={options=options.copy(tuning=options.tuning.copy(speechRate=it))},valueRange=0.5f..2f,enabled=enabled)
                naturalEngines.filter{!it.ready}.forEach{Text("${it.name}: install or enable on the OpenLive host",style=MaterialTheme.typography.bodySmall)}
            } else OutlinedTextField(options.naturalEngine,{if(it.length<=160)options=options.copy(naturalEngine=it)},label={Text("ElevenLabs TTS model ID")},enabled=enabled,singleLine=true)
            if(options.output==SpeechOutput.ELEVENLABS){
                Row(verticalAlignment=Alignment.CenterVertically){Text("Require a free-tier account",Modifier.weight(1f));Switch(options.naturalFreeOnly,{options=options.copy(naturalFreeOnly=it)},enabled=enabled)}
                Text("On: check the provider's Free account tier and remaining character allowance before each speech request; missing/insufficient allowance blocks speech. This cannot guarantee provider billing or other apps' concurrent usage. Off: your configured paid plan may be charged.",style=MaterialTheme.typography.bodySmall)
            } else Text("Local Kokoro/Pocket have no speech API fee. The host still uses its own CPU, memory and electricity.",style=MaterialTheme.typography.bodySmall)
            VoiceChoice("Natural voice",options.naturalVoice,naturalVoices.map{it.id to it.name},enabled){options=options.copy(naturalVoice=it)}
        }
        if(options.output==SpeechOutput.ANDROID) {
            Text("Android pitch/rate presets describe playback styles, not a person's identity, age or gender.")
            FlowRow{VoiceStyle.entries.forEach{value->FilterChip(options.style==value,{options=options.copy(style=value)},enabled=enabled,label={Text(value.label)})}}
        } else Text("Voice packs and provider voice IDs determine the speaker. Pitch presets are available for Android voices only; Piper and this provider adapter do not implement them.")
        }
        SettingsFeatureSection("Voice tuning",Modifier.testTag("voice-tuning")) {
        if(options.input!=SpeechInput.ANDROID) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("Adapt threshold for quiet input",Modifier.weight(1f))
                Switch(options.tuning.automaticThreshold,{options=options.copy(tuning=options.tuning.copy(automaticThreshold=it))},enabled=enabled)
            }
            Text("Optional: uses the quietest measured audio after half a second, capped by your threshold and a fixed noise floor. Turn it off in changing background noise.",style=MaterialTheme.typography.bodySmall)
            Text("Microphone threshold: ${"%.3f".format(java.util.Locale.ROOT,options.tuning.threshold)}")
            Slider(value=options.tuning.threshold.toFloat(),onValueChange={options=options.copy(tuning=options.tuning.copy(threshold=it.toDouble().coerceIn(0.001,0.1)))},valueRange=0.001f..0.1f,enabled=enabled,modifier=Modifier.testTag("voice-threshold"))
            Text("Pause before sending: ${options.tuning.silenceMs} ms")
            Slider(value=options.tuning.silenceMs.toFloat(),onValueChange={options=options.copy(tuning=options.tuning.copy(silenceMs=it.toInt().coerceIn(300,2000)))},valueRange=300f..2000f,enabled=enabled,modifier=Modifier.testTag("voice-silence"))
            Text("Lower thresholds hear quieter speech but admit more background noise. This energy detector is for offline/provider PCM capture; Android recognizer manages its own endpoint detection.",style=MaterialTheme.typography.bodySmall)
        }
        if(options.output==SpeechOutput.ANDROID) {
            Text("Playback speed: ${"%.2f".format(java.util.Locale.ROOT,options.tuning.speechRate)}×")
            Slider(value=options.tuning.speechRate,onValueChange={options=options.copy(tuning=options.tuning.copy(speechRate=it))},valueRange=0.5f..2f,enabled=enabled,modifier=Modifier.testTag("voice-rate"))
            Text("Playback pitch: ${"%.2f".format(java.util.Locale.ROOT,options.tuning.pitch)}×")
            Slider(value=options.tuning.pitch,onValueChange={options=options.copy(tuning=options.tuning.copy(pitch=it))},valueRange=0.5f..2f,enabled=enabled,modifier=Modifier.testTag("voice-pitch"))
            Text("Speed and pitch multiply the selected style, bounded to 0.5–2×. Play the real sample below to hear the result.",style=MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(enabled=enabled,onClick={options=options.copy(tuning=VoiceTuning())}){Text("Reset voice tuning")}
        Text("Tuning saves on this phone. The current chat's selected local model or online provider answers voice turns; speech providers are configured separately. Camera/photo analysis uses the selected vision provider in chat.",style=MaterialTheme.typography.bodySmall)
        }
        SettingsFeatureSection("Test speech") {
        OutlinedTextField(previewText,{ if(it.length<=200) previewText=it },Modifier.fillMaxWidth(),label={Text("Voice sample text")},minLines=2,maxLines=3,enabled=enabled)
        OutlinedButton(enabled=enabled && !missingVoice && !missingOutputProvider && previewText.isNotBlank(),onClick={voice.configure(options);voice.preview(previewText)},modifier=Modifier.testTag("voice-preview")){Text("Play real voice sample")}
        OutlinedButton(enabled=enabled && recognitionReady,onClick={testPending=true;if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED){voice.configure(options);voice.testRecognition()}else permission.launch(Manifest.permission.RECORD_AUDIO)},modifier=Modifier.testTag("voice-recognition-test")){Text("Test recognition only")}
        state.testTranscript?.let{Text("Heard: $it",style=MaterialTheme.typography.bodyMedium)}
        }
        SettingsFeatureSection("Offline speech models") {
        Text("Import a verified Meshlit voice ZIP from local storage or another device. Whisper recognition and Piper synthesis packs are separate from GGUF chat models; no pack is silently downloaded. This build supports English Whisper and Piper. The ZIP needs a Meshlit manifest with checksums; an arbitrary model ZIP will fail verification.")
        // Documents providers assign different MIME types to ZIP files. Accept the
        // picker result and let VoiceModels validate the manifest and every entry.
        OutlinedButton(enabled=enabled,onClick={importer.launch("*/*")}){Text(if(importing) "Verifying voice pack…" else "Import offline voice pack")}
        packs.forEach{pack->Row{Text("${pack.name}\n${pack.kind} · ${pack.license}",Modifier.weight(1f));TextButton(enabled=enabled,onClick={scope.launch{importing=true;try{models.remove(pack.id)}catch(e:Exception){error=e.message}finally{importing=false}}}){Text("Remove")}}}
        }
        Text("Captions follow synthesized phrases, not word timestamps. PCM activity uses measured audio; Android voices expose playback state only. Cloud realtime speech requires a separately qualified adapter.")
        }
        }
        (preferenceProblem ?: state.error ?: error ?: state.micNotice)?.let{Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("voice-problem"))}
        if(preferenceProblem!=null) OutlinedButton(enabled=!importing,onClick={scope.launch {
            importing=true
            try{options=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){VoicePreferences.resetUnreadable(context,packs)};preferenceProblem=null;error=null}
            catch(c:kotlinx.coroutines.CancellationException){throw c}catch(e:Exception){preferenceProblem=e.message ?: "Voice settings could not be reset"}finally{importing=false}
        }}){Text("Reset unreadable voice settings")}
        if(state.active) FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick={voice.setMuted(!state.muted)},modifier=Modifier.testTag("voice-mute")){
                Icon(if(state.muted) Icons.Default.Mic else Icons.Default.MicOff,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp))
                Text(if(state.muted) if(state.micNotice!=null) "Retry microphone" else "Unmute" else "Mute")
            }
            if(state.phase=="Listening") OutlinedButton(onClick=voice::sendVoice,modifier=Modifier.testTag("voice-send")) {
                Icon(Icons.Default.Send,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("Send voice")
            } else OutlinedButton(enabled=state.phase in setOf("Transcribing","Thinking","Preparing voice","Speaking"),onClick=voice::interrupt,modifier=Modifier.testTag("voice-interrupt")){Text("Interrupt")}
        }
        Button(enabled=!importing && (state.busy || canStart),onClick={if(state.busy) voice.stop() else {
            testPending=false
            if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) start() else permission.launch(Manifest.permission.RECORD_AUDIO)
        }},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).testTag("voice-start-stop"),
            colors=if(state.busy) ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.errorContainer,contentColor=MaterialTheme.colorScheme.onErrorContainer) else ButtonDefaults.buttonColors()) {
                Icon(if(state.busy) Icons.Default.CallEnd else Icons.Default.Mic,null);Spacer(Modifier.width(8.dp))
                Text(if(state.busy) "End conversation" else if(options.mode==VoiceMode.LIVE) "Start live" else "Start one turn")
        }
    } }
}

/** Voice opened from chat gets its own surface so capture/Stop never sit in a 260dp scroller. */
@Composable internal fun VoiceConversationOverlay(chat:ChatController,onClose:()->Unit) {
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {HandsFreeVoiceDialog(chat,onClose=onClose)}
    }
}
