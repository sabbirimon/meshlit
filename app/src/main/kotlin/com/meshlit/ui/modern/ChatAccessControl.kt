package com.meshlit.ui.modern

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.meshlit.chat.*

@Composable internal fun ChatAccessControl(options:ChatOptions,enabled:Boolean,onMode:(ChatApprovalMode)->Unit,onTools:()->Unit,onExecutionMode:(ChatExecutionMode)->Unit,modifier:Modifier=Modifier,extraOptions:@Composable ()->Unit={}) {
    var menu by remember{mutableStateOf(false)}
    var confirm by remember{mutableStateOf(false)}
    Box(modifier) {
        TouchIcon(Icons.Default.Tune, "Chat tools and permissions", Modifier.testTag("chat-access-indicator"),enabled) { menu=true }
        val count=options.toolCount + if(options.hostedAgentToolsActive) 1 else 0
        if(count>0) Badge(Modifier.align(androidx.compose.ui.Alignment.TopEnd)) { Text(count.toString()) }
        DropdownMenu(menu,{menu=false}) {
            DropdownMenuItem(text={Text("Choose tools and permissions")},onClick={menu=false;onTools()})
            extraOptions()
            ChatExecutionMode.entries.forEach { mode -> DropdownMenuItem(
                text={Text(mode.label+when {
                    options.executionMode==mode -> " ✓"
                    mode==ChatExecutionMode.AGENT && !options.canSelectAgentMode -> " · choose tools first"
                    else -> ""
                })},
                enabled=mode==ChatExecutionMode.CHAT || options.canSelectAgentMode && !com.meshlit.BuildProfile.coreCandidate,
                modifier=Modifier.testTag("chat-execution-${mode.name.lowercase()}"),
                onClick={menu=false;onExecutionMode(mode)}) }
            HorizontalDivider()
            ChatApprovalMode.entries.forEach { mode -> DropdownMenuItem(text={Text(mode.label+(if(options.approvalMode==mode)" ✓" else ""))},
                onClick={menu=false;if(mode==ChatApprovalMode.SELECTED_TOOLS && mode!=options.approvalMode)confirm=true else onMode(mode)}) }
            HorizontalDivider()

        }
    }
    if(confirm) SelectedToolsConfirmation(onDismiss={confirm=false},onConfirm={confirm=false;onMode(ChatApprovalMode.SELECTED_TOOLS)})
}

@Composable internal fun SelectedToolsConfirmation(onDismiss:()->Unit,onConfirm:()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={Text("Use selected approved tools?")},text={Text("For this conversation and app session, selected built-in tools may run without another chat confirmation. Device/account grants, OS confirmation, protected settings and root restrictions still apply. No new tool or device access is granted. Restarting the app returns to Ask each time.")},
        confirmButton={TextButton(onClick=onConfirm){Text("Use selected tools")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}
