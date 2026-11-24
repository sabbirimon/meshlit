package com.meshlit.models
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
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
        val power=context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val battery=runCatching{context.registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED))}.getOrNull()
        val level=battery?.getIntExtra(BatteryManager.EXTRA_LEVEL,-1) ?: -1
        val scale=battery?.getIntExtra(BatteryManager.EXTRA_SCALE,-1) ?: -1
        val batteryPercent=if(scale>0 && level in 0..scale) (level.toLong()*100/scale).toInt() else null
        val charging=when(battery?.getIntExtra(BatteryManager.EXTRA_STATUS,BatteryManager.BATTERY_STATUS_UNKNOWN)) {
            BatteryManager.BATTERY_STATUS_CHARGING,BatteryManager.BATTERY_STATUS_FULL -> true
            BatteryManager.BATTERY_STATUS_DISCHARGING,BatteryManager.BATTERY_STATUS_NOT_CHARGING -> false
            else -> null
        }
        // ABI and installed artifacts are availability evidence, not a successful native load.
        val nativeFiles=File(context.applicationInfo.nativeLibraryDir).list()?.toSet().orEmpty()
        return DeviceRuntimeSnapshot("Android",Build.VERSION.SDK_INT,Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            memory.totalMem,memory.availMem,context.filesDir.usableSpace,activity.isLowRamDevice,
            if(Build.VERSION.SDK_INT>=29) runCatching{power?.currentThermalStatus}.getOrNull() else null,
            Runtime.getRuntime().availableProcessors(),native.isFile && native.canExecute(),sdk.isFile || nativeFiles.any{it.contains("runanywhere") || it.contains("rac") || it=="libllama.so"},
            Build.MANUFACTURER,Build.MODEL,if(Build.VERSION.SDK_INT>=31) Build.SOC_MODEL else Build.HARDWARE,
            powerSave=runCatching{power?.isPowerSaveMode}.getOrNull(),batteryPercent=batteryPercent,
            charging=charging,memoryPressure=memory.lowMemory)
    }
}
