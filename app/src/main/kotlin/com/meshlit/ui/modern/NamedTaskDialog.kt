package com.meshlit.ui.modern

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.meshlit.chat.NamedModelTask
import com.meshlit.core.inference.models.OnlineProfile

@Composable internal fun NamedTaskDialog(
    request:NamedModelTask, profiles:List<OnlineProfile>, prior:OnlineProfile?,
    onChoose:(OnlineProfile,Boolean)->Unit,onSetup:()->Unit,onRevoke:()->Unit,onDismiss:()->Unit
) {
    val context=LocalContext.current
    val label=request.provider.label
    val choices=profiles.filter(request.provider::accepts)
    var chosen by remember(request,choices){mutableStateOf(prior ?: choices.singleOrNull())}
    var auto by remember(request,chosen){mutableStateOf(prior!=null && prior==chosen)}
    var error by remember(request){mutableStateOf<String?>(null)}
    AlertDialog(onDismissRequest=onDismiss,title={Text(label)},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)){
            if(choices.isEmpty())Text("Connect a provider to receive replies here.")
            else {
                if(choices.size>1)choices.forEach{p-> TextButton(onClick={chosen=p}){
                    Text(if(chosen==p)"✓ ${p.name} · ${p.model}" else "${p.name} · ${p.model}")
                }}
                chosen?.let { p ->
                    Text("${p.name} · ${p.model}",style=MaterialTheme.typography.titleSmall)
                    Text(p.endpoint,style=MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Checkbox(auto,{auto=it});Text("Auto-send named tasks here in this chat")
                    }
                    if(request.task.isNotEmpty())Text("Only this task will be sent.",style=MaterialTheme.typography.bodySmall)
                    if(prior!=null)TextButton(onClick=onRevoke){Text("Turn off automatic sending")}
                }
            }
            if(request.task.isNotBlank()) {
                Text(request.task.take(400),maxLines=5)
                TextButton(onClick={
                    val intent=Intent(Intent.ACTION_SEND).setType("text/plain").setPackage(request.provider.appPackage)
                        .putExtra(Intent.EXTRA_TEXT,request.task)
                    try{context.startActivity(intent);onDismiss()}catch(e:ActivityNotFoundException){
                        error="$label is unavailable for text sharing on this device."
                    }catch(e:SecurityException){error="$label declined the share."}
                }){Text("Open task in $label app")}
            }
            TextButton(onClick=onSetup){Text("Provider settings")}
            error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        }
    },confirmButton={
        chosen?.let { profile ->
            TextButton(onClick={onChoose(profile,auto)}){Text(if(request.task.isEmpty())"Save connection" else if(auto)"Enable and send" else "Send once")}
        }
    },dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}
