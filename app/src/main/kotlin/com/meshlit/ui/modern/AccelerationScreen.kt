package com.meshlit.ui.modern
import android.app.ActivityManager
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.meshlit.models.*
import com.meshlit.core.inference.models.*

@Composable fun AccelerationScreen(onBack:()->Unit) {
    val context=LocalContext.current;val preferences=remember{AccelerationPreferences(context)}
    var backend by remember{mutableStateOf(preferences.defaultBackend())};var threads by remember{mutableIntStateOf(preferences.threadLimit())}
    var mode by remember{mutableStateOf(preferences.cpuMode())}
    var cpu by remember{mutableStateOf(com.meshlit.core.inference.AndroidCpuBudget.decision())}
    LaunchedEffect(Unit){while(true){cpu=com.meshlit.core.inference.AndroidCpuBudget.decision();kotlinx.coroutines.delay(2000)}}
    val snapshot=remember{DeviceRuntimeProbe.read(context)};var error by remember{mutableStateOf<String?>(null)}
    val glEs=remember{context.getSystemService(ActivityManager::class.java).deviceConfigurationInfo.glEsVersion}
    val vulkan=remember{context.packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)}
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().testTag("acceleration-list"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item{TextButton(onClick=onBack){Text("Back to settings")};Text("Acceleration",style=MaterialTheme.typography.headlineSmall)
            Text("${snapshot.abi} · ${snapshot.cpuCores} logical CPU cores · ${snapshot.soc.ifBlank{"SoC not reported"}}")
            Text("SDK thread hints apply per request. Native local servers and RPC workers apply a thread policy when loaded or started; restart them to apply a changed mode.",style=MaterialTheme.typography.bodySmall)
            Text("Android advertises OpenGL ES $glEs · Vulkan hardware feature: $vulkan. These are graphics capabilities, not proof of LLM GPU support.")}
        item{Text("Default backend for new imports / URL models",style=MaterialTheme.typography.titleMedium)
            LocalModelBackend.entries.forEach{choice->FilterChip(backend==choice,{try{preferences.setDefault(choice);backend=choice}catch(e:Exception){error=e.message}},enabled=choice!=LocalModelBackend.NATIVE_LOCAL || snapshot.nativeLocalInstalled,label={Text(if(choice==LocalModelBackend.RUNANYWHERE) "RunAnywhere · SDK manages acceleration" else "Native llama.cpp · CPU")})}
            Text("Existing models retain their own Models → Runtime options. RunAnywhere GPU layer selection is not exposed by the pinned SDK.")
            Text("CPU worker cap: $threads; adaptive target ${cpu.threads}")
            CpuMode.entries.forEach{choice->FilterChip(mode==choice,{try{preferences.setCpuMode(choice);mode=choice}catch(e:Exception){error=e.message}},modifier=Modifier.testTag("cpu-mode-${choice.name}"),label={Text(choice.name.replace('_',' '))})}
            Text(cpu.reason)
            if(mode==CpuMode.OVERDRIVE_PLUS){
                Text("WARNING: Overdrive+ CPU limit overrides active. Increased heat, battery drain, slowdowns or app termination are possible. Android protections and model memory admission still apply.",color=MaterialTheme.colorScheme.error)
                listOf("heat" to "Ignore Meshlit heat throttling", "saver" to "Ignore battery-saver CPU limit", "battery" to "Ignore low-battery CPU limit", "memory" to "Ignore memory-pressure CPU limit", "background" to "Ignore background CPU limit").forEach{(key,label)->
                    var checked by remember(key){mutableStateOf(preferences.ignores(key))}
                    Row{Switch(checked,{value->try{preferences.setIgnore(key,value);checked=value}catch(e:Exception){error=e.message}});Text(label)}
                }
            };Slider(threads.coerceIn(1,6).toFloat(),{threads=it.toInt();preferences.setThreadLimit(threads)},modifier=Modifier.testTag("cpu-worker-cap"),valueRange=1f..6f,steps=4,enabled=mode !in setOf(CpuMode.OVERDRIVE,CpuMode.OVERDRIVE_PLUS))
            Text("SDK thread hints apply on the next request; native process settings apply on the next load. Android controls scheduling. Offline speech shares the heavy-work queue; its internal worker count is SDK-managed.")}
        item{AcceleratorNodePanel()}
        items(com.meshlit.core.gpu.AcceleratorCatalog.integrations){integration->Card{Column(Modifier.padding(16.dp)){Text(integration.families,style=MaterialTheme.typography.titleMedium);Text("${integration.sdk} · ${integration.profiler}");Text("Runtime adapter qualified: ${integration.adapterReady}")}}}
        items(listOf(
            "Vulkan / OpenCL" to "No GPU inference plugin is installed. Requires a compatible native build, driver and device generation test.",
            "OpenGL ES" to "Used by the Android graphics stack. No OpenGL LLM compute adapter is implemented.",
            "NVIDIA CUDA / TensorRT" to "Future Linux/Windows or authenticated remote GPU node adapter; no Android CUDA runtime is installed.",
            "AMD ROCm / HIP" to "Future supported Linux/Windows host adapter; not enabled by USB discovery.",
            "Apple Metal / Core ML" to "Future macOS/iOS adapter; not available on Android.",
            "Intel oneAPI / OpenVINO" to "Future Intel CPU/GPU/NPU plugin with runtime and model compatibility checks.",
            "Qualcomm QNN / Hexagon" to "Future licensed vendor runtime plugin; requires compatible SoC, libraries and model conversion.",
            "Huawei Ascend CANN / MindSpore Lite, HiAI / HarmonyOS" to "Future platform-specific adapters; no proprietary libraries bundled.",
            "Rockchip RKNN / MediaTek NeuroPilot / Samsung ENN" to "Future vendor NPU plugins; negotiate supported operators, precision and memory before scheduling."
        )){(title,status)->Card{Column(Modifier.padding(16.dp)){Text(title,style=MaterialTheme.typography.titleMedium);Text(status,style=MaterialTheme.typography.bodySmall)}}}
        error?.let{item{ErrorCard(it){error=null}}}
    }
}
