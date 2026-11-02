package com.meshlit.models
import android.content.Context
import com.meshlit.core.inference.models.*
class AccelerationPreferences(private val context:Context) {
    private val prefs=context.getSharedPreferences("acceleration-preferences",0)
    fun revision()=prefs.getLong("revision",0)
    fun applyCpu(expectedRevision:Long,mode:CpuMode?,workers:Int?)=synchronized(lock) {
        require(prefs.getLong("revision",0)==expectedRevision){"CPU configuration revision changed"}
        require(workers==null || workers in 1..128)
        val edit=prefs.edit().putLong("revision",expectedRevision+1)
        mode?.let{edit.putString("cpu-mode",it.name)};workers?.let{edit.putInt("threads",it)}
        check(edit.commit()){"CPU configuration could not be saved"}
    }
    fun cpuMode()=runCatching{CpuMode.valueOf(prefs.getString("cpu-mode","BALANCED")!!)}.getOrDefault(CpuMode.BALANCED)
    fun setCpuMode(mode:CpuMode)=synchronized(lock){applyCpu(revision(),mode,null)}
    fun ignores(factor:String)=prefs.getBoolean("ignore-$factor",true)
    fun setIgnore(factor:String,value:Boolean)=synchronized(lock){require(factor in setOf("heat","saver","battery","memory","background"));check(prefs.edit().putBoolean("ignore-$factor",value).putLong("revision",revision()+1).commit())}
    fun defaultBackend()=runCatching{LocalModelBackend.valueOf(prefs.getString("default","RUNANYWHERE")!!)}.getOrDefault(LocalModelBackend.RUNANYWHERE)
    fun setDefault(backend:LocalModelBackend){require(backend!=LocalModelBackend.NATIVE_LOCAL || DeviceRuntimeProbe.read(context).nativeLocalInstalled){"Install a native CPU runtime for this ABI first"};check(prefs.edit().putString("default",backend.name).commit())}
    fun threadLimit()=prefs.getInt("threads",4).coerceIn(1,128)
    fun setThreadLimit(value:Int)=synchronized(lock){applyCpu(revision(),null,value)}
    fun effectiveThreads()=com.meshlit.core.inference.AndroidCpuBudget.decision().threads
    fun newModelOptions()=ModelRuntimeOptions(backend=defaultBackend(),contextSize=DeviceRuntimePolicy.plan(DeviceRuntimeProbe.read(context)).recommendedContext)
    companion object {private val lock=Any()}
}
