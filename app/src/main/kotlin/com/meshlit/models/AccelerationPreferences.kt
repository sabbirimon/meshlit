package com.meshlit.models
import android.content.Context
import com.meshlit.core.inference.models.*
class AccelerationPreferences(private val context:Context) {
    private val prefs=context.getSharedPreferences("acceleration-preferences",0)
    fun defaultBackend()=runCatching{LocalModelBackend.valueOf(prefs.getString("default","RUNANYWHERE")!!)}.getOrDefault(LocalModelBackend.RUNANYWHERE)
    fun setDefault(backend:LocalModelBackend){require(backend!=LocalModelBackend.NATIVE_LOCAL || DeviceRuntimeProbe.read(context).nativeLocalInstalled){"Install a native CPU runtime for this ABI first"};check(prefs.edit().putString("default",backend.name).commit())}
    fun threadLimit()=prefs.getInt("threads",4).coerceIn(1,4)
    fun setThreadLimit(value:Int){require(value in 1..4);check(prefs.edit().putInt("threads",value).commit())}
    fun effectiveThreads()=minOf(threadLimit(),DeviceRuntimePolicy.plan(DeviceRuntimeProbe.read(context)).cpuThreads)
    fun newModelOptions()=ModelRuntimeOptions(backend=defaultBackend(),contextSize=DeviceRuntimePolicy.plan(DeviceRuntimeProbe.read(context)).recommendedContext)
}
