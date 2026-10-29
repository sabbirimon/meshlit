package com.meshlit.harness

import android.net.http.SslError
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream

/** Optional host UI, not an Android port of Node or the Python native runtime. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun HarnessScreen(onBack: () -> Unit) {
    if(com.meshlit.BuildProfile.coreCandidate) {
        Column(Modifier.padding(16.dp)) { Text("Harness is available in the experimental channel."); TextButton(onClick=onBack){Text("Back")} }
        return
    }
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("deepseek-harness-host", 0) }
    var input by remember { mutableStateOf(prefs.getString("endpoint", "https://").orEmpty()) }
    var target by remember { mutableStateOf<HarnessEndpoint?>(null) }
    var pending by remember { mutableStateOf<HarnessEndpoint?>(null) }
    var status by remember { mutableStateOf("") }
    var web by remember { mutableStateOf<WebView?>(null) }
    BackHandler(target != null) { target = null; input = prefs.getString("endpoint", "https://").orEmpty() }
    Scaffold(topBar = { TopAppBar(title = { Text("DeepSeek Harness") }, navigationIcon = {
        TextButton(onClick = { if (target != null) target = null else onBack() }) { Text("Back") }
    }, actions = { if (target != null) TextButton(onClick = { target = null }) { Text("Disconnect") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val address = target
            if (address == null) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Use your Harness host", style = MaterialTheme.typography.headlineSmall)
                    Text("Open the real Harness workspace here. Tasks run on your desktop or server; no Harness runtime is installed on this phone.")
                    OutlinedTextField(input, { input = it.take(2048) }, Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("HTTPS launch URL") }, visualTransformation = PasswordVisualTransformation())
                    Text("Paste the host’s launch URL with its login token. Only the address is saved. HTTP is allowed only for a tunnel on this phone’s literal loopback address.", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { try { pending = HarnessEndpoint.parse(input); status = "" } catch (_: Exception) { status = "Use HTTPS, a root ending in /, and an optional ?token=… login field." } }) { Text("Connect") }
                    TextButton(onClick = { prefs.edit().remove("endpoint").apply(); input = "https://"; status = "Address forgotten. This does not revoke host login cookies or stop host jobs." }) { Text("Forget address") }
                    Text("Models, MCP plugins, approvals, history and charges belong to Harness. Meshlit’s model keys, budgets and device permissions are not shared. Use Harness’s Stop control before disconnecting if you want to stop a task.", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Text(address.endpoint, Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.labelSmall)
                Text("Host workspace · Disconnect does not stop jobs", Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodySmall)
                AndroidView(modifier = Modifier.weight(1f).fillMaxWidth(), factory = { WebView(it).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.setSupportMultipleWindows(false)
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val blocked = !address.allowsNavigation(request.url.toString())
                            if (blocked) status = "Navigation outside this host is blocked. Configure providers on the host."
                            return blocked
                        }
                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                            if (address.allowsNavigation(request.url.toString())) null else WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                            handler.cancel(); status = "Certificate verification failed. Fix the host certificate; it cannot be bypassed here."
                        }
                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                            if (request.isForMainFrame) status = "Host unavailable. Verify its address, network and running service."
                        }
                        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                            if (request.isForMainFrame) status = "Host returned HTTP ${response.statusCode}. Reconnect with its current launch URL if login is required."
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
                        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) { callback.invoke(origin, false, false) }
                    }
                    setDownloadListener { _, _, _, _, _ -> status = "Downloads are not enabled in this host view." }
                    web = this
                    loadUrl(address.launchUrl)
                } }, onRelease = { owned -> owned.stopLoading(); owned.destroy(); if (web === owned) web = null })
            }
            if (status.isNotEmpty()) Text(status, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
    pending?.let { address -> AlertDialog(onDismissRequest = { pending = null }, title = { Text("Connect to this host?") },
        text = { Text("${address.endpoint}\n\nHarness can read files and run tools on its host under its own policy. This phone view grants no Meshlit device tools. WebView keeps this host’s login cookies; forgetting its address is not logout.") },
        confirmButton = { TextButton(onClick = { prefs.edit().putString("endpoint", address.endpoint).apply(); input = address.endpoint; target = address; pending = null }) { Text("Open Harness") } },
        dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } }) }
}
