package com.meshlit.ui.phone

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.meshlit.ui.modern.*

internal fun phoneIcon(id:String):ImageVector=with(Icons.Outlined) { when(id.removeSuffix("-category")) {
    "chat"->ChatBubbleOutline;"models"->Layers;"devices"->DevicesOther;"agents"->SmartToy;"settings"->Settings
    "dashboards"->Dashboard;"usage"->BarChart;"vpn"->VpnKey;"menu-layout"->ViewSidebar;"sharding"->AccountTree
    "radar"->Radar;"host-map"->Public;"external-services"->Lan;"web-bridge"->Web;"device-dashboard"->Devices
    "meshchat"->Forum;"securityprivacy"->PrivacyTip;"deepseek-harness"->Science;"tools-automation"->Construction
    "mcp-catalog"->Extension;"connected-capabilities"->Hub;"rag"->LibraryBooks;"huggingface"->CloudDownload
    "agent-registry"->Badge;"agent-logs"->History;"guardrails"->Rule;"search"->Search;"appearance"->Palette
    "crypto"->AccountBalanceWallet;"gibberlink"->GraphicEq;"remotecommands"->SettingsRemote;"p2p"->SwapHoriz
    "colibri"->Policy;"voice-settings","voice"->Mic;"media"->PermMedia;"configuration"->Tune
    "personalization"->PersonOutline;"behavior"->Psychology;"recovery"->Restore;"training"->School;"router"->AltRoute
    "cloud"->CloudQueue;"operations"->Engineering;"gateway"->Router;"commands"->Terminal;"packages"->Inventory2
    "securitylab"->Security;"providers"->Dns;"power"->BatteryChargingFull;"external"->Cable
    "acceleration"->Speed;"agent-catalog"->CollectionsBookmark;"agent-teams"->Groups;"bot-connections"->Link
    "moltbook"->Feed;"ssh"->Key;"firewall"->Shield;"device"->PhoneAndroid;"resources"->Memory;"tasks"->Checklist
    "ide"->Code;"hyperl"->DeveloperBoard;"permissions"->AdminPanelSettings;"files"->FolderOpen
    "termux"->Keyboard;"notifications"->Notifications;"monitor"->MonitorHeart;"audit"->FactCheck;"logs"->ReceiptLong
    "identity-recovery"->Fingerprint;"rewards"->EmojiEvents;"network"->Wifi;"peers"->PeopleOutline
    "runtime"->PlayCircleOutline;"crawler"->TravelExplore;"openclaw"->Build;"automation"->AutoMode
    "hooks"->Webhook;"help"->HelpOutline;"legal"->Gavel;"about"->Info;"vision"->PhotoCamera;"structured"->DataObject
    else->Folder
} }

@Composable internal fun SectionTitle(title:String) {
    Column(Modifier.fillMaxWidth().padding(top=20.dp,bottom=6.dp)) {
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
        Text(title,Modifier.padding(top=12.dp),style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun DestinationRow(id:String,title:String,detail:String,onClick:()->Unit) {
    var explain by remember{mutableStateOf(false)}
    Row(Modifier.fillMaxWidth().heightIn(min=56.dp).testTag("workspace-$id").combinedClickable(
        role=Role.Button,onClick=onClick,onLongClickLabel="Show details",onLongClick={explain=true}),
        horizontalArrangement=Arrangement.spacedBy(16.dp),verticalAlignment=Alignment.CenterVertically) {
        Icon(phoneIcon(id),null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)
        Icon(Icons.Outlined.ChevronRight,null,Modifier.size(18.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if(explain) AlertDialog(onDismissRequest={explain=false},title={Text(title)},text={Text(detail)},
        confirmButton={TextButton(onClick={explain=false;onClick()}){Text("Open")}},
        dismissButton={TextButton(onClick={explain=false}){Text("Close")}})
}

@Composable internal fun PhoneWorkspacePage(page:String,onSelect:(String)->Unit,onPrimary:()->Unit) {
    var query by rememberSaveable(page){mutableStateOf("")}
    var expanded by rememberSaveable(page){mutableStateOf<String?>(null)}
    val all=SettingsDestinations.all
    val groups=when(page){
        "devices"->listOf("Pairing" to listOf("devices","radar","device-dashboard"),"Network" to listOf("host-map","meshchat","network","sharding"))
        "agents"->listOf("My agents" to listOf("agents","agent-registry","agent-teams"),"Tools and access" to listOf("agent-catalog","connected-capabilities","permissions"),"Activity" to listOf("tasks","agent-logs"))
        else->SettingsGroup.entries.map{group-> group.title to all.filter{it.group==group}.map{it.id}}
    }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(horizontal=22.dp,vertical=12.dp)) {
        if(page=="settings")item { OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().testTag("settings-search"),singleLine=true,
            placeholder={Text("Search settings")},leadingIcon={Icon(Icons.Outlined.Search,null)}) }
        groups.forEach{(name,ids)->
            val entries=ids.mapNotNull{id->WorkspaceDestinations.all.find{it.id==id}}.filter{
                query.isBlank() || (it.title+" "+it.keywords+" "+it.description).contains(query.trim(),true)}
            if(entries.isNotEmpty()) {
                if(page=="settings" && query.isBlank()){
                    item(key=name){DestinationRow(entries.first().id+"-category",name,entries.joinToString {it.title}){expanded=if(expanded==name)null else name}}
                } else item(key=name){SectionTitle(name)}
                if(page!="settings" || query.isNotBlank() || expanded==name) items(entries,key={it.id}){entry->
                    val label=when {entry.id=="devices"&&page=="devices"->"Pair a device";entry.id=="agents"&&page=="agents"->"Manage profiles";else->entry.title}
                    DestinationRow(entry.id,label,entry.description){if(entry.id==page)onPrimary() else onSelect(entry.id)}
                }
            }
        }
    }
}
