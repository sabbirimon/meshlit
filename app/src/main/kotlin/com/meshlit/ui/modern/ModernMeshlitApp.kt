package com.meshlit.ui.modern

import com.meshlit.workspace.richtext.ReplyBlock

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.meshlit.ui.modern.workspaceStringResource as stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlit.ui.phone.*
import com.meshlit.R
import com.meshlit.chat.*
import com.meshlit.core.inference.*
import com.meshlit.core.inference.models.ReasoningEffort
import com.meshlit.core.inference.models.supportedEfforts
import com.meshlit.di.koinInject
import com.meshlit.ui.theme.ChatTokens as T
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay

private enum class ModelIndicator(val label: String, val dark: Color, val light: Color) {
    Standby("Standby", Color(0xFFFFA254), Color(0xFFAC5000)),
    Working("Generating", Color(0xFF55DB89), Color(0xFF16783F)),
    Loading("Loading", Color(0xFF71B7FF), Color(0xFF216AC0)),
    Starting("Starting", Color(0xFF71B7FF), Color(0xFF216AC0)),
    Configured("Configured", Color(0xFF59CBDA), Color(0xFF0C7583)),
    Tool("Using tool", Color(0xFFBE9AFF), Color(0xFF7543B7)),
    Approval("Awaiting approval", Color(0xFFF2D466), Color(0xFF856200)),
    Unavailable("Unavailable", Color(0xFFFF8585), Color(0xFFB13236)),
    Failed("Request failed", Color(0xFFFF8585), Color(0xFFB13236)),
    Unloaded("Not loaded", Color(0xFFA6ADB5), Color(0xFF646D77))
}

/** Shared modern navigation for both APK flavors; earlier sources are inventory references only. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernMeshlitApp() {
    val chats=koinInject<ChatController>()
    val state by chats.state.collectAsStateWithLifecycle()
    val library=koinInject<com.meshlit.models.ModelLibrary>()
    val entries by library.models.collectAsStateWithLifecycle()
    var showCapabilities by remember{mutableStateOf(false)}
    var showChatOptions by remember{mutableStateOf(false)}
    var chatMenu by remember{mutableStateOf(false)}
    var exportChat by remember { mutableStateOf<ChatConversation?>(null) }
    var deleteChatId by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope=rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val conversation = exportChat
        exportChat = null
        if (uri != null && conversation != null) {
                scope.launch {
                    val result = withContext(Dispatchers.IO) { runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { stream ->
                            stream.write(Json { prettyPrint = true }.encodeToString(conversation).toByteArray(Charsets.UTF_8))
                        } ?: error("Cannot open the selected file")
                    } }
                    android.widget.Toast.makeText(context,
                        if (result.isSuccess) "Full conversation exported" else "Export failed: ${result.exceptionOrNull()?.message}",
                        android.widget.Toast.LENGTH_LONG).show()
                }
        }
    }
    var showReadableExport by rememberSaveable { mutableStateOf(false) }
    var exportConversationId by rememberSaveable { mutableStateOf<String?>(null) }
    var exportRevision by rememberSaveable { mutableLongStateOf(0L) }
    var readableFormat by rememberSaveable { mutableStateOf(ConversationExport.MARKDOWN) }
    val readableLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val snapshot = state.conversations.find { it.id == exportConversationId && it.updatedAt == exportRevision }
        val format = readableFormat
        exportConversationId = null
        if (uri != null) scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                val conversation = requireNotNull(snapshot) { "Chat changed. Select it again to export." }
                val text = requireNotNull(format.render(conversation.messages)) { "No matching messages to export" }
                requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "Cannot open selected file" }
                    .bufferedWriter(Charsets.UTF_8).use { it.write(text) }
            } }
            android.widget.Toast.makeText(context, result.fold({ "Chat export saved" }, { "Export failed: ${it.message}" }),
                android.widget.Toast.LENGTH_LONG).show()
        }
    }
    if (showReadableExport) {
        val current = state.current
        val messages = current?.messages.orEmpty()
        val hasCode = remember(messages) { ConversationExport.CODE.render(messages) != null }
        AlertDialog(onDismissRequest = { showReadableExport = false }, title = { Text("Export current chat") },
            text = { Column {
                Text("Choose what to save in a readable file.")
                ConversationExport.entries.forEach { format ->
                    TextButton(enabled = current != null && !state.running && (format != ConversationExport.CODE || hasCode), onClick = {
                        if (current != null) {
                            readableFormat = format
                            exportConversationId = current.id
                            exportRevision = current.updatedAt
                            showReadableExport = false
                            readableLauncher.launch("Meshlit-chat-${current.id.take(8)}.${format.suffix}")
                        }
                    }) { Text(format.label) }
                }
                if (!hasCode) Text("Code export is available when a reply contains a fenced code block.")
            } }, confirmButton = { TextButton(onClick = { showReadableExport = false }) { Text("Close") } })
    }
    var showGlobalSearch by remember{mutableStateOf(false)}
    var showChatSearch by rememberSaveable{mutableStateOf(false)}
    var chatSearchQuery by rememberSaveable{mutableStateOf("")}
    var chatSearchMessage by remember{mutableStateOf<String?>(null)}
        val providers=koinInject<com.meshlit.providers.OnlineProviders>()
    val profiles by providers.profiles.collectAsStateWithLifecycle()
    val selectedProfile=profiles.firstOrNull{it.id==state.current?.options?.onlineProfileId}
    val coordinator=koinInject<InferenceCoordinator>()
    val runtime by coordinator.state.collectAsStateWithLifecycle()
    val pendingTool by chats.toolApprovals.pending.collectAsStateWithLifecycle()
    pendingTool?.let { review -> AlertDialog(onDismissRequest={chats.toolApprovals.answer(review.id,false)},
        title={Text("Allow ${review.tool}?")},text={Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("One call with these exact arguments. Existing device, account and OS permissions still apply.")
            SelectionContainer{Text(review.arguments,style=MaterialTheme.typography.bodySmall,fontFamily=FontFamily.Monospace)}
            Text("No response within 60 seconds denies this call.",style=MaterialTheme.typography.labelSmall)
        }},confirmButton={TextButton(onClick={chats.toolApprovals.answer(review.id,true)}){Text("Allow once")}},
        dismissButton={TextButton(onClick={chats.toolApprovals.answer(review.id,false)}){Text("Deny")}}) }
    val routes=koinInject<com.meshlit.routing.ModelRoutes>()
    val savedRoutes by routes.routes.collectAsStateWithLifecycle()
    val options=state.current?.options ?: ChatOptions()
    val loaded=coordinator.loadedModel()
    val localEntry=activeLibraryModel(entries,loaded,(runtime as? CoordinatorState.Loading)?.modelPath)
    val hostPolicy=options.colibriMode!="OFF" && options.onlineProfileId==null && options.routeId==null && !options.usesLocalTools
    val localSelected=options.onlineProfileId==null && options.routeId==null && !hostPolicy
    val modelTitle=when {
        options.onlineProfileId!=null -> selectedProfile?.model?.ifBlank{selectedProfile.name} ?: "Choose provider"
        options.routeId!=null -> savedRoutes.firstOrNull{it.id==options.routeId}?.name ?: if(options.routeId=="__auto__") "Automatic model route" else "Choose model route"
        hostPolicy -> "Colibri host"
        else -> modelDisplayName(localEntry?.name ?: loaded?.modelName.orEmpty())
    }
    val indicator = when {
        pendingTool != null -> ModelIndicator.Approval
        state.running && state.activeTool != null -> ModelIndicator.Tool
        localSelected && runtime is CoordinatorState.Loading -> ModelIndicator.Loading
        localSelected && runtime is CoordinatorState.Starting -> ModelIndicator.Starting
        state.running || (localSelected && runtime is CoordinatorState.Generating) -> ModelIndicator.Working
        state.error != null -> ModelIndicator.Failed
        localSelected && runtime is CoordinatorState.Error -> ModelIndicator.Unavailable
        localSelected && runtime is CoordinatorState.Ready -> ModelIndicator.Standby
        localSelected -> ModelIndicator.Unloaded
        options.onlineProfileId != null && selectedProfile == null -> ModelIndicator.Unavailable
        options.routeId != null && options.routeId != "__auto__" && savedRoutes.none { it.id == options.routeId } -> ModelIndicator.Unavailable
        else -> ModelIndicator.Configured
    }
    val sourceDetail = when {
        options.onlineProfileId != null -> selectedProfile?.name ?: "Model API"
        options.routeId != null -> "Model route"
        hostPolicy -> "Host policy · ${options.colibriMode}"
        else -> loaded?.contextSize?.takeIf { it > 0 }?.let { "$it ctx" }
            ?: localEntry?.metadata?.maxContext?.let { "$it model max · active ctx unknown" } ?: "Context unreported"
    }
    val modelStatus = "${indicator.label} · $sourceDetail"
    if(showCapabilities) ModelCapabilitiesDialog(if(localSelected)localEntry else null,modelTitle,
        runtimeInfo=if(localSelected)loaded else null,sourceLabel=modelStatus){showCapabilities=false}
    val drawer=rememberDrawerState(DrawerValue.Closed)
    var tab by rememberSaveable { mutableStateOf("chat") }
    var primaryDetail by rememberSaveable(tab) { mutableStateOf(false) }
    var previousHub by rememberSaveable { mutableStateOf("chat") }
    val chatPhotoState=remember{mutableStateOf<android.net.Uri?>(null)}
    val chatPhotoProviderState=remember{mutableStateOf("")}
    val chatDraftState=rememberSaveable{mutableStateOf("")}
    val chatPhotoTurnState=remember{mutableStateOf<String?>(null)}
    var attachmentChatId by remember{mutableStateOf(state.selectedId)}
    val chatCapturePathState=remember{mutableStateOf<String?>(null)}
    val chatCaptureUriState=remember{mutableStateOf<android.net.Uri?>(null)}
    LaunchedEffect(state.selectedId) {
        if(attachmentChatId!=null && attachmentChatId!=state.selectedId && !state.running) {
            chatPhotoState.value=null
            chatCapturePathState.value?.let{java.io.File(it).delete()}
            chatCapturePathState.value=null;chatCaptureUriState.value=null;chatPhotoTurnState.value=null
        }
        chatDraftState.value=state.current?.draft.orEmpty()
        attachmentChatId=state.selectedId
    }
    var closing by remember { mutableStateOf(false) }
    val closingAlpha by animateFloatAsState(if(closing) 1f else 0f,animationSpec=tween(260),label="close-overlay")
    var settingsDestination by remember { mutableStateOf<String?>(null) }
    var settingsOrigin by rememberSaveable { mutableStateOf("chat") }
    if(!com.meshlit.BuildProfile.coreCandidate) com.meshlit.permissions.FirstLaunchPermissionSetup{tab="permissions"}
    LaunchedEffect(chats) { chats.ready.await() }
    BackHandler(tab!="chat"){if(primaryDetail)primaryDetail=false else {tab=if(tab in setOf("settings","agents","devices","models")) "chat" else previousHub}}
    BackHandler(tab=="chat" && !closing){closing=true}
    LaunchedEffect(closing){if(closing){delay(360);(context as? android.app.Activity)?.finish()}}
    val config=com.meshlit.ui.theme.LocalMeshlitThemeConfig.current
    val glass=config.surfaceStyle==com.meshlit.ui.theme.SurfaceStyle.GLASS
    val colors=MaterialTheme.colorScheme
    val openMenu:()->Unit={scope.launch{drawer.open()}}
    var mediaKind by rememberSaveable { mutableStateOf("vision") }
    var researchOpen by rememberSaveable { mutableStateOf(false) }
    var providerPreset by rememberSaveable {mutableStateOf<String?>(null)}
    fun select(id:String) {
        providerPreset=when(id){"setup-gemini"->"GEMINI";"setup-chatgpt"->"CHATGPT";else->null}
        if (id == "research-topic" && !com.meshlit.BuildProfile.coreCandidate) { researchOpen=true; return }
        val destination=when(id) {
            "setup-gemini","setup-chatgpt" -> "providers"
            "create-image","create-video" -> {mediaKind=id.removePrefix("create-");"media"}
            "research-topic" -> "search"
            else -> id
        }
        if(tab in setOf("chat","settings","agents","devices","models")) previousHub=tab
        if(id in WorkspaceDestinations.settingsIds && tab!="settings" && tab !in WorkspaceDestinations.settingsIds) settingsOrigin=tab
        tab=if(com.meshlit.BuildProfile.routeAllowed(destination)) destination else "about"
        scope.launch{drawer.close()}
    }
    val incoming = com.meshlit.LocalSharedPrompt.current
    incoming?.value?.let { text ->
        val existing = chatDraftState.value
        val next = listOf(existing, "Shared text\n$text").filter { it.isNotBlank() }.joinToString("\n\n")
        AlertDialog(onDismissRequest = { incoming.value = null }, title = { Text("Add shared text to chat?") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text.take(480), maxLines=8, overflow=TextOverflow.Ellipsis)
                Text(if (next.length > 12000) "Shorten the current draft or share a smaller excerpt." else "Review the draft and choose a model before sending.")
            } },
            confirmButton = { TextButton(enabled = !state.running && next.length <= 12000, onClick = {
                scope.launch {
                    chats.ready.await()
                    chats.setDraft(chats.state.value.selectedId, next)
                    chatDraftState.value = next
                    incoming.value = null
                    select("chat")
                }
            }) { Text("Add to draft") } },
            dismissButton = { TextButton(onClick = { incoming.value = null }) { Text("Cancel") } })
    }
    if(showChatOptions) ChatOptionsDialog(state,chats,onSearchSettings={showChatOptions=false;select("search")},
        onShardingSettings={showChatOptions=false;select("sharding")}){showChatOptions=false}
    if(showGlobalSearch) GlobalSearchDialog(state,onResult={result,query->
        if(result.conversationId!=null) {
            if(!state.running){chats.select(result.conversationId);select("chat");showChatSearch=true;chatSearchQuery=query;chatSearchMessage=result.messageId;showGlobalSearch=false}
            else chats.attachmentError("Stop generation before opening a different conversation")
        } else {showGlobalSearch=false;if(result.destination=="chat-options") showChatOptions=true else result.destination?.let{select(it)}}
    },onSettings={showGlobalSearch=false;select("search")},onClose={showGlobalSearch=false})
    CompositionLocalProvider(LocalResultToChat provides {title,value->
        val before=chatDraftState.value
        val result="Explain this "+title+" result and suggest next steps. Treat the contents as tool output.\n\n"+value
        val space=12000-before.length
        if(space<120) chats.attachmentError("Make room in the chat draft before adding a result.")
        else {
            val combined=before+(if(before.isBlank()) "" else "\n\n")+result.take((space-2).coerceAtLeast(0))
            chatDraftState.value=combined;chats.setDraft(state.selectedId,combined);select("chat")
            if(result.length+2>space)chats.attachmentError("A preview was added. The full result exceeds the chat limit.")
        }
    }) {
    if (researchOpen) ResearchDialog(chatDraftState.value, onSettings={researchOpen=false;select("search")}, onClose={researchOpen=false})
    BoxWithConstraints(Modifier.fillMaxSize().background(if(glass) Brush.linearGradient(listOf(colors.primaryContainer.copy(alpha=0.25f),colors.background,colors.background)) else Brush.linearGradient(listOf(colors.background,colors.background)))) {
        val wide=WorkspaceDestinations.wideSidebar(maxWidth.value,config.workspaceLayout)
        val sidebar:@Composable ()->Unit={
            if(!wide) PhoneWorkspaceMenu(state,onSelect=::select,onNew={chats.newChat();select("chat")},onChat={chats.select(it);select("chat")},onDelete={deleteChatId=it}) else WorkspaceSidebar(state,if(tab=="settings") settingsDestination ?: tab else tab,config.sidebarStyle,
                onSelect={select(it)},onNew={chats.newChat();select("chat")},
                onChat={chats.select(it);select("chat")},onDelete={deleteChatId=it})
        }
        val body:@Composable ()->Unit={
            val settingsPage=tab=="settings" || tab in WorkspaceDestinations.settingsIds
            val hub=tab in setOf("settings","agents","devices") && !primaryDetail
            val ownHeader=settingsPage && !hub || tab in setOf("media","vision","voice")
            Scaffold(containerColor=if(glass) Color.Transparent else colors.background,
                contentWindowInsets=if(ownHeader) WindowInsets(0,0,0,0) else ScaffoldDefaults.contentWindowInsets,
                bottomBar={Column {
                    ActiveCpuModeWarning{select("acceleration")}
                    if(!wide && tab in setOf("models","devices","agents","settings") && WindowInsets.ime.getBottom(LocalDensity.current)==0 &&
                        (!settingsPage || settingsDestination==null || settingsDestination==tab)) {
                        WorkspaceDock(tab,onSelect={select(it)})
                    }
                }},
                topBar={if(!ownHeader) CenterAlignedTopAppBar(expandedHeight=60.dp,
                    title={if(tab=="chat") Column(Modifier.heightIn(min=T.touch).clickable(onClickLabel="Choose model",onClick={if(localSelected)select("models") else showChatOptions=true}).testTag("chat-model-header"),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).background(if (colors.background.luminance() < 0.5f) indicator.dark else indicator.light,androidx.compose.foundation.shape.CircleShape).semantics{contentDescription=modelStatus});Spacer(Modifier.width(7.dp))
                            Text(modelTitle,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleMedium,
                                modifier=Modifier.weight(1f,fill=false).testTag("chat-model-name"))
                            Icon(Icons.Default.ExpandMore,null,Modifier.size(16.dp),tint=colors.onSurfaceVariant)
                        }
                        Text(modelStatus,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall,
                            color=colors.onSurfaceVariant,modifier=Modifier.testTag("chat-model-status"))
                    } else Text(WorkspaceDestinations.all.firstOrNull{it.id==tab}?.title ?: "Meshlit",maxLines=1,
                        overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleMedium)},
                    navigationIcon={if(!wide) IconButton(onClick=openMenu){Icon(Icons.Default.Menu,stringResource(R.string.modern_menu))}},
                    actions={if(tab!="chat")IconButton(onClick={showGlobalSearch=true}){Icon(Icons.Default.Search,"Search all of Meshlit",Modifier.size(20.dp))}
                    if(tab=="chat") {
                        IconButton(enabled=!state.running,onClick={scope.launch{chats.ready.await();chats.newChat();android.widget.Toast.makeText(context,"New chat",android.widget.Toast.LENGTH_SHORT).show()}}){Icon(Icons.Default.EditNote,stringResource(R.string.modern_new_chat),Modifier.size(20.dp))}
                        IconButton(onClick={chatMenu=true}){Icon(Icons.Default.MoreVert,"Chat menu",Modifier.size(20.dp))}
                        DropdownMenu(chatMenu,{chatMenu=false}) {
                            DropdownMenuItem(text={Text("Models and imports")},onClick={chatMenu=false;select("models")})
                            DropdownMenuItem(text={Text("Search all chats and settings")},onClick={chatMenu=false;showGlobalSearch=true})
                            DropdownMenuItem(text={Text("Search this chat")},onClick={chatMenu=false;showChatSearch=true;chatSearchMessage=null})
                            DropdownMenuItem(text={Text(if (options.showReplyActivity) "Hide reply activity" else "Show reply activity")}, enabled=!state.running, onClick={
                                chatMenu=false
                                runCatching { chats.setOptions(options.copy(showReplyActivity=!options.showReplyActivity)) }
                                    .onFailure { chats.attachmentError(it.message ?: "Could not update reply activity") }
                            })
                            DropdownMenuItem(text={Text("Conversation and token settings")},onClick={chatMenu=false;showChatOptions=true})
                            DropdownMenuItem(text={Text("Model details")},onClick={chatMenu=false;showCapabilities=true})
                            DropdownMenuItem(text={Text("Export current chat…")},enabled=state.current?.messages?.isNotEmpty()==true && !state.running,onClick={
                                chatMenu=false;showReadableExport=true
                            })
                            DropdownMenuItem(text={Text("Export full conversation")},enabled=state.current!=null && !state.running,onClick={
                                chatMenu=false;exportChat=state.current?.copy(draft="")
                                exportLauncher.launch("Meshlit-chat-${state.current?.id?.take(8) ?: "export"}.json")
                            })
                            DropdownMenuItem(text={Text("Delete this chat")},enabled=state.current!=null && !state.running,onClick={
                                chatMenu=false;deleteChatId=state.selectedId
                            })
                        }
                    }},
                    colors=TopAppBarDefaults.topAppBarColors(containerColor=colors.background))})
                 { padding ->
                Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),contentAlignment=Alignment.TopCenter) {
                    key(tab) {when(tab) {
                        "models" -> PhoneModelPicker()
                        "devices","agents","settings" -> if(!primaryDetail) PhoneWorkspacePage(tab,::select){primaryDetail=true}
                            else if(tab=="devices") ModernNetworkScreen(onBack={primaryDetail=false},onDashboard={select("device-dashboard")}, initialPage="app")
                            else ModernSettingsScreen(initialDestination=tab,onExit={primaryDetail=false})
                        "providers" -> OnlineProvidersScreen(onBack={select(previousHub)},initialProvider=providerPreset)
                        "monitor" -> ModernMonitorScreen()
                        "media" -> MediaGenerationScreen(initialKind=mediaKind,initialPrompt=chatDraftState.value,returnMediaToChat=mediaKind in setOf("image","video","speech"),onAddToChat={file->chats.addMediaToChat(file);select("chat")},onBack={select("chat")})
                        "vision" -> MediaGenerationScreen(onBack={select("chat")},initialKind="vision")
                        "voice" -> HandsFreeVoiceDialog(chats){select("chat")}
                        "structured" -> ModernStructuredScreen()
                        else -> if(settingsPage) ModernSettingsScreen(initialDestination=tab.takeUnless{it=="settings"},onExit=if(tab!="settings") ({tab=previousHub;settingsDestination=null}) else null,onMenu=openMenu,onDestinationChanged={destination->
                            settingsDestination=destination
                            if(destination==null && tab!="settings") tab="settings"
                        }) else ModernChatScreen(state,runtime,chats,coordinator.engineTag,showChatSearch,chatSearchQuery,chatSearchMessage,chatPhotoState,chatPhotoProviderState,chatDraftState,chatPhotoTurnState,chatCapturePathState,chatCaptureUriState,onSearchQuery={chatSearchQuery=it;chatSearchMessage=null},onCloseSearch={showChatSearch=false;chatSearchMessage=null},onModels={select("models")},onMedia={select("media")},onVision={select("vision")},onFiles={select("files")},onProviders={select("providers")},onOptions={showChatOptions=true},onConnections={select("device-dashboard")},onTool=::select)
                    }}
                }
            }
        }
        if(wide) Row(Modifier.fillMaxSize().testTag("workspace-wide-shell")) {
            Surface(Modifier.width(T.wideSidebar).fillMaxHeight().windowInsetsPadding(WindowInsets.safeDrawing),color=colors.surface,
                border=androidx.compose.foundation.BorderStroke(1.dp,colors.outlineVariant.copy(alpha=0.4f))) {sidebar()}
            Box(Modifier.weight(1f).fillMaxHeight()){body()}
        } else ModalNavigationDrawer(drawerState=drawer,gesturesEnabled=drawer.isOpen,drawerContent={
            ModalDrawerSheet(Modifier.width(minOf(T.sidebar,maxWidth-T.touch)),drawerContainerColor=colors.surface) {sidebar()}
        }){body()}
    }
    }
    val selectedForDelete = state.conversations.firstOrNull { it.id == deleteChatId }
    if (selectedForDelete != null) AlertDialog(onDismissRequest={deleteChatId=null},
        title={Text("Delete chat?")},
        text={Text("Delete “${selectedForDelete.title}” and its messages from this device?")},
        confirmButton={TextButton(onClick={chats.delete(selectedForDelete.id);deleteChatId=null}){Text("Delete")}},
        dismissButton={TextButton(onClick={deleteChatId=null}){Text("Cancel")}})
    if(closing) Surface(Modifier.fillMaxSize().graphicsLayer(alpha=closingAlpha).testTag("closing-meshlit"),color=MaterialTheme.colorScheme.background) {
        Box(contentAlignment=Alignment.Center) {Column(horizontalAlignment=Alignment.CenterHorizontally) {
            CircularProgressIndicator(Modifier.size(52.dp),strokeWidth=2.dp)
            Spacer(Modifier.height(18.dp))
            Text("Closing Meshlit",style=MaterialTheme.typography.titleMedium)
            Text("Active background sessions keep their own notification and Stop control.",style=MaterialTheme.typography.bodySmall,textAlign=TextAlign.Center,modifier=Modifier.padding(24.dp))
        }}
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModernChatScreen(state:ChatState,runtime:CoordinatorState,controller:ChatController,engineTag:String,searchOpen:Boolean,searchQuery:String,searchMessageId:String?,photoState:MutableState<android.net.Uri?>,photoProviderState:MutableState<String>,draftState:MutableState<String>,photoTurnState:MutableState<String?>,capturePathState:MutableState<String?>,captureUriState:MutableState<android.net.Uri?>,onSearchQuery:(String)->Unit,onCloseSearch:()->Unit,onModels:()->Unit,onMedia:()->Unit,onVision:()->Unit,onFiles:()->Unit,onProviders:()->Unit,onOptions:()->Unit,onConnections:()->Unit,onTool:(String)->Unit) {
    var draft by draftState
    fun editDraft(value:String){draft=value;controller.setDraft(state.selectedId,value)}
    var showVoice by remember{mutableStateOf(false)}
    val providers=koinInject<com.meshlit.providers.OnlineProviders>()
    val profiles by providers.profiles.collectAsStateWithLifecycle()
    val visionHost=koinInject<com.meshlit.media.LocalVisionHost>()
    val importedVision by visionHost.model.collectAsStateWithLifecycle()
    val offlineVision=importedVision.takeIf{visionHost.available()}
    val visionProfiles=profiles.filter{it.enabled && it.model.isNotBlank() && it.protocol in setOf(com.meshlit.core.inference.models.OnlineProtocol.OPENAI,com.meshlit.core.inference.models.OnlineProtocol.OPENAI_COMPATIBLE)}
    val chatRoutes=koinInject<com.meshlit.routing.ModelRoutes>()
    val effortRoutes by chatRoutes.routes.collectAsStateWithLifecycle()
    var photo by photoState
    var photoProvider by photoProviderState
    var photoTurnId by photoTurnState
    LaunchedEffect(photo,visionProfiles) {if(photo!=null && photoProvider!=LOCAL_PHOTO_ROUTE && visionProfiles.none{it.id==photoProvider}) photoProvider=LOCAL_PHOTO_ROUTE}
    val context=LocalContext.current
    var captureFilePath by capturePathState
    var captureUri by captureUriState
    fun clearPhoto(){photo=null;captureFilePath?.let{java.io.File(it).delete()};captureFilePath=null;captureUri=null}
    fun selectPhoto(uri:android.net.Uri){clearPhoto();photo=uri;showVoice=false;photoProvider=LOCAL_PHOTO_ROUTE}
    val photoPicker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->if(uri!=null) selectPhoto(uri)}
    val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()){saved->if(saved && captureUri!=null){photo=captureUri;showVoice=false;photoProvider=LOCAL_PHOTO_ROUTE}else{clearPhoto();controller.attachmentError("Photo capture was cancelled or failed")}}
    LaunchedEffect(state.running,photoTurnId) {
        val turn=photoTurnId
        if(turn!=null && !state.running) {
            if(state.current?.messages?.any{it.id==turn && it.text.isNotBlank()}==true) {clearPhoto();draft=""}
            photoTurnId=null
        }
    }
    val list=rememberLazyListState()
    val scope=rememberCoroutineScope()
    val messages=state.current?.messages.orEmpty()
    val rows=mutableListOf<ReplyTimelineRow>()
    messages.forEach {message->
        if(message.role=="user") rows+=ReplyTimelineRow("${message.id}:user",message,kind="user")
        else {
            val document=key(message.id){rememberReply(message.text)}
            rows+=ReplyTimelineRow("${message.id}:header",message,kind="header")
            if (state.current?.options?.showReplyActivity != false && (message.activity.isNotEmpty() || message.text.isNotBlank()))
                rows += ReplyTimelineRow("${message.id}:activity",message,kind="activity")
            document.blocks.forEachIndexed{index,block->rows+=ReplyTimelineRow("${message.id}:$index",message,block,"block")}
            if(message.deviceOffer!=null || message.deviceReceipt!=null) rows+=ReplyTimelineRow("${message.id}:devices",message,kind="devices")
            if(message.text.isNotEmpty() || message.webSources.isNotEmpty()) rows+=ReplyTimelineRow("${message.id}:actions",message,kind="actions")
        }
    }
    var searchResults by remember(state.selectedId){mutableStateOf(com.meshlit.search.SectionMatches())}
    val searchableRows=rows.map{row->if(row.kind in setOf("user","block"))row.block?.let(::replyBlockText) ?: row.message.text else ""}
    LaunchedEffect(searchOpen,searchQuery,searchableRows) {
        searchResults=com.meshlit.search.SectionMatches()
        if(searchOpen) {
            if(searchQuery.isNotBlank())delay(120)
            searchResults=withContext(Dispatchers.Default){com.meshlit.search.searchTextSections(searchableRows,searchQuery)}
        }
    }
    val matches=searchResults.positions.map{it.section}
    var matchPosition by remember(state.selectedId,searchQuery){mutableIntStateOf(0)}
    var searchReader by remember{mutableStateOf<String?>(null)}
    searchReader?.let{ReplyReader(it,initialQuery=searchQuery){searchReader=null}}
    LaunchedEffect(searchOpen,searchQuery,searchMessageId,matches,state.selectedId) {
        if(searchOpen) {
            val anchor=searchMessageId?.let{id->rows.indexOfFirst{it.message.id==id}.takeIf{it>=0}}
            val target=anchor ?: matches.getOrNull(0)
            if(target!=null) list.scrollToItem(target)
        }
    }
    var followLatest by rememberChatFollowing(list,state.selectedId,searchOpen)
    val nearBottom by remember { derivedStateOf { !list.canScrollForward } }
    val composerFocus=remember{FocusRequester()};val keyboard=LocalSoftwareKeyboardController.current
    fun replyTo(message:ChatMessage) {
        val quoted=quotedReplyDraft(message,draft)
        if(quoted==null)controller.attachmentError("Your draft is full. Shorten it before adding a quoted reply.")
        else {editDraft(quoted);composerFocus.requestFocus();keyboard?.show()}
    }
    val chosenVision=visionProfiles.firstOrNull{it.id==photoProvider}
    val chosenOffline=offlineVision.takeIf{photoProvider==LOCAL_PHOTO_ROUTE}
    val hasChatModel=runtime is CoordinatorState.Ready || state.current?.options?.onlineProfileId!=null || state.current?.options?.routeId!=null
    val nativeConnection=photo==null && state.current?.options?.let{it.agentToolsActive && deviceConnectionChatIntent(draft,it)!=null}==true
    var customize by remember { mutableStateOf(false) }
    var catalogQuery by remember { mutableStateOf<String?>(null) }
    var toolArguments by remember { mutableStateOf<String?>(null) }
    var pickedPin by remember { mutableStateOf<ComposerPin?>(null) }
    val pins=savedComposerPins(context)
    val resultToChat=LocalResultToChat.current
    if(customize) ComposerToolPicker(pickedPin,toolArguments ?: draft,catalogQuery,onClose={customize=false;pickedPin=null;catalogQuery=null;toolArguments=null},onRoute={if(it=="chat-options")onOptions() else onTool(it)},onDraft={resultToChat("connected tool",it)})
    val commandText = draft.trim()
    val toolRequest = if(photo==null && !com.meshlit.BuildProfile.coreCandidate)
        Regex("""(?i)^(?:/tools?(?:\s+|$)|find a tool to\s+)([\s\S]*)$""").matchEntire(commandText)?.groupValues?.get(1) else null
    val key = toolRequest.orEmpty().substringBefore(' ')
    val pinIntent = if(toolRequest!=null && commandText.startsWith("/tool ",true))
        pins.find { it.command==key } ?: when {
            key.startsWith("mcp:") && key.matches(Regex("mcp:[A-Za-z0-9_.-]{1,128}")) -> ComposerPin("",key.removePrefix("mcp:"),key)
            else -> WorkspaceDestinations.all.firstOrNull { it.id==key && com.meshlit.BuildProfile.routeAllowed(it.id) }?.let { ComposerPin("",it.title,it.id) }
        } else null
    var shieldAction by rememberSaveable { mutableStateOf<String?>(null) }
    val shieldIntent=if(photo==null && !com.meshlit.BuildProfile.coreCandidate) shieldChatAction(draft) else null
    if(shieldAction!=null) {
        androidx.activity.compose.BackHandler { shieldAction=null }
        CompositionLocalProvider(LocalResultToChat provides {title,value->shieldAction=null;resultToChat(title,value)}) {
            ShirudoScreen(back={shieldAction=null},action=shieldAction!!)
        }
        return
    }
    val namedTask=remember(draft){if(!com.meshlit.BuildProfile.coreCandidate)namedModelTask(draft) else null}
    val grants=remember(context){NamedModelGrants(context)}
    var reviewNamed by remember(state.selectedId){mutableStateOf<NamedModelTask?>(null)}
    fun sendNamed(request:NamedModelTask,profile:com.meshlit.core.inference.models.OnlineProfile,automatic:Boolean){
        if(namedModelTask(draft)!=request)return
        if(request.task.isEmpty()){
            if(controller.state.value.current==null)controller.newChat()
        }else{
            followLatest=true
            if(controller.sendNamedTask(draft,NamedModelDispatch(request,profile))==null)return
        }
        controller.state.value.selectedId?.let{id->if(automatic)grants.allow(id,request,profile) else grants.revoke(id,request)}
        editDraft("");reviewNamed=null
        if(request.task.isEmpty())android.widget.Toast.makeText(context,
            if(automatic)"Auto-send enabled for ${request.provider.label}" else "${profile.name} is ready. Ask ${request.provider.label} a question.",
            android.widget.Toast.LENGTH_SHORT).show()
    }
    reviewNamed?.let {request->
        val prior=profiles.singleOrNull{grants.allows(state.selectedId,request,it)}
        NamedTaskDialog(request,profiles,prior,onChoose={profile,automatic->sendNamed(request,profile,automatic)},
            onSetup={reviewNamed=null;onTool("setup-"+request.provider.name.lowercase())},onRevoke={grants.revoke(state.selectedId,request);reviewNamed=null},
            onDismiss={reviewNamed=null})
    }
    val installedRequest = remember(draft, photo) {
        if (photo == null && !com.meshlit.BuildProfile.coreCandidate && !com.meshlit.BuildConfig.PLAY_REVIEW)
            installedAppRequest(draft) else null
    }
    var reviewApp by remember(state.selectedId) { mutableStateOf<AppRequest?>(null) }
    reviewApp?.let { request ->
        InstalledAppDialog(request, controller=controller, onInstall={reviewApp=null;onTool("permissions")}, onDismiss = { reviewApp = null },
            onDone = { reviewApp = null; editDraft("") },
            onControls = { reviewApp = null; editDraft(""); onTool("openclaw") },
            onChat = { reviewApp = null; if (controller.send(draft) != null) editDraft("") })
    }
    val canSend=!state.running && draft.length<=(if(photo!=null) 4000 else 12000) && if(photo!=null) (namedTask!=null || chosenVision!=null || chosenOffline!=null) else (hasChatModel || installedRequest!=null || nativeConnection || shieldIntent!=null || toolRequest!=null || namedTask!=null) && draft.isNotBlank()
    val send={ if(canSend && namedTask!=null && photo!=null) {
        controller.attachmentError(NAMED_TASK_ATTACHMENT_ERROR)
    } else if(canSend && namedTask!=null) {
        keyboard?.hide()
        val prior=profiles.singleOrNull{grants.allows(state.selectedId,namedTask,it)}
        if(prior!=null && namedTask.task.isNotEmpty())sendNamed(namedTask,prior,true) else reviewNamed=namedTask
    } else if(canSend && installedRequest!=null && !(nativeConnection && draft.trim().startsWith("connect",true) && !draft.contains("app",true))) {
        keyboard?.hide();reviewApp=installedRequest
    } else if(canSend && toolRequest!=null) {
        editDraft("");keyboard?.hide();pickedPin=pinIntent;catalogQuery=if(pinIntent==null)toolRequest else null
        toolArguments=if(pinIntent!=null)toolRequest.substringAfter(' ',"") else ""
        customize=true
    } else if(canSend && shieldIntent!=null) {editDraft("");keyboard?.hide();shieldAction=shieldIntent} else if(canSend) {
        followLatest=true
        val selectedPhoto=photo
        val sent=controller.send(if(selectedPhoto!=null) draft.ifBlank{"Describe this photo accurately, including any readable text."} else draft,
            selectedPhoto?.let{PhotoChatRequest(it,chosenVision,chosenOffline)})
        if(sent!=null) {if(selectedPhoto!=null) photoTurnId=sent else draft=""}
    } }
    val colors=MaterialTheme.colorScheme
    val keyboardVisible=WindowInsets.ime.getBottom(LocalDensity.current)>0
    BoxWithConstraints(Modifier.fillMaxSize().imePadding().background(colors.background)) {
    val panelHeight=(maxHeight*0.4f).coerceAtMost(260.dp)
    Column(Modifier.fillMaxHeight().widthIn(max=T.contentMax).fillMaxWidth().align(Alignment.TopCenter)) {
        if(searchOpen) Column(Modifier.fillMaxWidth().padding(horizontal=T.large).testTag("chat-search-bar")) {
            OutlinedTextField(searchQuery,{onSearchQuery(it.take(200))},Modifier.fillMaxWidth().testTag("chat-search-query"),singleLine=true,label={Text("Search this conversation")},
                leadingIcon={Icon(Icons.Default.Search,null)},trailingIcon={IconButton(onClick=onCloseSearch){Icon(Icons.Default.Close,"Close chat search")}})
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(if(matches.isEmpty()) "0 matches · ${searchResults.characters} text characters" else "${matchPosition.coerceAtMost(matches.lastIndex)+1} / ${searchResults.total} matches · ${searchResults.characters} text characters${if(searchResults.total>matches.size)" · first ${matches.size} navigable" else ""}",Modifier.weight(1f),style=MaterialTheme.typography.labelSmall)
                fun move(direction:Int){matchPosition=Math.floorMod(matchPosition+direction,matches.size);followLatest=false;scope.launch{list.animateScrollToItem(matches[matchPosition])}}
                IconButton(enabled=matches.isNotEmpty(),onClick={move(-1)},modifier=Modifier.testTag("chat-search-previous")){Icon(Icons.Default.KeyboardArrowUp,"Previous chat match")}
                IconButton(enabled=matches.isNotEmpty(),onClick={move(1)},modifier=Modifier.testTag("chat-search-next")){Icon(Icons.Default.KeyboardArrowDown,"Next chat match")}
            }
            val occurrence=searchResults.positions.getOrNull(matchPosition.coerceAtMost(matches.lastIndex).coerceAtLeast(0))
            occurrence?.let { hit -> rows.getOrNull(hit.section)?.let { row ->
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("Message ${messages.indexOf(row.message)+1} · line ${hit.occurrence.line}, column ${hit.occurrence.column} in this text block",Modifier.weight(1f),style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant)
                    TextButton(onClick={searchReader=row.message.text}){Text("Full message")}
                }
            } }
        }
        if(messages.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth()) {
            if(!keyboardVisible) Box(Modifier.align(Alignment.Center)) { PhoneGreeting(stringResource(R.string.modern_greeting)) {
                if(!hasChatModel) {
                    Spacer(Modifier.height(20.dp))
                    when(runtime) {
                        is CoordinatorState.Starting,is CoordinatorState.Loading -> {
                            LinearProgressIndicator(Modifier.width(160.dp))
                            Spacer(Modifier.height(T.medium))
                            Text("Your model is loading. You can browse chats and settings while it gets ready.",style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant,textAlign=TextAlign.Center)
                            TextButton(onClick=onModels){Text("View model status")}
                        }
                        else -> {
                            Text("Choose a model to start chatting.",style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)
                            TextButton(onClick=onModels){Text("Choose model")}
                        }
                    }
                }
            } }
        } else Box(Modifier.weight(1f)) {
            LazyColumn(state=list,modifier=Modifier.fillMaxSize().testTag("chat-timeline"),contentPadding=PaddingValues(horizontal=22.dp,vertical=24.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                items(rows,key={it.key}) { row -> val message=row.message
                    when(row.kind) {
                        "user" -> Row(Modifier.fillMaxWidth().padding(top=T.small),horizontalArrangement=Arrangement.End,verticalAlignment=Alignment.CenterVertically) {
                            val currentMatch=searchOpen && row.key==rows.getOrNull(matches.getOrNull(matchPosition) ?: -1)?.key
                            Surface(shape=RoundedCornerShape(8.dp),color=if(currentMatch) colors.secondaryContainer else colors.surfaceContainerHigh,modifier=Modifier.weight(1f,fill=false).widthIn(max=T.bubbleMax).then(if(currentMatch) Modifier.testTag("chat-search-current-match") else Modifier)) {
                                Column(Modifier.clickable(onClickLabel="Quote your message",enabled=!state.running){replyTo(message)}.padding(horizontal=16.dp,vertical=12.dp)) {
                                    MessageClock(message.createdAtMs)
                                    message.attachmentNote?.let{Text(it,style=MaterialTheme.typography.labelMedium,color=colors.onSurfaceVariant);Spacer(Modifier.height(6.dp))}
                                    SelectionContainer {Text(message.text,style=MaterialTheme.typography.bodyLarge.copy(fontSize=18.sp,lineHeight=28.sp),color=colors.onSurface)}
                                }
                            }
                        }
                        "header" -> Row(Modifier.fillMaxWidth().padding(top=20.dp),verticalAlignment=Alignment.CenterVertically) {
                            Icon(androidx.compose.ui.res.painterResource(R.drawable.meshlit_mark),null,
                                Modifier.size(24.dp),tint=androidx.compose.ui.graphics.Color.Unspecified)
                            Spacer(Modifier.width(10.dp))
                            Text(message.source ?: "Meshlit",style=MaterialTheme.typography.titleSmall,color=colors.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f,fill=false))
                            MessageClock(message.createdAtMs,Modifier.padding(start=8.dp))
                            if(state.running && message.id==controller.generationId && chatToolActivity(state.activeTool)!=null) ChatToolActivityIndicator(checkNotNull(state.activeTool))
                            else if(message.text.isEmpty() && state.running && message.id==controller.generationId) {
                                Spacer(Modifier.width(T.medium));LinearProgressIndicator(Modifier.width(60.dp))
                                Text(state.operationLabel,Modifier.padding(start=8.dp).weight(1f),style=MaterialTheme.typography.labelSmall,maxLines=2)
                            }
                            else message.status?.let{Text(it,Modifier.weight(1f).padding(start=8.dp),style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)}
                        }
                        "activity" -> Column {
                            var expanded by rememberSaveable(message.id) { mutableStateOf(false) }
                            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide activity" else "View activity") }
                            if (expanded) {
                                for (step in message.activity) Text(step, style = MaterialTheme.typography.bodyMedium)
                                message.reasoningSummary?.let { Text("Model summary", style = MaterialTheme.typography.titleSmall); Text(it) }
                                if (message.text.isNotBlank()) TextButton(enabled = !state.running, onClick = {
                                    val question = "Briefly explain the assumptions and evidence behind this answer."
                                    val next = quotedReplyDraft(message, listOf(draft, question).filter { it.isNotBlank() }.joinToString("\n\n"))
                                    if (next != null) { editDraft(next); composerFocus.requestFocus(); keyboard?.show() }
                                    else controller.attachmentError("Shorten the draft to add an explanation request.")
                                }) { Text("Explain answer") }
                            }
                        }
                        "block" -> ReplyContent(checkNotNull(row.block),if(searchOpen && row.key==rows.getOrNull(matches.getOrNull(matchPosition) ?: -1)?.key) Modifier.background(colors.secondaryContainer.copy(alpha=0.45f)).testTag("chat-search-current-match") else Modifier)
                        "devices" -> ChatDeviceConnections(message.deviceOffer,message.deviceReceipt,!state.running && state.current?.options?.connectionTools==true && state.current?.options?.agentToolsActive==true,controller::connectDevice,{controller.send("connect devices")},onConnections)
                        "actions" -> Column {
                            message.mediaFilename?.let { DownloadGeneratedFile(it) }
                            ChatWebSources(message.id,message.webSources)
                            if (message.knowledgeSources.isNotEmpty()) {
                                var showSources by remember(message.id) { mutableStateOf(false) }
                                TextButton(onClick = { showSources = !showSources }) { Text("${message.knowledgeSources.size} reference excerpts provided") }
                                if (showSources) message.knowledgeSources.forEach { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp)) }
                            }
                            if(message.text.isNotEmpty()) MessageActions(message.text,message.usage,showMetrics=state.current?.options?.showTokenStats!=false,onReply=if(state.running)null else ({replyTo(message)}))
                        }
                    }
                }
                item(key="chat-end-anchor"){Spacer(Modifier.fillMaxWidth().height(1.dp).testTag("chat-end-anchor"))}
            }
            val turnIndices=rows.indices.filter{rows[it].kind=="user" || rows[it].kind=="header"}
            if(turnIndices.size>=2){
                val activeTurn by remember(list,turnIndices) { derivedStateOf {
                    turnIndices.indexOfLast{it<=list.firstVisibleItemIndex}.coerceAtLeast(0)
                } }
                com.meshlit.ui.phone.PhoneTurnAnchors(turnIndices.map { index ->
                    val point = rows[index]
                    val author = if (point.kind == "user") "You" else "Meshlit"
                    "$author · " + point.message.text.take(120).ifBlank { if (state.running) "In progress" else "Attachment" }
                },activeTurn,Modifier.align(Alignment.CenterStart)){turn->
                    followLatest=false
                    scope.launch{list.scrollToItem(turnIndices[turn])}
                }
            }
            if(!nearBottom && rows.isNotEmpty()) SmallFloatingActionButton(onClick={followLatest=true;list.requestScrollToItem(rows.size)},
                modifier=Modifier.align(Alignment.BottomCenter).padding(T.small).testTag("chat-latest")) {Icon(Icons.Default.ArrowDownward,stringResource(R.string.modern_latest))}
        }
        if(showVoice && !com.meshlit.BuildProfile.coreCandidate) VoiceConversationOverlay(controller){showVoice=false}
        if(photo!=null) ChatPhotoAttachment(checkNotNull(photo),visionProfiles,photoProvider,!state.running,offlineVision?.label,
            onProvider={photoProvider=it},onRemove={clearPhoto()},onSetup=onProviders,onLocalSetup=onMedia,maxHeight=panelHeight)
        val access=state.current?.options ?: com.meshlit.chat.ChatOptions()
        val accessLabel=when {
            access.hostedAgentToolsActive -> "Hosted agent · reviewed web search · ask each time"
            access.onlineProfileId!=null -> "Online provider selected · chat tools off"
            access.routeId!=null -> "Model route selected · chat tools off"
            access.colibriMode!="OFF" -> "Host policy ${access.colibriMode} · chat tools off"
            access.executionMode==com.meshlit.chat.ChatExecutionMode.CHAT -> "Chat mode · tool execution off · ${access.toolCount} selected tool slots"
            access.agentToolsActive -> "Agent mode · Web request ${if(access.webTools) "on" else "off"} · Phone request ${if(access.phoneTools) "on" else "off"} · Device connection requests ${if(access.connectionTools) "on" else "off"} · ${access.toolCount} tool slots"
            else -> "Local chat · Web and phone tools off"
        }
        if(state.running && !keyboardVisible && state.current?.options?.showTokenStats!=false) TokenSpeedIndicator(state,onOptions)
        if(photo!=null && draft.length>4000) Text("Photo questions support up to 4,000 characters.",Modifier.padding(horizontal=16.dp),color=colors.error,style=MaterialTheme.typography.bodySmall)
        state.error?.let { Box(Modifier.padding(horizontal=T.large)) {ErrorCard(it){controller.clearError()}} }
        if(runtime is CoordinatorState.Error) Box(Modifier.padding(horizontal=T.large)) {ErrorCard(runtime.message,onModels)}
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=16.dp,vertical=10.dp)) {
            PhonePrompt(draft,{editDraft(it.take(12000))},if(photo!=null) "Ask about this photo" else stringResource(R.string.modern_message),
                !state.running,composerFocus,send,attachments={
                    key(state.selectedId){ChatAttachmentActions(!state.running,onText={text->if(draft.length+text.length+2<=12000) editDraft(if(draft.isBlank()) text else "$draft\n\n$text") else controller.attachmentError("Attachment plus draft exceeds 12000 characters")},onSelectedImage={selectPhoto(it)},onPhoto={photoPicker.launch("image/*")},onCamera={try{
                        clearPhoto()
                        val dir=java.io.File(context.cacheDir,"exports/captures").apply{mkdirs();listFiles().orEmpty().filter{it.name.startsWith("chat-") && System.currentTimeMillis()-it.lastModified()>86_400_000}.forEach{it.delete()}}
                        val target=java.io.File(dir,"chat-${java.util.UUID.randomUUID()}.jpg")
                        captureFilePath=target.path
                        captureUri=androidx.core.content.FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",target)
                        camera.launch(checkNotNull(captureUri))
                    }catch(_:Exception){clearPhoto();controller.attachmentError("No compatible camera capture app is available")}},onFiles=onFiles,onMedia=onMedia,onVision=onVision,onOptions=onOptions,onTool={id->
                        if(id=="reset-model-autosend"){
                            grants.clear(state.selectedId)
                            android.widget.Toast.makeText(context,"Model auto-send off for this chat",android.widget.Toast.LENGTH_SHORT).show()
                        }else onTool(id)
                    },onCustomize={pin->pickedPin=pin;customize=true},pins=pins,options=access,onUpdate={next->
                        runCatching {controller.setOptions(next)}.onFailure {controller.attachmentError(it.message ?: "Could not change tool access")}
                    })}
                },tools={
                        ChatAccessControl(access,!state.running,onMode={mode->
                            runCatching{controller.setOptions(access.copy(approvalMode=mode))}.onFailure{controller.attachmentError(it.message ?: "Approval choice could not be applied")}
                        },onTools=onOptions,onExecutionMode={mode->
                            runCatching{controller.setOptions(access.copy(executionMode=mode))}.onFailure{controller.attachmentError(it.message ?: "Conversation mode could not be applied")}
                        },modifier=Modifier.semantics{contentDescription="Access: $accessLabel"},extraOptions={
                        ReasoningEffortButton(access.reasoningEffort,chatSupportedEfforts(access,profiles,effortRoutes),!state.running && photo==null) { effort ->
                            runCatching{controller.setOptions(access.copy(reasoningEffort=effort))}.onFailure{controller.attachmentError(it.message ?: "Effort choice could not be applied")}
                        }
                        })
                },action={
                        if(draft.isBlank() && !state.running && photo==null && !com.meshlit.BuildProfile.coreCandidate) TouchIcon(if(showVoice) Icons.Default.Keyboard else Icons.Default.Mic,if(showVoice) "Close voice controls" else "Open voice controls"){showVoice=!showVoice}
                        else IconButton(enabled=state.running || canSend,onClick={if(state.running) controller.stop() else send()},
                            modifier=Modifier.testTag("chat-send-stop")) {
                            Icon(if(state.running) Icons.Default.Stop else Icons.Default.ArrowUpward,
                                if(state.running) "Stop response" else if(photo!=null) "Send photo" else stringResource(R.string.modern_send))
                        }
                })
            if(!keyboardVisible && photo!=null) Text(if(photoProvider==LOCAL_PHOTO_ROUTE)"Photo → on-device vision" else "Photo → selected vision provider",Modifier.fillMaxWidth().padding(top=4.dp),textAlign=TextAlign.Center,
                style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    }
}


@Composable
private fun MessageClock(timestamp:Long?,modifier:Modifier=Modifier){
    if(timestamp==null)return
    val context=androidx.compose.ui.platform.LocalContext.current
    Text(android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(timestamp)),modifier,
        style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
}

private data class ReplyTimelineRow(val key:String,val message:ChatMessage,val block:ReplyBlock?=null,val kind:String)
