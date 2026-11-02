package com.meshlit.ui.modern
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
@Composable internal fun CpuAlertSettings() {
    val context=LocalContext.current;val prefs=remember{context.getSharedPreferences("cpu-alerts",0)}
    var enabled by remember{mutableStateOf(prefs.getBoolean("enabled",false))}
    var sound by remember{mutableStateOf(prefs.getBoolean("sound",false))}
    var error by remember{mutableStateOf<String?>(null)}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){if(!it)error="Android notification permission was not granted"}
    Text("Smart resource alerts",style=MaterialTheme.typography.titleMedium)
    Row{Switch(enabled,{v->if(prefs.edit().putBoolean("enabled",v).commit()){enabled=v;if(v && Build.VERSION.SDK_INT>=33)permission.launch(Manifest.permission.POST_NOTIFICATIONS)}else error="Could not save notification setting"});Text("Dynamic warning notifications")}
    Row{Switch(sound,{v->if(prefs.edit().putBoolean("sound",v).commit())sound=v else error="Could not save sound setting"});Text("Warning alarm sound")}
    Text("Alerts update when resource conditions change, with a 60-second cooldown. Sound follows Android channel and Do Not Disturb settings. Alerts run while Meshlit is alive.")
    error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
}
