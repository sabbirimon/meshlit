package com.meshlit.ui.modern

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

internal data class AppRequest(val query: String, val control: Boolean)

private val appCommand = Regex(
    """^(?:(?:please|plz|can you|could you)\s+)?(open|launch|access|operate|control|connect to|use)\s+(?:(?:the|my)\s+)?(.+?)\s*[?!.]?$""",
    RegexOption.IGNORE_CASE
)

internal fun installedAppRequest(text: String): AppRequest? {
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
                               onControls: () -> Unit, onChat: () -> Unit) {
    val context = LocalContext.current
    val pm = context.packageManager
    val settings = koinInject<SettingsRepository>()
    val protectedPackages by settings.androidAutomationHighRiskPackagesFlow.collectAsState(initial = SettingsRepository.defaultHighRiskPackages)
    val control = koinInject<AndroidControl>()
    var matches by remember(request) { mutableStateOf<List<LaunchableApp>?>(null) }
    var error by remember(request) { mutableStateOf<String?>(null) }
    LaunchedEffect(request) {
        try {
            matches = withContext(Dispatchers.IO) {
                launchableApps(pm).filter { (label, pkg) ->
                    label.contains(request.query, true) || pkg.equals(request.query, true)
                }.sortedWith(compareBy({ !it.label.equals(request.query, true) }, { it.label }, { it.packageName }))
            }
        } catch (e: SecurityException) { error = e.localizedMessage }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (request.control) "Allow app control" else "Open installed app") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(when {
                error != null -> error.orEmpty()
                matches == null -> "Finding apps…"
                matches!!.isEmpty() -> "No launchable app matches “${request.query}” in this Android profile."
                request.control -> "Choose an app to add to the allowed list, then review Android automation and Accessibility."
                else -> "Choose the app to open."
            })
            matches.orEmpty().forEach { (label, pkg) ->
                TextButton(onClick = {
                    try {
                        if (request.control) {
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
        dismissButton = { TextButton(onClick = onChat) { Text("Ask in chat") } })
}
