package com.meshlit.ui.modern

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlit.di.koinInject
import com.meshlit.settings.SettingsRepository
import com.meshlit.ui.theme.*
import com.meshlit.ui.theme.ChatTokens as T
import kotlinx.coroutines.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ModernSettingsScreen(initialDestination:String?=null,onExit:(()->Unit)?=null,onMenu:(()->Unit)?=null,onDestinationChanged:(String?)->Unit={}) {
    var destination by rememberSaveable{mutableStateOf(initialDestination?.takeIf { com.meshlit.BuildProfile.routeAllowed(it) })}
    var query by rememberSaveable{mutableStateOf("")}
    LaunchedEffect(destination){onDestinationChanged(destination)}
    val context=LocalContext.current
    val prefs=remember{context.getSharedPreferences("settings-navigation",0)}
    var advanced by rememberSaveable{mutableStateOf(prefs.getBoolean("advanced",false))}
    var showAllBasic by rememberSaveable { mutableStateOf(false) }
    val repository=koinInject<SettingsRepository>()
    var history by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    fun navigate(next:String) {
        if(next==destination) return
        history=ArrayList(history).apply { add(destination.orEmpty()) }
        destination=next
    }
    fun back() {
        if (history.isEmpty() && initialDestination != null && onExit != null) {
            onExit()
            return
        }
        val previous=history.lastOrNull()
        history=ArrayList(history.dropLast(1))
        destination=previous?.ifEmpty { null }
    }
    val back:()->Unit={back()}
    BackHandler(destination!=null){back()}
    if(destination=="menu-layout"){MenuLayoutScreen(back);return}
    if(destination=="sharding"){ShardingSettingsScreen(back);return}
    if(destination=="radar"){RadarScreen(back,{navigate("scan")},{navigate("meshchat")},{navigate("logs")},{navigate("host-map")},
        {navigate("device-peer:$it")});return}
    if(destination?.startsWith("device-peer:")==true){ModernNetworkScreen(back,{navigate("device-dashboard")},initialPage="app",
        focusPeerNodeId=destination!!.removePrefix("device-peer:"));return}
    if(destination=="host-map"){HostMapScreen(back,{navigate("securityprivacy")},{navigate("devices")});return}
    if(destination=="external-services"){ExternalServiceScreen(back){navigate(it)};return}
    if(destination=="web-bridge"){WebBridgeEnrollmentScreen(back);return}
    if(destination=="device-dashboard"){DeviceManagementScreen(back,{navigate(it)});return}
    if(destination=="scan"){com.meshlit.ui.v2.screens.V2ScanScreen(onOpenQrPairing={navigate("network")},onBack=back);return}
    if(destination=="meshchat"){PeerChatScreen(back);return}
    if(destination=="securityprivacy"){SecurityPrivacyScreen(back,{navigate(it)});return}
    if(destination=="tools-automation"){ToolsAutomationScreen(back,{navigate(it)});return}
    if(destination=="mcp-catalog"){McpCatalogScreen(back,{navigate("gateway")});return}
    if(destination=="huggingface"){HuggingFaceDashboardScreen(back);return}
    if(destination=="rag"){
        val scope=rememberCoroutineScope()
        com.meshlit.ui.screens.settings.RagSettingsScreen(
            initialRagMode=repository.ragModeFlowNow(),initialLoopMode=repository.loopModeFlowNow(),onBack=back,
            onRagModeChange={mode->scope.launch{repository.setRagMode(mode)}},
            onLoopModeChange={mode->scope.launch{repository.setLoopMode(mode)}})
        return
    }
    if(destination=="agent-registry"){AgentRegistryScreen(back,{navigate("agents")},{navigate("mcp-catalog")});return}
    if(destination=="agent-logs"){ModernLogsScreen(back,com.meshlit.core.observability.LogSource.AGENT,"Agent logs");return}
    if(destination=="guardrails"){GuardrailSettingsScreen(back,{navigate("behavior")},{navigate("gateway")});return}
    if(destination=="crypto"){CryptoScreen(back);return}
    if(destination=="gibberlink"){GibberLinkScreen(back);return}
    if(destination=="remotecommands"){RemoteCommandsScreen(back);return}
    if(destination=="p2p"){PeerChatScreen(back);return}
    if(destination=="colibri"){ColibriScreen(back);return}
    if(destination=="search"){SearchAccessScreen(back);return}
    if(destination=="legal"){com.meshlit.legal.LegalDocumentsScreen(back);return}
    if(destination=="personalization"){PersonalizationScreen(back);return}
    if(destination=="behavior"){LocalBehaviorScreen(back,{navigate("models")});return}
    if(destination=="help"){HelpTutorialScreen(back);return}
    if(destination=="cloud"){CloudManagementScreen(back,{navigate("huggingface")});return}
    if(destination=="recovery"){RecoveryScreen(back);return}
    if(destination=="training"){FineTuningScreen(back);return}
    if(destination=="router"){ModelRouterScreen(back);return}
    if(destination=="media"){MediaOptionsScreen(back);return}
    if(destination=="configuration"){ConfigurationScreen(back);return}
    if(destination=="operations"){OperationsScreen(back);return}
    if(destination=="gateway"){GatewayScreen(back);return}
    if(destination=="commands"){ManualCommandsScreen(back);return}
    if(destination=="packages"){LabPackagesScreen(back);return}
    if(destination=="securitylab"){SecurityLabScreen(back,{navigate("runtime")},{navigate("packages")});return}
    if(destination=="providers"){OnlineProvidersScreen(back);return}
    if(destination=="power"){PowerCostsScreen(back,{navigate("cloud")});return}
    if(destination=="external"){ExternalDevicesScreen(back);return}
    if(destination=="agents"){AgentManagementScreen(back,{navigate(it)});return}
    if(destination=="acceleration"){AccelerationScreen(back);return}
    if(destination=="ssh"){SshConnectionsScreen(back);return}
    if(destination=="firewall"){FirewallSettingsScreen(back);return}
    if(destination=="tasks"){TaskManagerScreen(back,{navigate("operations")});return}
    if(destination=="files"){FileManagerScreen(back);return}
    if(destination=="termux"){ExistingTermuxSettings(back);return}
    if(destination=="ide"){com.meshlit.ide.CodeWorkspaceScreen(back);return}
    if(destination=="hyperl"){HyperLScreen(back);return}
    if(destination=="resources"){ResourcesScreen(back);return}
    if(destination=="permissions"){com.meshlit.permissions.PermissionSetupScreen(back);return}
    if(destination=="openclaw"){OpenClawScreen(back);return}
    if(destination=="audit"){AuditTelemetryScreen(back);return}
    if(destination=="logs"){ModernLogsScreen(back);return}
    if(destination=="models"){ModernModelsScreen(back);return}
    if(destination=="identity-recovery"){com.meshlit.ui.v2.screens.RecoveryScreen();return}
    if(destination=="rewards"){com.meshlit.ui.v2.screens.RewardsScreen(back);return}
    if(destination=="devices"){ModernNetworkScreen(back,{navigate("device-dashboard")},initialPage="app");return}
    if(destination=="network"){ModernNetworkScreen(back,{navigate("device-dashboard")},onDevices={navigate("devices")});return}
    if(destination=="peers"){com.meshlit.ui.screens.settings.ForwardingPeersScreen(back);return}
    if(destination=="automation"){com.meshlit.ui.screens.cloud.AndroidAutomationSettingsScreen(repository,back);return}
    if(destination=="hooks"){com.meshlit.ui.screens.settings.HooksScreen(back,{navigate("hook:$it")});return}
    if(destination?.startsWith("hook:")==true){com.meshlit.ui.screens.settings.HookEditorScreen(destination!!.removePrefix("hook:"),back);return}
    Scaffold(topBar={TopAppBar(expandedHeight=48.dp,colors=TopAppBarDefaults.topAppBarColors(containerColor=Color.Transparent),title={Text(SettingsDestinations.all.firstOrNull{it.id==destination}?.title ?: "Settings",style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary)},
        navigationIcon={if(destination!=null || onExit!=null) IconButton(onClick={if(destination!=null) back() else onExit?.invoke()}){
            Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")} else if(onMenu!=null) IconButton(onClick=onMenu){Icon(Icons.Default.Menu,"Menu")}})}){padding ->
        when(destination){
            "appearance" -> AppearanceSettings(Modifier.padding(padding))
            "device" -> Box(Modifier.padding(padding)){com.meshlit.ui.screens.settings.DeviceScreen(back)}
            "monitor" -> Box(Modifier.padding(padding)){ModernMonitorScreen()}
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding).widthIn(max=T.contentMax).testTag("settings-list"),contentPadding=PaddingValues(12.dp),
                verticalArrangement=Arrangement.spacedBy(T.medium)) {
                when(destination){
                    "runtime" -> item{com.meshlit.ui.screens.cloud.RuntimeDashboardCard()}
                    "crawler" -> item{com.meshlit.ui.screens.cloud.CrawlerSettingsCard()}
                    "notifications" -> item{Card{Column(Modifier.padding(T.large)){
                        Text("Notification settings are managed by Android. Downloads, generation and pipeline sessions use separate channels.")
                        Button(onClick={context.startActivity(if(Build.VERSION.SDK_INT>=26) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName) else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.parse("package:${context.packageName}")))}){Text("Open notification settings")}
                    }}}
                    "about" -> item{Card{Column(Modifier.padding(T.large),verticalArrangement=Arrangement.spacedBy(T.small)){
                        Text("Meshlit ${com.meshlit.BuildConfig.VERSION_NAME}",style=MaterialTheme.typography.titleLarge)
                        Text("Created by IMON",style=MaterialTheme.typography.titleMedium)
                        Text("Channel: ${com.meshlit.BuildProfile.name}")
                        Text(if(com.meshlit.BuildProfile.coreCandidate) "Local chat, models and user-selected files. Separate package and storage from Experimental. Device endurance, SDK network privacy and release signing are still qualification gates." else "Local inference: RunAnywhere llama.cpp. Layer execution: optional experimental RPC workers. Linux and Crawl4AI require separately installed companions.")
                        Text("Legacy settings for account tiers, automatic model download, custom GPU layer counts and transport policy are not connected by this menu. They are not presented as functioning switches.")
                        Text("Project and third-party notices are maintained in the repository LICENSE and vendored source notices.")
                    }}}
                    else -> {
                        item {OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().testTag("settings-search"),singleLine=true,placeholder={Text("Search settings")},
                            leadingIcon={Icon(Icons.Default.Search,null)},trailingIcon={if(query.isNotEmpty()) IconButton(onClick={query=""}){Icon(Icons.Default.Close,"Clear search")}},shape=RoundedCornerShape(28.dp))}
                        item {Row(horizontalArrangement=Arrangement.spacedBy(T.small)){
                            FilterChip(!advanced,{advanced=false;prefs.edit().putBoolean("advanced",false).apply()},label={Text("Basic")})
                            FilterChip(advanced,{advanced=true;prefs.edit().putBoolean("advanced",true).apply()},label={Text("Advanced")})
                        }}
                        if(query.isBlank()) item {
                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                                Text(if(advanced) "Advanced tools and integrations" else if(showAllBasic) "All everyday settings" else "Essentials",
                                    Modifier.weight(1f),style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                if(!advanced) TextButton(onClick={showAllBasic=!showAllBasic},modifier=Modifier.testTag("settings-toggle-all")) {
                                    Text(if(showAllBasic) "Show essentials" else "Browse all")
                                }
                            }
                        }
                        val results=if(query.isBlank() && !advanced && !showAllBasic) {
                            val essentials=setOf("models","device-dashboard","agents","securityprivacy","appearance","permissions")
                            SettingsDestinations.all.filter { it.id in essentials }
                        } else SettingsDestinations.search(query,advanced)
                        if(results.isEmpty()) item{Text("No matching settings. Search covers Basic and Advanced.")}
                        items(results,key={it.id}){entry -> Card(shape=RoundedCornerShape(20.dp),onClick={navigate(entry.id)},modifier=Modifier.fillMaxWidth(),
                            colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)){
                            Row(Modifier.padding(horizontal=12.dp,vertical=10.dp),horizontalArrangement=Arrangement.spacedBy(T.medium),verticalAlignment=Alignment.CenterVertically){
                                SettingsIconTile(entry.id)
                                Column(Modifier.weight(1f)){Text(entry.title,style=MaterialTheme.typography.titleMedium);Text(entry.description,
                                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                                Icon(Icons.Default.ChevronRight,null)
                            }
                        }}
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun AppearanceSettings(modifier:Modifier) {
    val settings=koinInject<SettingsRepository>()
    val config by settings.flow.collectAsStateWithLifecycle(MeshlitThemeConfig.Default)
    val scope=rememberCoroutineScope()
    var error by remember{mutableStateOf<String?>(null)}
    fun write(action:suspend()->Unit){scope.launch{try{action()}catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}}}
    LazyColumn(modifier.fillMaxSize().widthIn(max=T.contentMax),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(T.large)){
        item {
            Text(config.uiLanguage.text("Interface language", "界面语言"),style=MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(T.small)) {
                com.meshlit.workspace.WorkspaceLanguage.entries.forEach { language ->
                    FilterChip(config.uiLanguage==language,{write{settings.setUiLanguage(language)}},
                        modifier=Modifier.testTag("workspace-language-${language.tag}"),label={Text(language.label)})
                }
            }
            Text(config.uiLanguage.text("English is the default. Chinese covers the main chat and model library; advanced tools currently retain English. Model replies and prompts are unchanged.",
                "默认使用英语。中文覆盖主要聊天和模型库，高级工具目前仍使用英语。模型回复和提示词不受影响。"),style=MaterialTheme.typography.bodySmall)
        }
        item {Text("Colors, type and display",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        item {Text("Workspace themes",style=MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(T.small)) {
                AppearancePreset.entries.forEach { preset -> FilterChip(preset.matches(config),{write{settings.applyAppearance(preset)}},
                    modifier=Modifier.testTag("appearance-preset-${preset.name}"),label={Text(preset.label)},
                    leadingIcon={Icon(Icons.Default.Circle,null,tint=buildColorScheme(config.copy(basePalette=preset.base,accentHue=preset.accent,customPalette=CustomPalette.None)).primary)}) }
            }
            Text("Presets retain font size, high contrast and operation permissions.",style=MaterialTheme.typography.bodySmall)
        }
        item {Text("Workspace layout",style=MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(T.small)) {
                WorkspaceLayout.entries.forEach { layout -> FilterChip(config.workspaceLayout==layout,{write{settings.setWorkspaceLayout(layout)}},label={Text(layout.label)}) }
            }
            Text("Adaptive keeps a sidebar in wide windows. Focus uses an on-demand menu. Phones always keep the content width.",style=MaterialTheme.typography.bodySmall)
        }
        item {Text("Side menu",style=MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(T.small)) {
                SidebarStyle.entries.forEach { style -> FilterChip(config.sidebarStyle==style,{write{settings.setSidebarStyle(style)}},label={Text(style.label)}) }
            }
        }
        item {OutlinedButton(onClick={write{settings.applyReferenceAppearance()}},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp)){
            Icon(Icons.Default.AutoAwesome,null);Spacer(Modifier.width(T.small));Text("Reference blue · light theme")}}
        item {Text("Display mode",style=MaterialTheme.typography.titleMedium);FlowRow(horizontalArrangement=Arrangement.spacedBy(T.small)){
            ThemeMode.entries.forEach{mode -> FilterChip(config.themeMode==mode,{write{settings.setThemeMode(mode)}},label={Text(when(mode){ThemeMode.SYSTEM->"System";ThemeMode.LIGHT->"Light";ThemeMode.DARK->"Dark";ThemeMode.AUTO_TIME->"Scheduled"})})}
        }}
        item {Row(horizontalArrangement=Arrangement.spacedBy(T.medium),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Wallpaper dynamic colors")
            Text(if(Build.VERSION.SDK_INT>=31) "Use your system palette" else "Available on Android 12 and later",style=MaterialTheme.typography.bodySmall)}
            Switch(config.dynamicColors,{enabled -> write{settings.setDynamicColors(enabled);if(enabled) settings.setCustomPalette(CustomPalette.None)}},enabled=Build.VERSION.SDK_INT>=31)}}
        item {HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.5f))}
        item {Text("Accent color",style=MaterialTheme.typography.titleMedium);FlowRow(horizontalArrangement=Arrangement.spacedBy(T.small)){
            AccentHue.entries.forEach{accent -> FilterChip(!config.dynamicColors && config.accentHue==accent,
                {write{settings.setDynamicColors(false);settings.setCustomPalette(CustomPalette.None);settings.setAccentHue(accent)}},
                label={Text(if(accent==AccentHue.AMBER) "Amber" else accent.displayName)},leadingIcon={Icon(Icons.Default.Circle,null,tint=accent.primary)})}
        }}
        item {Text("Dark palette",style=MaterialTheme.typography.titleMedium);FlowRow(horizontalArrangement=Arrangement.spacedBy(T.small)){
            BasePalette.entries.forEach{base -> FilterChip(config.basePalette==base,{write{settings.setDynamicColors(false);settings.setBasePalette(base)}},label={Text(when(base){BasePalette.MIDNIGHT->"Midnight";BasePalette.PAPER->"Paper";BasePalette.RUNANYWHERE->"RunAnywhere";else->base.displayName})})}
        };Text("Light mode uses a light surface; dark palettes apply in dark mode.",style=MaterialTheme.typography.bodySmall)}
        item {Row(verticalAlignment=Alignment.CenterVertically){Text("Color animations",Modifier.weight(1f));Switch(config.animationsEnabled,{write{settings.setAnimationsEnabled(it)}})}}
        item {OutlinedButton(onClick={write{settings.setDynamicColors(false);settings.setCustomPalette(CustomPalette.AnimatedGradient(
            stops=listOf(0xFF6366F1,0xFF14B8A6,0xFFF43F5E),cycleSeconds=18))}}){Text("Use a slowly shifting accent")}}
        item{HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.5f))}
        item{Text("UI font",style=MaterialTheme.typography.titleMedium);FlowRow(horizontalArrangement=Arrangement.spacedBy(T.small)){
            UiFont.entries.forEach{font->FilterChip(config.uiFont==font,{write{settings.setUiFont(font)}},label={Text(font.label)})}
        };Text("The quick brown fox — Meshlit 0123456789",style=MaterialTheme.typography.bodyLarge)}
        item{Text("Surface style",style=MaterialTheme.typography.titleMedium);FlowRow(horizontalArrangement=Arrangement.spacedBy(T.small)){
            SurfaceStyle.entries.forEach{style->FilterChip(config.surfaceStyle==style,{write{settings.setSurfaceStyle(style)}},label={Text(style.label)})}
        };Text("Tinted glass uses translucent surfaces without live blur. Low-memory, power-saving, severe-thermal and high-contrast modes use solid surfaces.",style=MaterialTheme.typography.bodySmall)}
        item{Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(T.medium)){Text("High contrast / solid surfaces",Modifier.weight(1f));Switch(config.highContrast,{write{settings.setHighContrast(it)}})}}
        item {Text("Font size: ${"%.0f".format(config.fontScale*100)}%")
            Slider(config.fontScale,{value -> write{settings.setFontScale(value)}},valueRange=0.85f..1.5f)}
        error?.let{item{ErrorCard(it){error=null}}}
    }
}

/** Reuses the audited legacy integration and its capability registry. */
@Composable private fun ExistingTermuxSettings(onBack:()->Unit) {
    val app=koinInject<com.meshlit.MeshlitApplication>()
    val bridge=koinInject<com.meshlit.network.termux.TermuxBridge>()
    val capability=com.meshlit.core.cloudmcp.agent.AgentCapability.Termux
    val scope=rememberCoroutineScope()
    var enabled by remember{mutableStateOf(app.agentCapabilities.registry.isAllowed(capability))}
    com.meshlit.ui.screens.integrations.TermuxIntegrationScreen(
        bridge=bridge,enabled=enabled,onEnableChange={value -> scope.launch {
            val setup=runCatching{bridge.probe()}.getOrNull()
            app.agentCapabilities.registry.update(capability,
                com.meshlit.core.cloudmcp.agent.AgentCapabilityRegistry.CapabilityState(
                    enabledByUser=value,permissionGranted=setup?.runCommandPermissionGranted==true))
            enabled=value
        }},onBack=onBack)
}

/** The same icon vocabulary is used in settings and the app drawer. */
internal fun settingsIcon(id:String)=when(id){
    "chat"->Icons.Outlined.ChatBubbleOutline
    "voice","gibberlink"->Icons.Outlined.GraphicEq
    "vision"->Icons.Outlined.CameraAlt
    "structured"->Icons.Outlined.DataObject
    "crypto"->Icons.Outlined.EnhancedEncryption
    "remotecommands","p2p"->Icons.Outlined.Hub
    "sharding"->Icons.Outlined.Hub
    "guardrails"->Icons.Outlined.Shield
    "resources"->Icons.Outlined.Memory
    "menu-layout"->Icons.Outlined.DashboardCustomize
    "appearance"->Icons.Outlined.Palette
    "models","router","acceleration","colibri"->Icons.Outlined.Storage
    "cloud","providers"->Icons.Outlined.Cloud
    "network","peers","ssh","firewall"->Icons.Outlined.Wifi
    "agents","openclaw","automation"->Icons.Outlined.SmartToy
    "tasks"->Icons.Outlined.Checklist
    "files","configuration"->Icons.Outlined.FolderOpen
    "media"->Icons.Outlined.PermMedia
    "power"->Icons.Outlined.BatteryChargingFull
    "help"->Icons.Outlined.HelpOutline
    "logs","monitor","audit"->Icons.Outlined.Insights
    "permissions"->Icons.Outlined.Security
    "ide","hyperl","termux","runtime","hooks"->Icons.Outlined.Code
    else->Icons.Outlined.Settings
}
