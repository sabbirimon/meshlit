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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlit.R
import com.meshlit.chat.*
import com.meshlit.core.inference.*
import com.meshlit.di.koinInject
import com.meshlit.ui.theme.ChatTokens as T
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay

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
    var showGlobalSearch by remember{mutableStateOf(false)}
    var showChatSearch by rememberSaveable{mutableStateOf(false)}
    var chatSearchQuery by rememberSaveable{mutableStateOf("")}
    var chatSearchMessage by remember{mutableStateOf<String?>(null)}
        val providers=koinInject<com.meshlit.providers.OnlineProviders>()
    val profiles by providers.profiles.collectAsStateWithLifecycle()
    val selectedProfile=profiles.firstOrNull{it.id==state.current?.options?.onlineProfileId}
    val coordinator=koinInject<InferenceCoordinator>()
    val runtime by coordinator.state.collectAsStateWithLifecycle()
    if(showCapabilities) ModelCapabilitiesDialog(if(state.current?.options?.onlineProfileId==null && state.current?.options?.routeId==null) entries.firstOrNull{it.path==coordinator.loadedModel()?.modelPath} else null,"Selected provider or route"){showCapabilities=false}
    val drawer=rememberDrawerState(DrawerValue.Closed)
    var tab by rememberSaveable { mutableStateOf("chat") }
    val chatPhotoState=remember{mutableStateOf<android.net.Uri?>(null)}
    val chatPhotoProviderState=remember{mutableStateOf("")}
    val chatDraftState=rememberSaveable{mutableStateOf("")}
    val chatPhotoTurnState=remember{mutableStateOf<String?>(null)}
    var attachmentChatId by remember{mutableStateOf(state.selectedId)}
    val chatCapturePathState=remember{mutableStateOf<String?>(null)}
    val chatCaptureUriState=remember{mutableStateOf<android.net.Uri?>(null)}
    LaunchedEffect(state.selectedId) {
        if(attachmentChatId!=null && attachmentChatId!=state.selectedId && !state.running) {
            chatPhotoState.value=null;chatDraftState.value=""
            chatCapturePathState.value?.let{java.io.File(it).delete()}
            chatCapturePathState.value=null;chatCaptureUriState.value=null;chatPhotoTurnState.value=null
        }
        attachmentChatId=state.selectedId
    }
    var closing by remember { mutableStateOf(false) }
    val closingAlpha by animateFloatAsState(if(closing) 1f else 0f,animationSpec=tween(260),label="close-overlay")
    var settingsDestination by remember { mutableStateOf<String?>(null) }
    var settingsOrigin by rememberSaveable { mutableStateOf("chat") }
    if(!com.meshlit.BuildProfile.coreCandidate) com.meshlit.permissions.FirstLaunchPermissionSetup{tab="permissions"}
    LaunchedEffect(chats) { chats.ready.await() }
    BackHandler(tab!="chat"){tab="chat"}
    BackHandler(tab=="chat" && !closing){closing=true}
    LaunchedEffect(closing){if(closing){delay(360);(context as? android.app.Activity)?.finish()}}
    val config=com.meshlit.ui.theme.LocalMeshlitThemeConfig.current
    val glass=config.surfaceStyle==com.meshlit.ui.theme.SurfaceStyle.GLASS
    val colors=MaterialTheme.colorScheme
    val openMenu:()->Unit={scope.launch{drawer.open()}}
    fun select(id:String) {
        if(id in WorkspaceDestinations.settingsIds && tab!="settings" && tab !in WorkspaceDestinations.settingsIds) settingsOrigin=tab
        tab=if(com.meshlit.BuildProfile.routeAllowed(id)) id else "about"
        scope.launch{drawer.close()}
    }
    if(showChatOptions) ChatOptionsDialog(state,chats,onSearchSettings={showChatOptions=false;select("search")},
        onShardingSettings={showChatOptions=false;select("sharding")}){showChatOptions=false}
    if(showGlobalSearch) GlobalSearchDialog(state,onResult={result,query->
        if(result.conversationId!=null) {
            if(!state.running){chats.select(result.conversationId);select("chat");showChatSearch=true;chatSearchQuery=query;chatSearchMessage=result.messageId;showGlobalSearch=false}
            else chats.attachmentError("Stop generation before opening a different conversation")
        } else {showGlobalSearch=false;if(result.destination=="chat-options") showChatOptions=true else result.destination?.let{select(it)}}
    },onSettings={showGlobalSearch=false;select("search")},onClose={showGlobalSearch=false})
    BoxWithConstraints(Modifier.fillMaxSize().background(if(glass) Brush.linearGradient(listOf(colors.primaryContainer.copy(alpha=0.25f),colors.background,colors.background)) else Brush.linearGradient(listOf(colors.background,colors.background)))) {
        val wide=WorkspaceDestinations.wideSidebar(maxWidth.value,config.workspaceLayout)
        val sidebar:@Composable ()->Unit={
            WorkspaceSidebar(state,if(tab=="settings") settingsDestination ?: tab else tab,config.sidebarStyle,
                onSelect={select(it)},onNew={chats.newChat();select("chat")},
                onChat={chats.select(it);select("chat")},onDelete={deleteChatId=it})
        }
        val body:@Composable ()->Unit={
            val settingsPage=tab=="settings" || tab in WorkspaceDestinations.settingsIds
            val ownHeader=settingsPage || tab in setOf("media","vision","voice")
            Scaffold(containerColor=if(glass) Color.Transparent else colors.background,
                contentWindowInsets=if(ownHeader) WindowInsets(0,0,0,0) else ScaffoldDefaults.contentWindowInsets,
                bottomBar={
                    if(!wide && tab in setOf("chat","models","devices","agents","settings") && WindowInsets.ime.getBottom(LocalDensity.current)==0 &&
                        (!settingsPage || settingsDestination==null || settingsDestination==tab)) {
                        WorkspaceDock(tab,onSelect={select(it)})
                    }
                },
                topBar={if(!ownHeader) TopAppBar(expandedHeight=48.dp,
                    title={if(tab=="chat") Column(Modifier.heightIn(min=T.touch).clickable(onClickLabel="Choose model",onClick={select("models")}),verticalArrangement=Arrangement.Center) {
                        Text("Meshlit",maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Text(if(state.current?.options?.colibriMode!="OFF" && state.current?.options?.colibriMode!=null) "Colibri policy · ${state.current?.options?.colibriMode}" else if(state.current?.options?.routeId!=null) "Model router" else if(state.current?.options?.onlineProfileId!=null)
                                selectedProfile?.let{"${it.name} · ${it.model}"} ?: "Online model" else when(val current=runtime) {
                                    is CoordinatorState.Ready -> current.model.modelName
                                    is CoordinatorState.Loading -> stringResource(R.string.modern_loading)
                                    is CoordinatorState.Generating -> stringResource(R.string.modern_generating)
                                    CoordinatorState.Starting -> stringResource(R.string.modern_starting)
                                    else -> stringResource(R.string.modern_choose_model)
                                },maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelSmall,
                                color=colors.onSurfaceVariant,modifier=Modifier.weight(1f,fill=false))
                            Icon(Icons.Default.ExpandMore,null,Modifier.size(16.dp),tint=colors.onSurfaceVariant)
                        }
                    } else Text(WorkspaceDestinations.all.firstOrNull{it.id==tab}?.title ?: "Meshlit",maxLines=1,
                        overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleMedium)},
                    navigationIcon={if(!wide) IconButton(onClick=openMenu){Icon(Icons.Default.Menu,stringResource(R.string.modern_menu))}},
                    actions={IconButton(onClick={showGlobalSearch=true}){Icon(Icons.Default.Search,"Search all of Meshlit")}
                    if(tab=="chat") {
                        IconButton(enabled=!state.running,onClick={chats.newChat()}){Icon(Icons.Default.EditNote,stringResource(R.string.modern_new_chat))}
                        IconButton(onClick={chatMenu=true}){Icon(Icons.Default.MoreVert,"Chat menu")}
                        DropdownMenu(chatMenu,{chatMenu=false}) {
                            DropdownMenuItem(text={Text("Search this chat")},onClick={chatMenu=false;showChatSearch=true;chatSearchMessage=null})
                            DropdownMenuItem(text={Text("Conversation and token settings")},onClick={chatMenu=false;showChatOptions=true})
                            DropdownMenuItem(text={Text("Model details")},onClick={chatMenu=false;showCapabilities=true})
                            DropdownMenuItem(text={Text("Export full conversation")},enabled=state.current!=null && !state.running,onClick={
                                chatMenu=false;exportChat=state.current
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
                        "models" -> ModernModelsScreen()
                        "devices" -> ModernNetworkScreen(onBack={select("chat")},
                            onDashboard={select("device-dashboard")}, initialPage="app")
                        "monitor" -> ModernMonitorScreen()
                        "media" -> MediaGenerationScreen(onBack={select("chat")})
                        "vision" -> MediaGenerationScreen(onBack={select("chat")},initialKind="vision")
                        "voice" -> HandsFreeVoiceDialog(chats){select("chat")}
                        "structured" -> ModernStructuredScreen()
                        else -> if(settingsPage) ModernSettingsScreen(initialDestination=tab.takeUnless{it=="settings"},onExit=if(tab!="settings") ({tab=settingsOrigin;settingsDestination=null}) else null,onMenu=openMenu,onDestinationChanged={destination->
                            settingsDestination=destination
                            if(destination==null && tab!="settings") tab="settings"
                        }) else ModernChatScreen(state,runtime,chats,coordinator.engineTag,showChatSearch,chatSearchQuery,chatSearchMessage,chatPhotoState,chatPhotoProviderState,chatDraftState,chatPhotoTurnState,chatCapturePathState,chatCaptureUriState,onSearchQuery={chatSearchQuery=it;chatSearchMessage=null},onCloseSearch={showChatSearch=false;chatSearchMessage=null},onModels={select("models")},onMedia={select("media")},onVision={select("vision")},onFiles={select("files")},onProviders={select("providers")},onOptions={showChatOptions=true})
                    }}
                }
            }
        }
        if(wide) Row(Modifier.fillMaxSize().testTag("workspace-wide-shell")) {
            Surface(Modifier.width(T.wideSidebar).fillMaxHeight().windowInsetsPadding(WindowInsets.safeDrawing),color=colors.surface,
                border=androidx.compose.foundation.BorderStroke(1.dp,colors.outlineVariant.copy(alpha=0.4f))) {sidebar()}
            Box(Modifier.weight(1f).fillMaxHeight()){body()}
        } else ModalNavigationDrawer(drawerState=drawer,drawerContent={
            ModalDrawerSheet(Modifier.width(minOf(T.sidebar,maxWidth-T.touch)),drawerContainerColor=colors.surface) {sidebar()}
        }){body()}
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

@Composable
private fun ModernChatScreen(state:ChatState,runtime:CoordinatorState,controller:ChatController,engineTag:String,searchOpen:Boolean,searchQuery:String,searchMessageId:String?,photoState:MutableState<android.net.Uri?>,photoProviderState:MutableState<String>,draftState:MutableState<String>,photoTurnState:MutableState<String?>,capturePathState:MutableState<String?>,captureUriState:MutableState<android.net.Uri?>,onSearchQuery:(String)->Unit,onCloseSearch:()->Unit,onModels:()->Unit,onMedia:()->Unit,onVision:()->Unit,onFiles:()->Unit,onProviders:()->Unit,onOptions:()->Unit) {
    var draft by draftState
    var showVoice by remember{mutableStateOf(false)}
    val providers=koinInject<com.meshlit.providers.OnlineProviders>()
    val profiles by providers.profiles.collectAsStateWithLifecycle()
    val visionProfiles=profiles.filter{it.enabled && it.model.isNotBlank() && it.protocol in setOf(com.meshlit.core.inference.models.OnlineProtocol.OPENAI,com.meshlit.core.inference.models.OnlineProtocol.OPENAI_COMPATIBLE)}
    var photo by photoState
    var photoProvider by photoProviderState
    var photoTurnId by photoTurnState
    LaunchedEffect(photo,visionProfiles) {if(photo!=null && visionProfiles.none{it.id==photoProvider}) photoProvider=visionProfiles.firstOrNull()?.id.orEmpty()}
    val context=LocalContext.current
    var captureFilePath by capturePathState
    var captureUri by captureUriState
    fun clearPhoto(){photo=null;captureFilePath?.let{java.io.File(it).delete()};captureFilePath=null;captureUri=null}
    fun selectPhoto(uri:android.net.Uri){clearPhoto();photo=uri;showVoice=false;photoProvider=visionProfiles.firstOrNull{it.id==state.current?.options?.onlineProfileId}?.id ?: visionProfiles.firstOrNull()?.id.orEmpty()}
    val photoPicker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->if(uri!=null) selectPhoto(uri)}
    val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()){saved->if(saved && captureUri!=null){photo=captureUri;showVoice=false;photoProvider=visionProfiles.firstOrNull{it.id==state.current?.options?.onlineProfileId}?.id ?: visionProfiles.firstOrNull()?.id.orEmpty()}else{clearPhoto();controller.attachmentError("Photo capture was cancelled or failed")}}
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
            document.blocks.forEachIndexed{index,block->rows+=ReplyTimelineRow("${message.id}:$index",message,block,"block")}
            if(message.text.isNotEmpty()) rows+=ReplyTimelineRow("${message.id}:actions",message,kind="actions")
        }
    }
    val matches=rows.indices.filter{index->val row=rows[index];searchQuery.isNotBlank() && row.kind in setOf("user","block") && (if(row.block!=null) replyBlockText(row.block) else row.message.text).contains(searchQuery,true)}
    var matchPosition by remember(state.selectedId,searchQuery){mutableIntStateOf(0)}
    LaunchedEffect(searchOpen,searchQuery,searchMessageId,rows.size,state.selectedId) {
        if(searchOpen) {
            val anchor=searchMessageId?.let{id->rows.indexOfFirst{it.message.id==id}.takeIf{it>=0}}
            val target=anchor ?: matches.getOrNull(0)
            if(target!=null) list.scrollToItem(target)
        }
    }
    var followLatest by remember(state.selectedId){mutableStateOf(true)}
    val nearBottom by remember { derivedStateOf { !list.canScrollForward } }
    LaunchedEffect(list) {snapshotFlow{list.isScrollInProgress to list.canScrollForward}.collectLatest{(scrolling,forward)->if(scrolling) followLatest=!forward}}
    LaunchedEffect(messages.lastOrNull()?.text?.length,rows.size,state.selectedId) {
        if(rows.isNotEmpty() && followLatest && !searchOpen) list.scrollToItem(rows.lastIndex)
    }
    val chosenVision=visionProfiles.firstOrNull{it.id==photoProvider}
    val hasChatModel=runtime is CoordinatorState.Ready || state.current?.options?.onlineProfileId!=null || state.current?.options?.routeId!=null
    val canSend=!state.running && draft.length<=(if(photo!=null) 4000 else 12000) && if(photo!=null) chosenVision!=null else hasChatModel && draft.isNotBlank()
    val send={ if(canSend) {
        followLatest=true
        val selectedPhoto=photo
        val sent=controller.send(if(selectedPhoto!=null) draft.ifBlank{"Describe this photo accurately, including any readable text."} else draft,
            selectedPhoto?.let{PhotoChatRequest(it,checkNotNull(chosenVision))})
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
                Text(if(matches.isEmpty()) "No matching message blocks" else "${matchPosition.coerceAtMost(matches.lastIndex)+1} / ${matches.size} matching blocks",Modifier.weight(1f),style=MaterialTheme.typography.labelSmall)
                fun move(direction:Int){matchPosition=Math.floorMod(matchPosition+direction,matches.size);followLatest=false;scope.launch{list.animateScrollToItem(matches[matchPosition])}}
                IconButton(enabled=matches.isNotEmpty(),onClick={move(-1)},modifier=Modifier.testTag("chat-search-previous")){Icon(Icons.Default.KeyboardArrowUp,"Previous chat match")}
                IconButton(enabled=matches.isNotEmpty(),onClick={move(1)},modifier=Modifier.testTag("chat-search-next")){Icon(Icons.Default.KeyboardArrowDown,"Next chat match")}
            }
        }
        if(messages.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth()) {
            if(!keyboardVisible) Column(Modifier.align(Alignment.Center).padding(horizontal=T.section),
                horizontalAlignment=Alignment.CenterHorizontally) {
                Icon(Icons.Default.AutoAwesome,null,Modifier.size(32.dp),tint=MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(T.large))
                Text(stringResource(R.string.modern_greeting),style=MaterialTheme.typography.headlineSmall,
                    textAlign=TextAlign.Center,modifier=Modifier.widthIn(max=280.dp))
                Spacer(Modifier.height(20.dp))
                ModelWelcomeSuggestion(onModels)
            }
        } else Box(Modifier.weight(1f)) {
            LazyColumn(state=list,modifier=Modifier.fillMaxSize().testTag("chat-timeline"),contentPadding=PaddingValues(horizontal=T.large,vertical=T.medium),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                items(rows,key={it.key}) { row -> val message=row.message
                    when(row.kind) {
                        "user" -> Row(Modifier.fillMaxWidth().padding(top=T.medium),horizontalArrangement=Arrangement.End) {
                            val currentMatch=searchOpen && row.key==rows.getOrNull(matches.getOrNull(matchPosition) ?: -1)?.key
                            Surface(shape=MaterialTheme.shapes.extraLarge,color=if(currentMatch) colors.secondaryContainer else colors.surfaceContainerHigh,modifier=Modifier.widthIn(max=T.bubbleMax).then(if(currentMatch) Modifier.testTag("chat-search-current-match") else Modifier)) {
                                Column(Modifier.padding(T.large)) {
                                    message.attachmentNote?.let{Text(it,style=MaterialTheme.typography.labelMedium,color=colors.onSurfaceVariant);Spacer(Modifier.height(6.dp))}
                                    SelectionContainer {Text(message.text,style=MaterialTheme.typography.bodyLarge)}
                                }
                            }
                        }
                        "header" -> Row(Modifier.fillMaxWidth().padding(top=T.small),verticalAlignment=Alignment.CenterVertically) {
                            Text(message.source ?: "Meshlit",style=MaterialTheme.typography.labelMedium,color=colors.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f,fill=false))
                            if(message.text.isEmpty() && state.running && message.id==controller.generationId) {Spacer(Modifier.width(T.medium));LinearProgressIndicator(Modifier.width(60.dp))}
                            else if(message.text.isEmpty()) message.status?.let{Text(it,Modifier.weight(1f).padding(start=8.dp),style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)}
                        }
                        "block" -> ReplyContent(checkNotNull(row.block),if(searchOpen && row.key==rows.getOrNull(matches.getOrNull(matchPosition) ?: -1)?.key) Modifier.background(colors.secondaryContainer.copy(alpha=0.45f)).testTag("chat-search-current-match") else Modifier)
                        "actions" -> Column {
                            if (message.knowledgeSources.isNotEmpty()) {
                                var showSources by remember(message.id) { mutableStateOf(false) }
                                TextButton(onClick = { showSources = !showSources }) { Text("${message.knowledgeSources.size} reference excerpts provided") }
                                if (showSources) message.knowledgeSources.forEach { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp)) }
                            }
                            MessageActions(message.text,message.usage)
                        }
                    }
                }
            }
            if(!nearBottom && rows.isNotEmpty()) SmallFloatingActionButton(onClick={followLatest=true;scope.launch{list.animateScrollToItem(rows.lastIndex)}},
                modifier=Modifier.align(Alignment.BottomCenter).padding(T.small)) {Icon(Icons.Default.ArrowDownward,stringResource(R.string.modern_latest))}
        }
        if(showVoice && !com.meshlit.BuildProfile.coreCandidate) HandsFreeVoiceDialog(controller,maxHeight=panelHeight){showVoice=false}
        if(photo!=null) ChatPhotoAttachment(checkNotNull(photo),visionProfiles,photoProvider,!state.running,
            onProvider={photoProvider=it},onRemove={clearPhoto()},onSetup=onProviders,maxHeight=panelHeight)
        val access=state.current?.options ?: com.meshlit.chat.ChatOptions()
        val accessLabel=when {
            access.onlineProfileId!=null -> "Online provider selected · chat tools off"
            access.routeId!=null -> "Model route selected · chat tools off"
            access.colibriMode!="OFF" -> "Host policy ${access.colibriMode} · chat tools off"
            access.usesLocalTools -> "Local chat · Web request ${if(access.webTools) "on" else "off"} · Phone request ${if(access.phoneTools) "on" else "off"} · ${access.toolCount} tool slots"
            else -> "Local chat · Web and phone tools off"
        }
        if(!keyboardVisible && state.current?.options?.showTokenStats!=false) TokenSpeedIndicator(state,onOptions)
        if(photo!=null && draft.length>4000) Text("Photo questions support up to 4,000 characters.",Modifier.padding(horizontal=16.dp),color=colors.error,style=MaterialTheme.typography.bodySmall)
        state.error?.let { Box(Modifier.padding(horizontal=T.large)) {ErrorCard(it){controller.clearError()}} }
        if(runtime is CoordinatorState.Error) Box(Modifier.padding(horizontal=T.large)) {ErrorCard(runtime.message,onModels)}
        Column(Modifier.fillMaxWidth().padding(horizontal=T.large,vertical=if(keyboardVisible) T.small else T.medium)) {
            Surface(shape=RoundedCornerShape(24.dp),color=colors.surfaceContainerLow,
                border=androidx.compose.foundation.BorderStroke(1.dp,colors.outlineVariant.copy(alpha=0.65f))) {
                Column(Modifier.fillMaxWidth().padding(6.dp)) {
                    TextField(value=draft,onValueChange={draft=it.take(12000)},enabled=!state.running,
                        modifier=Modifier.fillMaxWidth().testTag("chat-composer"),
                        placeholder={Text(if(photo!=null) "Ask about this photo…" else stringResource(R.string.modern_message),style=MaterialTheme.typography.bodyLarge)},maxLines=4,
                        keyboardOptions=KeyboardOptions(imeAction=ImeAction.Send),keyboardActions=KeyboardActions(onSend={send()}),
                        colors=TextFieldDefaults.colors(focusedContainerColor=Color.Transparent,unfocusedContainerColor=Color.Transparent,
                            disabledContainerColor=Color.Transparent,focusedIndicatorColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent,disabledIndicatorColor=Color.Transparent))
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    ChatAttachmentActions(!state.running,onText={text->if(draft.length+text.length+2<=12000) draft=if(draft.isBlank()) text else "$draft\n\n$text" else controller.attachmentError("Attachment plus draft exceeds 12000 characters")},onSelectedImage={selectPhoto(it)},onPhoto={photoPicker.launch("image/*")},onCamera={try{
                        clearPhoto()
                        val dir=java.io.File(context.cacheDir,"exports/captures").apply{mkdirs();listFiles().orEmpty().filter{it.name.startsWith("chat-") && System.currentTimeMillis()-it.lastModified()>86_400_000}.forEach{it.delete()}}
                        val target=java.io.File(dir,"chat-${java.util.UUID.randomUUID()}.jpg")
                        captureFilePath=target.path
                        captureUri=androidx.core.content.FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",target)
                        camera.launch(checkNotNull(captureUri))
                    }catch(_:Exception){clearPhoto();controller.attachmentError("No compatible camera capture app is available")}},onFiles=onFiles,onMedia=onMedia,onVision=onVision,onOptions=onOptions)
                        TextButton(onClick=onOptions,modifier=Modifier.weight(1f).testTag("chat-access-indicator").semantics{contentDescription="Access: $accessLabel"},contentPadding=PaddingValues(horizontal=4.dp)) {
                            Icon(if(access.usesLocalTools) Icons.Default.Tune else Icons.Default.Shield,null,Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if(access.usesLocalTools) "Web ${if(access.webTools) "on" else "off"} · Phone ${if(access.phoneTools) "on" else "off"}" else "Tools off",style=MaterialTheme.typography.labelMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                        }
                        if(!com.meshlit.BuildProfile.coreCandidate) IconButton(onClick={showVoice=!showVoice},enabled=!state.running && photo==null){Icon(if(showVoice) Icons.Default.Keyboard else Icons.Default.Mic,if(showVoice) "Close voice controls" else "Open voice controls",Modifier.size(22.dp))}
                        FilledIconButton(enabled=state.running || canSend,onClick={if(state.running) controller.stop() else send()},modifier=Modifier.size(T.touch).testTag("chat-send-stop"),
                            colors=IconButtonDefaults.filledIconButtonColors(containerColor=colors.primary,contentColor=colors.onPrimary)) {
                            Icon(if(state.running) Icons.Default.Stop else Icons.Default.ArrowUpward,
                                if(state.running) "Stop response" else if(photo!=null) "Send photo" else stringResource(R.string.modern_send),Modifier.size(22.dp))
                        }
                    }
                }
            }
            val mode=if(state.current?.options?.colibriMode!="OFF" && state.current?.options?.colibriMode!=null) "Colibri policy · ${state.current?.options?.colibriMode}" else if(state.current?.options?.routeId!=null) "Model router" else if(state.current?.options?.onlineProfileId!=null) "Online provider" else when(engineTag){
                "runanywhere","onnx-ort","llama-native-local" -> "On-device model"
                "llama-rpc-layer" -> "Cluster model"
                else -> "Selected model"
            }
            if(!keyboardVisible) Text(if(photo!=null) "Photo → selected vision provider" else mode,Modifier.fillMaxWidth().padding(top=T.small),textAlign=TextAlign.Center,
                style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    }
}


private data class ReplyTimelineRow(val key:String,val message:ChatMessage,val block:ReplyBlock?=null,val kind:String)
