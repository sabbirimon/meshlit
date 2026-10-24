package com.meshlit.ui.modern
import android.app.ActivityManager
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.meshlit.models.*
import com.meshlit.core.inference.models.*

@Composable fun AccelerationScreen(onBack:()->Unit) {
    val context=LocalContext.current;val preferences=remember{AccelerationPreferences(context)}
    var backend by remember{mutableStateOf(preferences.defaultBackend())};var threads by remember{mutableIntStateOf(preferences.threadLimit())}
    val snapshot=remember{DeviceRuntimeProbe.read(context)};var error by remember{mutableStateOf<String?>(null)}
    val glEs=remember{context.getSystemService(ActivityManager::class.java).deviceConfigurationInfo.glEsVersion}
    val vulkan=remember{context.packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)}
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item{TextButton(onClick=onBack){Text("Back to settings")};Text("Acceleration",style=MaterialTheme.typography.headlineSmall)
            Text("${snapshot.abi} · ${snapshot.cpuCores} logical CPU cores · ${snapshot.soc.ifBlank{"SoC not reported"}}")
            Text("Android advertises OpenGL ES $glEs · Vulkan hardware feature: $vulkan. These are graphics capabilities, not proof of LLM GPU support.")}
        item{Text("Default backend for new imports / URL models",style=MaterialTheme.typography.titleMedium)
            LocalModelBackend.entries.forEach{choice->FilterChip(backend==choice,{try{preferences.setDefault(choice);backend=choice}catch(e:Exception){error=e.message}},enabled=choice!=LocalModelBackend.NATIVE_LOCAL || snapshot.nativeLocalInstalled,label={Text(if(choice==LocalModelBackend.RUNANYWHERE) "RunAnywhere · SDK manages acceleration" else "Native llama.cpp · CPU")})}
            Text("Existing models retain their own Models → Runtime options. RunAnywhere GPU layer selection is not exposed by the pinned SDK.")
            Text("Native CPU thread cap: $threads; effective now ${preferences.effectiveThreads()}");Slider(threads.toFloat(),{threads=it.toInt();preferences.setThreadLimit(threads)},valueRange=1f..4f,steps=2)
            Text("Applied when starting the next native local or pipeline coordinator process. Device pressure can lower the effective limit.")}
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
