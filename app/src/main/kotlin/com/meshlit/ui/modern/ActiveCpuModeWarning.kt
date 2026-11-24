package com.meshlit.ui.modern

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.meshlit.core.inference.models.CpuMode
import com.meshlit.models.AccelerationPreferences
import kotlinx.coroutines.*

/** Visible even when optional notification sounds/alerts are off. */
@Composable internal fun ActiveCpuModeWarning(onManage:()->Unit) {
    val context=LocalContext.current;val owner=LocalLifecycleOwner.current
    var active by remember{mutableStateOf(false)}
    LaunchedEffect(owner){owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){
        while(isActive){active=AccelerationPreferences(context).cpuMode()==CpuMode.OVERDRIVE_PLUS;delay(2000)}
    }}
    if(active)Surface(color=MaterialTheme.colorScheme.errorContainer){Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically){
        Text("Overdrive+ is on · review resource limits",Modifier.weight(1f),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onErrorContainer)
        TextButton(onClick=onManage){Text("Manage")}
    }}
}
