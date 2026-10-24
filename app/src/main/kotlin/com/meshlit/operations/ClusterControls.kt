package com.meshlit.operations
import android.content.Context
import com.meshlit.core.mcp.control.ClusterCapacity
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
class ClusterControls private constructor(context:Context) {
    private val prefs=context.getSharedPreferences("cluster-capacity",0)
    private val mutable=MutableStateFlow(prefs.getString("capacity",null)?.let{Json.decodeFromString<ClusterCapacity>(it).also{it.validate()}} ?: ClusterCapacity())
    val state=mutable.asStateFlow()
    @Synchronized fun saveHuman(value:ClusterCapacity){value.validate();check(prefs.edit().putString("capacity",Json.encodeToString(value)).commit());mutable.value=value}
    @Synchronized fun changeAgent(limit:Int){saveHuman(mutable.value.agentChange(limit))}
    companion object { @Volatile private var instance:ClusterControls?=null
        fun get(context:Context):ClusterControls=instance ?: synchronized(this){instance ?: ClusterControls(context.applicationContext).also{instance=it}} }
}
