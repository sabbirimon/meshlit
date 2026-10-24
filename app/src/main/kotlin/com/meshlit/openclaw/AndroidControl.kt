package com.meshlit.openclaw

import android.content.Context
import com.meshlit.core.cloudmcp.android.*
import com.meshlit.core.mcp.*
import com.meshlit.settings.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*

/** Explicit saved delegation, evaluated against the real foreground app on every action. */
class AndroidControl(private val context:Context,private val settings:SettingsRepository) {
    private val prefs=context.getSharedPreferences("android-agent-delegation",0)
    fun enabled()=!com.meshlit.BuildConfig.PLAY_REVIEW && prefs.getBoolean("autonomous",false)
    fun setEnabled(value:Boolean){prefs.edit().putBoolean("autonomous",value && !com.meshlit.BuildConfig.PLAY_REVIEW).commit()}
    fun packages():Set<String> = prefs.getStringSet("packages",emptySet()).orEmpty().toSet()
    fun savePackages(values:Set<String>){require(values.size<=128 && values.all{it.matches(Regex("[A-Za-z0-9_.]+"))});prefs.edit().putStringSet("packages",values).commit()}
    fun allApps()=prefs.getBoolean("all-apps",false)
    fun setAllApps(value:Boolean){prefs.edit().putBoolean("all-apps",value).commit()}
    fun specs()=listOf(
        McpToolSpec("android_control_status","Read actual accessibility binding and saved autonomous delegation. Does not grant root or OS permissions."){
            McpToolResult.Json(buildJsonObject{put("enabled",enabled());put("service_bound",MeshlitAccessibilityService.instance!=null)
                put("all_apps",allApps());put("packages",buildJsonArray{packages().sorted().forEach{add(it)}})})
        },
        McpToolSpec("android_control","Perform a delegated Android accessibility action: snapshot, click, type, open, back, home. Scope is checked against the real foreground app. Passwords and high-risk system apps are refused.",
            objectSchema(mapOf("action" to stringProp(enumValues=listOf("snapshot","click","type","open","back","home")),
                "package" to stringProp(),"text" to stringProp(),"resource_id" to stringProp(),"description" to stringProp()),listOf("action"))) {arguments ->
            if(!enabled() || !settings.androidAutomationEnabledFlow.first()) return@McpToolSpec denied("Enable Android automation and autonomous delegation first")
            val service=MeshlitAccessibilityService.instance ?: return@McpToolSpec denied("Enable Meshlit in Android accessibility settings")
            val args=arguments as? JsonObject ?: return@McpToolSpec denied("Arguments required")
            fun value(key:String)=args[key]?.jsonPrimitive?.contentOrNull
            val action=value("action")
            val target=if(action=="open") value("package").orEmpty() else service.foregroundPackage()
            if(target.isBlank() || target in settings.androidAutomationHighRiskPackagesFlow.first()) return@McpToolSpec denied("Target requires human control")
            if(!allApps() && target !in packages()) return@McpToolSpec denied("Target is outside saved delegation")
            val request=when(action){
                "snapshot" -> AutomationRequest.Snapshot(target)
                "click" -> AutomationRequest.ClickRequest(target,value("text"),value("description"),value("resource_id"))
                "type" -> AutomationRequest.TypeRequest(target,value("text")?.takeIf{it.length<=8192} ?: return@McpToolSpec denied("Text is missing or too long"))
                "open" -> AutomationRequest.OpenApp(target,target)
                "back" -> AutomationRequest.BackRequest(target)
                "home" -> AutomationRequest.HomeRequest(target)
                else -> return@McpToolSpec denied("Unknown action")
            }
            // Recheck after permission reads; revocation takes effect before dispatch.
            if(!enabled()) return@McpToolSpec denied("Autonomy was revoked")
            val response=withContext(Dispatchers.IO){service.dispatch(request)}
            if(!response.ok) return@McpToolSpec McpToolResult.Error(McpToolResult.ErrorCode.EXEC_FAILED,response.error ?: "Action failed")
            McpToolResult.Json(buildJsonObject{
                put("ok",true)
                if(response is AutomationResponse.SnapshotResponse){
                    put("package",response.snapshot.packageName)
                    put("nodes",buildJsonArray{response.snapshot.flatten().take(512).forEach{node ->add(buildJsonObject{
                        node.text?.let{put("text",it)};node.contentDescription?.let{put("description",it)};node.resourceId?.let{put("resource_id",it)}
                        put("clickable",node.isClickable);put("editable",node.isEditable);put("password",node.isPassword)
                    })}})
                }
            })
        }
    )
    private fun denied(message:String)=McpToolResult.Error(McpToolResult.ErrorCode.PERMISSION_DENIED,message)
}
