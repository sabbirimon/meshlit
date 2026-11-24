package com.meshlit.ui.modern

import android.content.Intent
import android.net.Uri
import android.net.http.SslError
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.meshlit.providers.GatewayDashboardEndpoint
import com.meshlit.providers.OnlineProviders
import java.io.ByteArrayInputStream

@Composable internal fun OmniRouteDashboardSetup(providers:OnlineProviders,onOpen:(GatewayDashboardEndpoint)->Unit) {
    var saved by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    LaunchedEffect(providers) {
        try { saved=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){providers.dashboardAddress()};input=saved }
        catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(_:Exception){message="Saved dashboard address could not be read."}
    }
    SettingsFeatureSection("OmniRoute workspace") {
        Text("Browse the real dashboard of your installed OmniRoute host to connect providers, manage its API keys and configure routing. Meshlit does not install OmniRoute or create accounts on this phone.")
        OutlinedTextField(input,{input=it.take(2048)},Modifier.fillMaxWidth(),label={Text("Dashboard URL · separate from API base")},singleLine=true,
            supportingText={Text("Use your host's HTTPS address or a literal loopback tunnel on this phone. A desktop's 127.0.0.1 address is not reachable directly from Android.")})
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick={try{providers.saveDashboardAddress(input);saved=providers.dashboardAddress();message="Address saved. Nothing connected or started."}catch(_:Exception){message="Use HTTPS or literal loopback HTTP with no URL credentials, query or fragment. Address was not saved."}}){Text("Save address")}
            Button(enabled=saved.isNotBlank(),onClick={try{onOpen(GatewayDashboardEndpoint.parse(saved))}catch(_:Exception){message="Review the saved dashboard address."}}){Text("Open dashboard")}
        }
        if(message.isNotBlank()) Text(message,style=MaterialTheme.typography.bodySmall)
        Text("Sign in on the host dashboard. Meshlit never injects its saved API key into this page. To use a routed model in chat, add or edit the OmniRoute API profile below and refresh its model catalog.",style=MaterialTheme.typography.bodySmall)
    }
}

/** Owner-opened dashboard only. Reuses the host-view restrictions used by HarnessScreen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun GatewayDashboardScreen(address:GatewayDashboardEndpoint,onClose:()->Unit) {
    val context=LocalContext.current
    var status by remember(address.url) { mutableStateOf("") }
    var web by remember(address.url) { mutableStateOf<WebView?>(null) }
    BackHandler { if(web?.canGoBack()==true) web?.goBack() else onClose() }
    Scaffold(topBar={TopAppBar(title={Text("OmniRoute dashboard")},navigationIcon={TextButton(onClick=onClose){Text("Close")}},
        actions={TextButton(onClick={web?.reload()}){Text("Reload")};TextButton(onClick={try{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(address.url)))}catch(_:Exception){status="No browser is available."}}){Text("Browser")}})}) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(address.url,Modifier.padding(horizontal=12.dp),style=MaterialTheme.typography.labelSmall)
            Text("Host dashboard · login may persist in Android WebView. Sign out on the host to revoke login. Close does not stop host jobs.",Modifier.padding(12.dp),style=MaterialTheme.typography.bodySmall)
            AndroidView(modifier=Modifier.weight(1f).fillMaxWidth(),factory={WebView(it).apply {
                settings.javaScriptEnabled=true
                settings.domStorageEnabled=true
                settings.allowFileAccess=false
                settings.allowContentAccess=false
                settings.setSupportMultipleWindows(false)
                settings.javaScriptCanOpenWindowsAutomatically=false
                settings.mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
                if(android.os.Build.VERSION.SDK_INT>=26) settings.safeBrowsingEnabled=true
                CookieManager.getInstance().setAcceptThirdPartyCookies(this,false)
                webViewClient=object:WebViewClient() {
                    override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean {
                        val blocked=!address.allowsNavigation(request.url.toString())
                        if(blocked) status="Navigation outside this host is blocked. Use Browser for an external sign-in flow."
                        return blocked
                    }
                    override fun shouldInterceptRequest(view:WebView,request:WebResourceRequest):WebResourceResponse? =
                        if(address.allowsNavigation(request.url.toString())) null else WebResourceResponse("text/plain","UTF-8",403,"Blocked",emptyMap(),ByteArrayInputStream(ByteArray(0)))
                    override fun onReceivedSslError(view:WebView,handler:SslErrorHandler,error:SslError) {handler.cancel();status="TLS certificate verification failed. Fix the host certificate; no bypass is available."}
                    override fun onReceivedError(view:WebView,request:WebResourceRequest,error:WebResourceError) {if(request.isForMainFrame) status="Dashboard unavailable. Check its address, tunnel and running host."}
                    override fun onReceivedHttpError(view:WebView,request:WebResourceRequest,response:WebResourceResponse) {if(request.isForMainFrame) status="Dashboard returned HTTP ${response.statusCode}. Review host setup or sign-in."}
                }
                webChromeClient=object:WebChromeClient() {
                    override fun onPermissionRequest(request:PermissionRequest){request.deny()}
                    override fun onGeolocationPermissionsShowPrompt(origin:String,callback:GeolocationPermissions.Callback){callback.invoke(origin,false,false)}
                }
                setDownloadListener { _,_,_,_,_->status="Downloads are disabled in this view. Use the owner host or Browser if needed." }
                web=this;loadUrl(address.url)
            }},onRelease={owned->owned.stopLoading();owned.destroy();if(web===owned)web=null})
            if(status.isNotBlank()) Text(status,Modifier.padding(12.dp),style=MaterialTheme.typography.bodySmall)
        }
    }
}
