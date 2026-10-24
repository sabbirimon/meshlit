package com.meshlit.browser

import android.content.Context
import com.meshlit.core.inference.browser.BrowserAction
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import java.net.URI

@Serializable data class BrowserPolicy(val enabled:Boolean=false,val agentAllowed:Boolean=false,
    val navigationAllowed:Boolean=false,val typingAllowed:Boolean=false,val maxSteps:Int=10) {
    fun validate(){require(maxSteps in 1..20)}
}

/** Pure action gate. Autonomous clicks use GET navigation, never dispatch site click handlers. */
object BrowserPolicyRules {
    fun origin(url:String):String {
        val u=URI(url)
        require(u.scheme=="https" && !u.host.isNullOrBlank() && u.userInfo==null && (u.port==-1 || u.port in 1..65535)){"Approve an HTTPS origin"}
        return "https://${u.host.lowercase()}"+if(u.port==-1 || u.port==443) "" else ":${u.port}"
    }
    private val sensitive=Regex("(?i)(password|captcha|one.?time|verification|checkout|payment|purchase|delete|remove|send|publish|subscribe|sign.?in|log.?in|submit|transfer)")
    fun check(policy:BrowserPolicy,origin:String,action:BrowserAction,state:JsonObject,agent:Boolean){
        policy.validate();require(policy.enabled && (!agent || policy.agentAllowed)){"Autonomy permission is off"}
        require(BrowserPolicyRules.origin(state["url"]!!.jsonPrimitive.content)==origin){"Page left the approved origin"}
        require(state["sensitiveFlow"]?.jsonPrimitive?.booleanOrNull!=true){"Login, verification or payment needs human interaction"}
        if(action.kind in setOf("done","scroll")) return
        val element=state["elements"]!!.jsonArray[action.index!!].jsonObject
        require(!sensitive.containsMatchIn(element["label"]?.jsonPrimitive?.content.orEmpty())){"This action needs human review"}
        when(action.kind){
            "click"->{
                require(policy.navigationAllowed && element["tag"]?.jsonPrimitive?.content=="A"){"Only approved same-origin links can be followed autonomously"}
                require(BrowserPolicyRules.origin(element["href"]!!.jsonPrimitive.content)==origin){"Link needs another origin approval"}
                require(element["download"]?.jsonPrimitive?.booleanOrNull!=true){"Download needs human review"}
            }
            "type"->{
                require(policy.typingAllowed && element["tag"]?.jsonPrimitive?.content in setOf("INPUT","TEXTAREA")){"Text input permission is off"}
                require(element["type"]?.jsonPrimitive?.content in setOf("text","search","textarea")){"Sensitive input needs human review"}
                require(element["formOrigin"]?.jsonPrimitive?.content.orEmpty() in setOf("",origin)){"Cross-origin form needs review"}
            }
            else->error("Unsupported autonomous action")
        }
    }
}

/** Visible activity owns WebView. Commands cannot launch a hidden browser or grant permissions. */
class BrowserSessionBroker(context:Context) {
    private val operations = com.meshlit.operations.OperationsControl.get(context).gate
    private val prefs=context.getSharedPreferences("browser-origin-policy",0)
    private val json=Json
    private var handler:(suspend(String,Int,Boolean,suspend()->Unit)->JsonElement)?=null
    private var origin:(()->String?)?=null
    private var running:Job?=null
    var foreground:Boolean=false
        private set
    fun setForeground(value:Boolean){foreground=value;if(!value)stop()}
    fun policy(origin:String):BrowserPolicy=prefs.getString(BrowserPolicyRules.origin(origin),null)?.let{
        runCatching{json.decodeFromString<BrowserPolicy>(it)}.getOrNull()
    } ?: BrowserPolicy()
    /** Called by human UI only; never exposed as a command. */
    fun savePolicy(origin:String,policy:BrowserPolicy){
        policy.validate();check(prefs.edit().putString(BrowserPolicyRules.origin(origin),json.encodeToString(policy)).commit())
        if(!policy.enabled || !policy.agentAllowed) stop()
    }
    fun attach(origin:()->String?,handler:suspend(String,Int,Boolean,suspend()->Unit)->JsonElement){this.origin=origin;this.handler=handler}
    fun detach(){stop();handler=null;origin=null}
    fun stop(){running?.cancel(CancellationException("Browser session stopped"))}
    suspend fun status():JsonElement=withContext(Dispatchers.Main.immediate){buildJsonObject{
        put("visible",handler!=null && foreground);put("running",running?.isActive==true)
        origin?.invoke()?.let{put("origin",it);put("policy",json.encodeToJsonElement(policy(it)))}
        put("implementation","visible-android-webview-local-model");put("externalChromeViaThisSession",false)
    }}
    suspend fun run(task:String,steps:Int,agent:Boolean,authorize:suspend()->Unit={}):JsonElement = operations.run(com.meshlit.core.common.control.ManagedFeature.BROWSER,agent) { runManaged(task,steps,agent,authorize) }
    private suspend fun runManaged(task:String,steps:Int,agent:Boolean,authorize:suspend()->Unit={}):JsonElement=withContext(Dispatchers.Main.immediate){
        require(task.isNotBlank() && task.length<=4000 && steps in 1..20)
        require(foreground){"Browser must remain in foreground"}
        require(running?.isActive!=true){"Browser already running"}
        val execute=handler ?: error("Open the visible Browser screen first")
        val approved=origin?.invoke() ?: error("Open an approved HTTPS site first")
        val policy=policy(approved)
        require(policy.enabled && (!agent || policy.agentAllowed)){"Enable origin autonomy and agent permission in Browser first"}
        coroutineScope {
            val run=async(start=CoroutineStart.LAZY){withTimeout(120_000){execute(task,minOf(steps,policy.maxSteps),agent,authorize)}}
            running=run
            try{run.start();run.await()}finally{if(running===run) running=null}
        }
    }
}
