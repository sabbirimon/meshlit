package com.meshlit.core.inference

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import com.meshlit.core.inference.models.*

object AndroidCpuBudget {
    @Volatile private var context:Context?=null
    private val recovery=CpuRecoveryWindow()
    private var policyRevision:Long?=null
    @Volatile var lastRequestedThreads:Int?=null
        private set
    fun initialize(value:Context){context=value.applicationContext}
    @Synchronized fun decision():CpuDecision {
        val app=context ?: return CpuDecision(1,0,"CPU policy not initialized")
        val power=app.getSystemService(Context.POWER_SERVICE) as PowerManager
        val memory=ActivityManager.MemoryInfo().also{(app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it)}
        val process=ActivityManager.RunningAppProcessInfo().also(ActivityManager::getMyMemoryState)
        val battery=app.registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level=battery?.getIntExtra(BatteryManager.EXTRA_LEVEL,-1) ?: -1
        val scale=battery?.getIntExtra(BatteryManager.EXTRA_SCALE,-1) ?: -1
        val pct=if(scale>0 && level in 0..scale) level*100/scale else null
        val plugged=battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED,-1)?.takeIf{it>=0}?.let{it!=0}
        val prefs=app.getSharedPreferences("acceleration-preferences",0)
        val revision=prefs.getLong("revision",0)
        // An explicit owner policy change applies immediately; automatic pressure recovery still waits.
        if(policyRevision!=revision){recovery.reset();policyRevision=revision}
        val mode=runCatching{CpuMode.valueOf(prefs.getString("cpu-mode","BALANCED")!!)}.getOrDefault(CpuMode.BALANCED)
        val target=AdaptiveCpuPolicy.decide(CpuConditions(Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
            if(Build.VERSION.SDK_INT>=29) runCatching{power.currentThermalStatus}.getOrNull() else null,
            runCatching{power.isPowerSaveMode}.getOrNull(),pct,plugged,memory.lowMemory,
            process.importance<=ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND),mode,prefs.getInt("threads",4).coerceIn(1,128),CpuOverrides(
                prefs.getBoolean("ignore-heat",true),prefs.getBoolean("ignore-saver",true),prefs.getBoolean("ignore-battery",true),prefs.getBoolean("ignore-memory",true),prefs.getBoolean("ignore-background",true)))
        val stable=recovery.apply(target.threads,SystemClock.elapsedRealtime())
        return target.copy(threads=stable,reason=target.reason+if(stable<target.threads) "; recovery cooldown" else "")
    }
    fun generationThreads(requested:Int?=null):Int {
        require(requested==null || requested in 1..128)
        return minOf(decision().threads,requested ?: 128).also{lastRequestedThreads=it}
    }
}
