package com.meshlit.operations
import android.content.Context
import com.meshlit.core.common.control.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
/** One gate shared by DI, manual UI, agents and service entry points. */
class OperationsControl private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("operation-control", 0)
    val gate = OperationGate(runCatching { prefs.getString("policy", null)?.let { Json.decodeFromString<OperationPolicy>(it) } ?: OperationPolicy() }
        .getOrElse { OperationPolicy(emergencyStopped = true) }, allowedFeatures = com.meshlit.BuildProfile.features) { value ->
        check(prefs.edit().putString("policy", Json.encodeToString(value)).commit()) { "Operation policy persistence failed" }
    }
    companion object {
        @Volatile private var instance: OperationsControl? = null
        fun get(context: Context): OperationsControl = instance ?: synchronized(this) {
            instance ?: OperationsControl(context.applicationContext).also { instance = it }
        }
    }
}
