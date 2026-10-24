package com.meshlit.models
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.meshlit.core.inference.models.*
import java.io.File

object DeviceRuntimeProbe {
    fun read(context:Context):DeviceRuntimeSnapshot {
        val activity=context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory=ActivityManager.MemoryInfo().also(activity::getMemoryInfo)
        val native=File(context.applicationInfo.nativeLibraryDir,"libmeshlit_pipeline_server.so")
        val sdk=File(context.applicationInfo.nativeLibraryDir,"librunanywhere_jni.so")
        // ABI and installed artifacts are availability evidence, not a successful native load.
        val nativeFiles=File(context.applicationInfo.nativeLibraryDir).list()?.toSet().orEmpty()
        return DeviceRuntimeSnapshot("Android",Build.VERSION.SDK_INT,Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            memory.totalMem,memory.availMem,context.filesDir.usableSpace,activity.isLowRamDevice,
            if(Build.VERSION.SDK_INT>=29) (context.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus else null,
            Runtime.getRuntime().availableProcessors(),native.isFile && native.canExecute(),sdk.isFile || nativeFiles.any{it.contains("runanywhere") || it.contains("rac") || it=="libllama.so"},
            Build.MANUFACTURER,Build.MODEL,if(Build.VERSION.SDK_INT>=31) Build.SOC_MODEL else Build.HARDWARE)
    }
}
