package com.meshlit.ui.modern

import android.content.Intent
import android.net.Uri
import com.meshlit.chat.*
import com.meshlit.core.cloudmcp.android.MeshlitAccessibilityService
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import com.meshlit.openclaw.LaunchableApp
import com.meshlit.openclaw.launchableApps
import com.meshlit.settings.SettingsRepository
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.meshlit.di.koinInject
import com.meshlit.openclaw.AndroidControl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class AppRequest(val query: String, val control: Boolean, val instruction: String? = null)

private val appCommand = Regex(
    """^(?:(?:please|plz|can you|could you)\s+)?(open|launch|access|operate|control|connect to|use)\s+(?:(?:the|my)\s+)?(.+?)\s*[?!.]?$""",
    RegexOption.IGNORE_CASE
)

internal fun installedAppRequest(text: String): AppRequest? {
    androidAppTask(text)?.let { return AppRequest(it.name, true, it.instruction) }
    val hit = appCommand.matchEntire(text.trim()) ?: return null
    val action = hit.groupValues[1].lowercase()
    val name = hit.groupValues[2].removeSuffix(" on my phone")
    val query = name.replace(Regex("""(?i)^(?:installed )?app\s+|\s+app$"""), "").trim()
    if (action == "use" && query == name) return null
    if (!query.matches(Regex("""[\p{L}\p{N}][\p{L}\p{N} ._+'-]{0,79}"""))) return null
    return AppRequest(query, action !in setOf("open", "launch"))
}

@Composable
internal fun InstalledAppDialog(request: AppRequest, onDismiss: () -> Unit, onDone: () -> Unit,
                               onControls: () -> Unit, onChat: () -> Unit, onInstall: () -> Unit, controller: ChatController) {
    val context = LocalContext.current
    val pm = context.packageManager
    val settings = koinInject<SettingsRepository>()
    val protectedPackages by settings.androidAutomationHighRiskPackagesFlow.collectAsState(initial = SettingsRepository.defaultHighRiskPackages)
    val control = koinInject<AndroidControl>()
    var matches by remember(request) { mutableStateOf<List<LaunchableApp>?>(null) }
    var error by remember(request) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var busy by remember(request) { mutableStateOf(false) }
    suspend fun runTask(pkg: String, authorize: Boolean) {
        val task = request.instruction ?: return
        check(pkg !in settings.androidAutomationHighRiskPackagesFlow.first()) { "This app requires human control." }
        check(MeshlitAccessibilityService.instance != null) { "Enable Meshlit in Android Accessibility, return here and tap the app again." }
        val options = controller.state.value.current?.options ?: ChatOptions()
        if (authorize) {
            check(options.supportsLocalToolSelection) { "Choose a local chat model before using phone apps." }
            val selected = options.copy(phoneTools=true, executionMode=ChatExecutionMode.AGENT, approvalMode=ChatApprovalMode.SELECTED_TOOLS)
            selected.validate()
            if (!control.allApps()) control.savePackages(control.packages() + pkg)
            settings.setAndroidAutomationEnabled(true)
            control.setEnabled(true)
            controller.setOptions(selected)
        }
        check(control.enabled() && settings.androidAutomationEnabledFlow.first() && (control.allApps() || pkg in control.packages())) { "Phone-agent access was revoked." }
        if (controller.send("Use $pkg to $task") != null) onDone()
        else error = controller.state.value.error ?: "Wait for the current reply or load a local model."
    }
    LaunchedEffect(request) {
        try {
            matches = withContext(Dispatchers.IO) {
                launchableApps(pm).filter { (label, pkg) ->
                    label.contains(request.query, true) || pkg.equals(request.query, true)
                }.sortedWith(compareBy({ !it.label.equals(request.query, true) }, { it.label }, { it.packageName }))
            }
            val approved=matches.orEmpty().singleOrNull { it.label.equals(request.query,true) || it.packageName==request.query }
            val options=controller.state.value.current?.options
            if(request.instruction!=null && approved!=null && options?.agentToolsActive==true && options.phoneTools &&
                options.approvalMode==ChatApprovalMode.SELECTED_TOOLS && control.enabled() &&
                (control.allApps() || approved.packageName in control.packages()) && settings.androidAutomationEnabledFlow.first()) {
                runTask(approved.packageName,false)
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { error = e.localizedMessage }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (request.control) "Allow app control" else "Open installed app") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(when {
                error != null -> error.orEmpty()
                matches == null -> "Finding apps…"
                matches!!.isEmpty() -> "No launchable app matches “${request.query}” in this Android profile."
                request.instruction != null -> "Tap an app to enable phone-agent access and run your task. Autopilot can run this chat’s selected tools without another chat confirmation."
                request.control -> "Choose an app to add to the allowed list, then review Android automation and Accessibility."
                else -> "Choose the app to open."
            })
            if(matches?.isEmpty()==true) {
                TextButton(onClick={
                    val query=Uri.encode(request.query)
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("market://search?q=$query&c=apps")))
                    } catch (_: android.content.ActivityNotFoundException) {
                        try { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://play.google.com/store/search?q=$query&c=apps"))) }
                        catch (_: android.content.ActivityNotFoundException) { error="No app store or browser is available." }
                    }
                }) { Text("Search app store") }
                TextButton(onClick=onInstall) { Text("Install a local APK…") }
            }
            if(request.instruction != null && MeshlitAccessibilityService.instance == null)
                TextButton(onClick={ context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Open Accessibility settings") }
            matches.orEmpty().forEach { (label, pkg) ->
                TextButton(enabled=!busy,onClick = {
                    try {
                        if(request.instruction != null) {
                            busy=true
                            scope.launch {
                                try { runTask(pkg,true) }
                                catch(e:kotlinx.coroutines.CancellationException){throw e}
                                catch(e:Exception){error=e.localizedMessage ?: "App task could not start."}
                                finally {busy=false}
                            }
                        } else if (request.control) {
                            check(pkg !in protectedPackages) { "Android control is restricted for this app." }
                            control.savePackages(control.packages() + pkg)
                            onControls()
                        } else {
                            val intent = pm.getLaunchIntentForPackage(pkg) ?: error("This app is no longer available.")
                            context.startActivity(intent)
                            onDone()
                        }
                    } catch (e: Exception) { error = e.localizedMessage ?: "Android could not complete the request." }
                }) { Column { Text(label); Text(pkg, style = MaterialTheme.typography.bodySmall) } }
            }
        } }, confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        dismissButton = { if(request.instruction==null) TextButton(onClick = onChat) { Text("Ask in chat") } })
}
