package com.meshlit.ui.modern

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.meshlit.chat.*

@Composable internal fun ChatAccessControl(options:ChatOptions,enabled:Boolean,onMode:(ChatApprovalMode)->Unit,onTools:()->Unit,modifier:Modifier=Modifier) {
    var menu by remember{mutableStateOf(false)}
    var confirm by remember{mutableStateOf(false)}
    Box(modifier) {
        TextButton(onClick={menu=true},enabled=enabled,modifier=Modifier.fillMaxWidth().testTag("chat-access-indicator"),contentPadding=PaddingValues(horizontal=4.dp)) {
            Icon(Icons.Default.Shield,null,Modifier.size(16.dp));Spacer(Modifier.width(6.dp))
            Text(if(!options.usesLocalTools)"Tools off" else when(options.approvalMode){ChatApprovalMode.ASK->"Ask · ${options.toolCount} tools";ChatApprovalMode.AUTO_READS->"Auto reads · ${options.toolCount}";ChatApprovalMode.SELECTED_TOOLS->"Approved tools · ${options.toolCount}"},style=MaterialTheme.typography.labelMedium,maxLines=2)
        }
        DropdownMenu(menu,{menu=false}) {
            ChatApprovalMode.entries.forEach { mode -> DropdownMenuItem(text={Text(mode.label+(if(options.approvalMode==mode)" ✓" else ""))},
                onClick={menu=false;if(mode==ChatApprovalMode.SELECTED_TOOLS && mode!=options.approvalMode)confirm=true else onMode(mode)}) }
            HorizontalDivider()
            DropdownMenuItem(text={Text("Choose tools and conversation settings")},onClick={menu=false;onTools()})
        }
    }
    if(confirm) SelectedToolsConfirmation(onDismiss={confirm=false},onConfirm={confirm=false;onMode(ChatApprovalMode.SELECTED_TOOLS)})
}

@Composable internal fun SelectedToolsConfirmation(onDismiss:()->Unit,onConfirm:()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={Text("Use selected approved tools?")},text={Text("For this conversation and app session, selected built-in tools may run without another chat confirmation. Device/account grants, OS confirmation, protected settings and root restrictions still apply. No new tool or device access is granted. Restarting the app returns to Ask each time.")},
        confirmButton={TextButton(onClick=onConfirm){Text("Use selected tools")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}
