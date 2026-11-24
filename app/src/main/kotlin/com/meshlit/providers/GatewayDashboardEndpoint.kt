package com.meshlit.providers

import java.net.URI

/** Reviewed dashboard address, separate from the model API and its encrypted key. */
class GatewayDashboardEndpoint private constructor(val url:String,private val scheme:String,private val host:String,private val port:Int) {
    fun allowsNavigation(value:String):Boolean = runCatching {
        require(value.length<=8192 && value.none { it.isISOControl() || it=='\\' })
        val target=URI(value)
        target.userInfo==null && target.scheme?.lowercase()==scheme && target.host?.lowercase()==host && effectivePort(target)==port
    }.getOrDefault(false)

    companion object {
        private fun effectivePort(uri:URI)=if(uri.port!=-1) uri.port else if(uri.scheme.equals("https",true)) 443 else 80
        fun parse(value:String):GatewayDashboardEndpoint {
            require(value.length in 1..2048 && value.none { it.isWhitespace() || it.isISOControl() || it=='\\' }) { "Use an address without spaces or credentials" }
            val uri=URI(value)
            val scheme=uri.scheme?.lowercase();val host=uri.host?.lowercase()
            require(scheme=="https" || scheme=="http" && host in setOf("127.0.0.1","[::1]","::1")) { "Use HTTPS or literal loopback HTTP (127.0.0.1 or [::1])" }
            require(!host.isNullOrBlank() && uri.userInfo==null && uri.rawQuery==null && uri.fragment==null) { "Dashboard address must not contain a login token, credentials, query or fragment" }
            require(uri.port==-1 || uri.port in 1..65535) { "Invalid dashboard port" }
            return GatewayDashboardEndpoint(uri.toASCIIString(),scheme!!,host,effectivePort(uri))
        }
    }
}
