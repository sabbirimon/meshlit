package com.meshlit.core.mcp.gateway

import com.meshlit.core.mcp.security.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.net.ServerSocket

class GatewayTest {
    @Test fun rootDoesNotUnlockLab() {
        for(mode in listOf("APP","ROOT","ROOT_CHROOT","PROOT","BUBBLEWRAP")) try {LabGate.requireReady(mode,"SSH_READY");fail()}catch(_:IllegalArgumentException){}
        try {LabGate.requireReady("VM_SSH","STOPPED");fail()}catch(_:IllegalArgumentException){}
        try {LabGate.requireReady("VM_SSH","SSH_READY",true,false);fail()}catch(_:IllegalArgumentException){}
        LabGate.requireReady("VM_SSH","SSH_READY");LabGate.requireReady("VM_SSH","SSH_READY",true,true)
    }
    @Test fun policyCannotDisableSizeBounds() {
        GatewayPolicy(ContentMode.MINIMAL,inputTerms=listOf("blocked")).check("blocked")
        try {GatewayPolicy(ContentMode.CUSTOM,inputTerms=listOf("secret")).check("SECRET");fail()}catch(_:IllegalArgumentException){}
        try {GatewayPolicy(ContentMode.MINIMAL,maxInputChars=3).check("four");fail()}catch(_:IllegalArgumentException){}
    }
    @Test fun assessmentChecksScopeExpiryAndAgent() {
        val grant=SecurityAssessment("id","owner","/tmp/test.apk","a".repeat(64),"host",100,tools=setOf("apk_inventory"))
        grant.authorize("apk_inventory","host","a".repeat(64),99,false)
        for(action in listOf<()->Unit>({grant.authorize("apk_inventory","other","a".repeat(64),99,false)},{grant.authorize("apk_inventory","host","a".repeat(64),100,false)},{grant.authorize("apk_inventory","host","a".repeat(64),99,true)})) try {action();fail()}catch(_:IllegalArgumentException){}
    }
    @Test fun realHttpAuthOriginMcpAndA2a() {
        val port=ServerSocket(0).use {it.localPort};val token="a".repeat(64)
        val server=EmbeddedGateway(port,token,{GatewayPolicy()}, {buildJsonArray {}},{_,_->buildJsonObject {}},{buildJsonArray {}},{buildJsonObject {}},{buildJsonObject {put("id","task");put("kind","task")}},{id->if(id=="task") buildJsonObject {put("id",id)} else null},{null})
        server.start(2000,false)
        fun request(path:String,body:String?=null,key:String=token,origin:String?=null):Pair<Int,String> {
            if(origin!=null) return java.net.Socket("127.0.0.1",port).use {socket ->
                socket.soTimeout=3000
                socket.getOutputStream().write("GET $path HTTP/1.1\r\nHost: 127.0.0.1\r\nAuthorization: Bearer $key\r\nOrigin: $origin\r\nConnection: close\r\n\r\n".toByteArray())
                val text=socket.getInputStream().bufferedReader().readText()
                text.lineSequence().first().split(" ")[1].toInt() to text
            }
            val c=URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
            c.connectTimeout=3000;c.readTimeout=3000;c.setRequestProperty("Authorization","Bearer $key");c.setRequestProperty("Accept","application/json, text/event-stream");origin?.let {c.setRequestProperty("Origin",it)}
            if(body!=null) {c.requestMethod="POST";c.doOutput=true;c.setRequestProperty("Content-Type","application/json");c.outputStream.use {it.write(body.toByteArray())}}
            return try {val code=c.responseCode;code to (if(code>=400)c.errorStream else c.inputStream).bufferedReader().use {it.readText()}} finally {c.disconnect()}
        }
        try {
            assertEquals(401,request("/health",key="bad").first);assertEquals(403,request("/health",origin="https://evil.example").first)
            val init=request("/mcp","""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25"}}""")
            assertEquals(200,init.first);assertTrue(init.second.contains("2025-11-25"))
            assertEquals(202,request("/mcp","""{"jsonrpc":"2.0","method":"notifications/initialized"}""").first)
            assertEquals(405,request("/mcp").first)
            assertTrue(request("/a2a","""{"jsonrpc":"2.0","id":2,"method":"message/send","params":{}}""").second.contains("task"))
            assertTrue(request("/a2a","""{"jsonrpc":"2.0","id":3,"method":"tasks/get","params":{"id":"missing"}}""").second.contains("Task not found"))
            assertTrue(request("/.well-known/agent-card.json").second.contains("0.3.0"))
            assertEquals(400,request("/v1/chat/completions","""{"stream":true}""").first)
        } finally {server.stop()}
    }
}
