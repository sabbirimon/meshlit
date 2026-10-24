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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
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
@Composable internal fun HandsFreeVoiceDialog(chat:ChatController,maxHeight:Dp=340.dp,onClose:()->Unit) {
    val context=LocalContext.current;val models=koinInject<VoiceModels>();val providers=koinInject<OnlineProviders>()
    val voice=remember{HandsFreeVoice(context,chat,models,providers)}
    val chatState by chat.state.collectAsState();val state by voice.state.collectAsState();val packs by models.models.collectAsState();val profiles by providers.profiles.collectAsState()
    val scope=rememberCoroutineScope()
    var options by remember(context){mutableStateOf(VoicePreferences.load(context,models.models.value))}
    var showSettings by remember { mutableStateOf(false) }
    var previewText by remember { mutableStateOf("Hi, I'm Meshlit. It's good to hear from you. What would you like to explore today?") }
    var importing by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
    var testPending by remember{mutableStateOf(false)}
    val enabled=!state.busy && !importing
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
    val usesProvider=options.input==SpeechInput.PROVIDER || options.output==SpeechOutput.PROVIDER
    val providerReady=options.network && profiles.any{it.id==options.providerId && it.enabled && it.protocol in setOf(OnlineProtocol.OPENAI,OnlineProtocol.OPENAI_COMPATIBLE)}
    val missingProvider=usesProvider && (!providerReady ||
        options.input==SpeechInput.PROVIDER && options.sttModel.isBlank() || options.output==SpeechOutput.PROVIDER && options.ttsModel.isBlank())
    val recognitionReady=!missingRecognition && (options.input!=SpeechInput.PROVIDER || providerReady && options.sttModel.isNotBlank())
    val offlineInputReady=when(options.input) {
        SpeechInput.OFFLINE_MODEL -> !missingRecognition
        SpeechInput.ANDROID -> android.os.Build.VERSION.SDK_INT>=31 && runCatching { android.speech.SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)
        SpeechInput.PROVIDER -> false
    }
    LaunchedEffect(options) { VoicePreferences.save(context,options) }
    DisposableEffect(voice,owner){val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_PAUSE) voice.stop()}
        owner.lifecycle.addObserver(observer);onDispose{owner.lifecycle.removeObserver(observer);voice.close()}}
    Card(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=4.dp).testTag("voice-panel")) { Column(Modifier.fillMaxWidth().heightIn(max=maxHeight).verticalScroll(rememberScrollState()).padding(12.dp).testTag("voice-options"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            VoiceActivity(state,Modifier.size(56.dp))
            Column(Modifier.weight(1f).padding(horizontal=12.dp)) {
                Text(if(options.mode==VoiceMode.LIVE) "Live voice" else "Voice message",style=MaterialTheme.typography.titleMedium)
                Text(state.phase,modifier=Modifier.testTag("voice-phase"),style=MaterialTheme.typography.bodyMedium)
                if(state.busy) Text("${state.elapsedSeconds/60}:${(state.elapsedSeconds%60).toString().padStart(2,'0')} · ${state.completedTurns} replies",style=MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick={showSettings=!showSettings}){Icon(Icons.Default.Tune,if(showSettings) "Hide voice settings" else "Voice settings")}
            IconButton(onClick={voice.stop();onClose()}){Icon(Icons.Default.Close,"End and close voice")}
        }
        (state.error ?: error)?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        if(!state.busy) FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            VoiceMode.entries.forEach{mode->FilterChip(options.mode==mode,{options=options.copy(mode=mode)},label={Text(mode.label)},enabled=enabled)}
        }
        if(options.captions) {
            state.lastHeard?.let{Text("You · $it",style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("voice-user-caption"))}
            val generating=chatState.current?.messages?.lastOrNull()?.takeIf{it.role=="assistant"}?.text
            val caption=when(state.phase) {
                "Thinking" -> generating?.takeIf{it.isNotBlank()}
                "Speaking","Preparing voice" -> state.caption
                else -> state.lastReply?.let(::spokenReply)
            }
            caption?.takeIf{it.isNotBlank()}?.let{Text(it,style=MaterialTheme.typography.bodyLarge,modifier=Modifier.testTag("voice-reply-caption"))}
        }
        if(state.phase=="Listening") {
            LinearProgressIndicator(progress={state.inputLevel.coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().testTag("voice-input-level"))
            Text(if(state.speechDetected) "I hear you · pause to send" else "Listening · speak when ready",style=MaterialTheme.typography.bodySmall)
        }
        if(state.active) FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick={voice.setMuted(!state.muted)},modifier=Modifier.testTag("voice-mute")){Text(if(state.muted) "Unmute" else "Mute mic")}
            OutlinedButton(enabled=state.phase in setOf("Transcribing","Thinking","Preparing voice","Speaking"),onClick={voice.interrupt()},modifier=Modifier.testTag("voice-interrupt")){Text("Interrupt")}
        }
        Button(enabled=!importing && (state.busy || (!missingRecognition && !missingVoice && !missingProvider && (options.network || offlineInputReady))),onClick={if(state.busy) voice.stop() else {
            testPending=false
            if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) start() else permission.launch(Manifest.permission.RECORD_AUDIO)
        }},modifier=Modifier.fillMaxWidth().testTag("voice-start-stop")){Text(if(state.busy) "End conversation" else if(options.mode==VoiceMode.LIVE) "Start live" else "Start one turn")}
        Text(if(options.network) "Network audio allowed · ${options.input.label}" else "Offline audio · ${options.input.label}",style=MaterialTheme.typography.labelSmall)
        if(!offlineInputReady && !options.network) Text("Install an offline speech pack in Voice settings to get started.",color=MaterialTheme.colorScheme.error)

        if(showSettings) {
        Row(verticalAlignment=Alignment.CenterVertically){Text("Audio conversation captions",Modifier.weight(1f));Switch(options.captions,{options=options.copy(captions=it)},enabled=enabled)}
        Text("Live mode listens again after each reply, for up to 10 replies or five minutes. Tap Interrupt to stop a reply and speak. Mute releases capture; End releases the session. Automatic acoustic interruption is not yet qualified.",style=MaterialTheme.typography.bodySmall)
        VoiceChoice("Recognition",options.input.name,SpeechInput.entries.map{it.name to it.label},enabled){options=options.copy(input=SpeechInput.valueOf(it))}
        VoiceChoice("Voice reply",options.output.name,SpeechOutput.entries.map{it.name to it.label},enabled){options=options.copy(output=SpeechOutput.valueOf(it),style=VoiceStyle.NATURAL)}
        Row{Text("Allow network audio",Modifier.weight(1f));Switch(options.network,{options=options.copy(network=it)},enabled=enabled,modifier=Modifier.testTag("voice-network"))}
        Text("Off means no online voice request or network Android voice. Android's guaranteed on-device recognizer needs supported Android 12+. On Android 11 or earlier, choose an installed offline recognition pack for offline input.")
        if(options.input==SpeechInput.OFFLINE_MODEL) VoiceChoice("Offline recognition pack",options.sttPack,packs.filter{it.kind==SpeechKind.WHISPER_STT}.map{it.id to "${it.name} · ${it.language}"},enabled){options=options.copy(sttPack=it)}
        if(options.output==SpeechOutput.OFFLINE_MODEL) VoiceChoice("Offline voice pack",options.ttsPack,packs.filter{it.kind==SpeechKind.PIPER_TTS}.map{it.id to "${it.name} · ${it.language}"},enabled){options=options.copy(ttsPack=it)}
        if(missingRecognition) Text("No Whisper recognition pack selected. Import a compatible ZIP below, or choose Android recognizer / online speech.",color=MaterialTheme.colorScheme.error)
        if(missingVoice) Text("No Piper voice pack selected. Import a compatible ZIP below, or choose an installed Android voice / online speech.",color=MaterialTheme.colorScheme.error)
        if(missingProvider) Text("Choose an enabled OpenAI-format speech provider, allow network audio, and enter the speech model ID for each online direction.",color=MaterialTheme.colorScheme.error)
        if(options.output==SpeechOutput.ANDROID) VoiceChoice("Installed voice",options.androidVoice.orEmpty(),state.voices.filter{options.network || !it.isNetworkConnectionRequired}.map{it.name to "${it.name} · ${it.locale.displayName} · ${if(it.isNetworkConnectionRequired) "network" else "offline"}"},enabled){options=options.copy(androidVoice=it)}
        if(options.input==SpeechInput.PROVIDER || options.output==SpeechOutput.PROVIDER) {
            VoiceChoice("Speech provider",options.providerId,profiles.filter{it.enabled && it.protocol in setOf(OnlineProtocol.OPENAI,OnlineProtocol.OPENAI_COMPATIBLE)}.map{it.id to it.name},enabled){options=options.copy(providerId=it)}
            Text("Microphone audio goes to the selected HTTPS provider for recognition; reply text goes there for synthesis. Configure credentials in Online providers. This can still use your offline chat LLM. Provider fees and supported models vary.")
            if(options.input==SpeechInput.PROVIDER) OutlinedTextField(options.sttModel,{if(it.length<=160)options=options.copy(sttModel=it)},label={Text("Recognition model ID")},singleLine=true,enabled=enabled)
            if(options.output==SpeechOutput.PROVIDER) {
                OutlinedTextField(options.ttsModel,{if(it.length<=160)options=options.copy(ttsModel=it)},label={Text("Speech synthesis model ID")},singleLine=true,enabled=enabled)
                OutlinedTextField(options.providerVoice,{if(it.length<=80)options=options.copy(providerVoice=it)},label={Text("Provider voice ID")},singleLine=true,enabled=enabled)
            }
        }
        if(options.output==SpeechOutput.ANDROID) {
            Text("Android pitch/rate presets describe playback styles, not a person's identity, age or gender.")
            FlowRow{VoiceStyle.entries.forEach{value->FilterChip(options.style==value,{options=options.copy(style=value)},enabled=enabled,label={Text(value.label)})}}
        } else Text("Voice packs and provider voice IDs determine the speaker. Pitch presets are available for Android voices only; Piper and this provider adapter do not implement them.")
        OutlinedTextField(previewText,{ if(it.length<=200) previewText=it },Modifier.fillMaxWidth(),label={Text("Voice sample text")},minLines=2,maxLines=3,enabled=enabled)
        OutlinedButton(enabled=enabled && !missingVoice && !missingProvider && previewText.isNotBlank(),onClick={voice.configure(options);voice.preview(previewText)},modifier=Modifier.testTag("voice-preview")){Text("Play real voice sample")}
        OutlinedButton(enabled=enabled && recognitionReady,onClick={testPending=true;if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED){voice.configure(options);voice.testRecognition()}else permission.launch(Manifest.permission.RECORD_AUDIO)},modifier=Modifier.testTag("voice-recognition-test")){Text("Test recognition only")}
        state.testTranscript?.let{Text("Heard: $it",style=MaterialTheme.typography.bodyMedium)}
        HorizontalDivider();Text("Offline speech models",style=MaterialTheme.typography.titleSmall)
        Text("Import a verified Meshlit voice ZIP from local storage or another device. Whisper recognition and Piper synthesis packs are separate from GGUF chat models; no pack is silently downloaded. This build supports English Whisper and Piper. The ZIP needs a Meshlit manifest with checksums; an arbitrary model ZIP will fail verification.")
        // Documents providers assign different MIME types to ZIP files. Accept the
        // picker result and let VoiceModels validate the manifest and every entry.
        OutlinedButton(enabled=enabled,onClick={importer.launch("*/*")}){Text(if(importing) "Verifying voice pack…" else "Import offline voice pack")}
        packs.forEach{pack->Row{Text("${pack.name}\n${pack.kind} · ${pack.license}",Modifier.weight(1f));TextButton(enabled=enabled,onClick={scope.launch{importing=true;try{models.remove(pack.id)}catch(e:Exception){error=e.message}finally{importing=false}}}){Text("Remove")}}}
        }
        if(showSettings) Text("Captions follow synthesized phrases, not word timestamps. PCM activity uses measured audio; Android voices expose playback state only. Cloud realtime speech requires a separately qualified adapter.")
    } }
}
