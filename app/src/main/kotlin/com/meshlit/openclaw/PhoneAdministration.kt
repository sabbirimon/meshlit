package com.meshlit.openclaw

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.meshlit.core.mcp.*
import com.meshlit.core.common.control.ManagedFeature
import com.meshlit.core.common.control.OperationGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal fun installSourceAllowed(administrationAvailable:Boolean,query:()->Boolean):Boolean =
    administrationAvailable && query()

/** Public OS workflows, never silent root/ADB administration. APK selection and
 * protected confirmation screens belong to the human. Completion is not inferred
 * from launching an intent. */
class PhoneAdministration(private val context:Context,private val control:AndroidControl,private val settings:com.meshlit.settings.SettingsRepository,private val gate:OperationGate) {
    private val approved=context.getSharedPreferences("approved-apk-installation",0)
    private val packagePattern=Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")
    private val installLock=Mutex()
    fun approveApk(uri:Uri) {
        require(uri.scheme=="content"){"Choose an APK using Android's file picker"}
        check(approved.edit().putString("uri",uri.toString()).commit())
    }
    fun status():JsonObject=buildJsonObject {
        put("androidApi",Build.VERSION.SDK_INT)
        put("packageAdministrationAvailable",!com.meshlit.BuildConfig.PLAY_REVIEW)
        // Android requires REQUEST_INSTALL_PACKAGES even to make this query;
        // the narrower build removes it and must report unavailable directly.
        put("installSourceAllowed",installSourceAllowed(!com.meshlit.BuildConfig.PLAY_REVIEW){
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O) context.packageManager.canRequestPackageInstalls() else true
        })
        put("deviceOwner",context.getSystemService(android.app.admin.DevicePolicyManager::class.java).isDeviceOwnerApp(context.packageName))
        put("silentAdministrationImplemented",false);put("wirelessAdbAdapterImplemented",false);put("rootAdapterImplemented",false)
        put("packageActionsNeedHumanConfirmation",true)
    }
    suspend fun request(action:String,target:String,agent:Boolean=false):JsonObject = gate.run(ManagedFeature.AUTOMATION,agent) {
        withTimeout(120_000) { requestAllowed(action,target,agent) }
    }
    private suspend fun requestAllowed(action:String,target:String,agent:Boolean):JsonObject {
        check(!com.meshlit.BuildConfig.PLAY_REVIEW){"Package administration is unavailable in the Play review build"}
        check(com.meshlit.MainActivity.foregroundActive){"Open Meshlit before requesting an Android confirmation screen"}
        if(agent) check(control.enabled() && settings.androidAutomationEnabledFlow.first()){"Enable saved Android autonomy first"}
        val intent=when(action) {
            "install" -> {
                val uri=Uri.parse(target)
                require(uri.scheme=="content" && approved.getString("uri",null)==target){"The human must select this APK in App permissions first"}
                val file=installLock.withLock { withContext(Dispatchers.IO) {
                    val dir=File(context.cacheDir,"installers").apply{check(mkdirs() || isDirectory)}
                    dir.listFiles()?.filter{System.currentTimeMillis()-it.lastModified()>24L*60*60*1000}?.forEach{it.delete()}
                    require(dir.listFiles().orEmpty().size<4){"Four APK requests are retained. Clear installer cache in Android app settings or retry after retention expires"}
                    val stem=UUID.randomUUID().toString()
                    val destination=File(dir,"$stem.apk.part")
                    try {
                        context.contentResolver.openInputStream(uri)?.use{input->destination.outputStream().use{out->
                            val bytes=ByteArray(65536);var size=0L
                            while(true){currentCoroutineContext().ensureActive();val n=input.read(bytes);if(n<0) break;size+=n
                                require(size<=512L*1024*1024){"APK exceeds 512 MiB limit"};require(dir.usableSpace>128L*1024*1024){"Insufficient installer staging space"};out.write(bytes,0,n)}
                        }} ?: error("Selected APK grant is no longer readable; select it again")
                        require(context.packageManager.getPackageArchiveInfo(destination.path,0)!=null){"Selected file is not a readable Android APK"}
                        val ready=File(dir,"$stem.apk");check(destination.renameTo(ready));ready
                    } finally {destination.delete()}
                }}
                Intent(Intent.ACTION_VIEW).setDataAndType(FileProvider.getUriForFile(context,context.packageName+".fileprovider",file),"application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            "permissions","uninstall" -> {
                require(target.matches(packagePattern) && target.length<=255){"Enter an exact Android package name"}
                if(agent) {
                    require(target!=context.packageName && target !in settings.androidAutomationHighRiskPackagesFlow.first()){"Target requires human control"}
                    require(control.allApps() || target in control.packages()){"Package is outside saved app delegation"}
                    val info=context.packageManager.getApplicationInfo(target,0)
                    require(info.flags and ApplicationInfo.FLAG_SYSTEM==0){"System package administration requires human control"}
                }
                Intent(if(action=="uninstall") Intent.ACTION_DELETE else Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$target"))
            }
            else -> error("Unknown administration request")
        }
        // Recheck agent revocation immediately before launching the protected OS surface.
        if(agent) check(control.enabled() && settings.androidAutomationEnabledFlow.first()){"Android autonomy was revoked"}
        withContext(Dispatchers.Main.immediate){currentCoroutineContext().ensureActive();gate.requireAllowed(ManagedFeature.AUTOMATION,agent);check(com.meshlit.MainActivity.foregroundActive){"Meshlit left the foreground; request was not launched"};context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
        return buildJsonObject{put("requestSubmittedToAndroid",true);put("humanConfirmationRequired",true);put("completed",false)}
    }
    fun specs()=listOf(
        McpToolSpec("android_package_status","Read actual OS package-management availability; silent root/ADB administration is not implemented."){McpToolResult.Json(status())},
        McpToolSpec("android_package_request","Open Android's human-confirmed APK install, package uninstall or permission-management workflow. Does not report completion or grant permissions. Install requires an APK selected by the human in App permissions.",
            objectSchema(mapOf("action" to stringProp(enumValues=listOf("install","uninstall","permissions")),"target" to stringProp("Exact delegated package name, or previously human-selected content URI for install")),listOf("action","target"))){args->
            val obj=args as? JsonObject ?: return@McpToolSpec McpToolResult.Error(McpToolResult.ErrorCode.INVALID_ARGS,"Object arguments required")
            fun string(key:String)=(obj[key] as? JsonPrimitive)?.takeIf{it.isString}?.content
            val action=string("action") ?: return@McpToolSpec McpToolResult.Error(McpToolResult.ErrorCode.INVALID_ARGS,"action required")
            val target=string("target") ?: return@McpToolSpec McpToolResult.Error(McpToolResult.ErrorCode.INVALID_ARGS,"target required")
            McpToolResult.Json(request(action,target,true))
        }
    )
}
