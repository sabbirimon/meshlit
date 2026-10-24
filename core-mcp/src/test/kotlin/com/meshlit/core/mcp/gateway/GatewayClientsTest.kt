package com.meshlit.core.mcp.gateway

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL

class GatewayClientsTest {
    @Test fun keysAreDistinctHashedAndExpireOrRevoke() {
        var now=1_000_000L
        val a=GatewayClientAccess.issue("Desktop",setOf("model"),now=now)
        val b=GatewayClientAccess.issue("Phone",setOf("model"),now=now)
        assertNotEquals(a.key,b.key);assertNotEquals(a.key,a.client.keyHash)
        assertFalse(a.toString().contains(a.key))
        var records=listOf(a.client,b.client)
        val access=GatewayClientAccess({records},{now})
        assertEquals(a.client.id,access.authenticate(a.key)!!.id)
        assertNull(access.authenticate("wrong".repeat(16)))
        records=records.map{if(it.id==a.client.id) it.copy(revoked=true) else it}
        assertNull(access.authenticate(a.key));assertNotNull(access.authenticate(b.key))
        now=b.client.expiresAtMs;assertNull(access.authenticate(b.key))
    }
    @Test fun quotasAndConcurrencyArePerClientAndBounded() {
        var now=1_000_000L
        val issued=GatewayClientAccess.issue("A",setOf("m"),requestsPerMinute=2,now=now)
        val second=GatewayClientAccess.issue("B",setOf("m"),now=now)
        val access=GatewayClientAccess({listOf(issued.client,second.client)},{now})
        val a=access.authenticate(issued.key)!!;val b=access.authenticate(second.key)!!
        assertTrue(access.acquire(a));assertFalse(access.acquire(a));assertTrue(access.acquire(b));access.release(b)
        access.release(a);assertTrue(access.acquire(a));access.release(a);assertFalse(access.acquire(a))
        now+=60_000;assertTrue(access.acquire(a));access.release(a)
    }
    @Test fun rejectsUnboundedOrModelLessInferenceGrants() {
        assertThrows(IllegalArgumentException::class.java){GatewayClientAccess.issue("A",emptySet())}
        assertThrows(IllegalArgumentException::class.java){GatewayClientAccess.issue("A",setOf("m"),maxOutputTokens=4096)}
        assertThrows(IllegalArgumentException::class.java){GatewayClientAccess.issue("A",setOf("m"),lifetimeMs=Long.MAX_VALUE)}
        GatewayClientAccess.issue("Read tools",emptySet(),setOf(GatewayScope.MCP),setOf("meshlit_job_status"))
    }
    @Test fun actualHttpEnforcesScopeModelsLimitsAndClientContext() {
        val issued=GatewayClientAccess.issue("Desktop",setOf("allowed"),maxOutputTokens=32)
        var records=listOf(issued.client)
        val access=GatewayClientAccess(records={records})
        var calls=0;var contextId=""
        val port=ServerSocket(0).use{it.localPort}
        val server=EmbeddedGateway(port,"a".repeat(64),{GatewayPolicy()},{buildJsonArray{}},{_,_->buildJsonObject{}},
            {buildJsonArray {add(buildJsonObject{put("id","allowed")});add(buildJsonObject{put("id","denied")})}},
            {input->calls++;contextId=currentGatewayClient().id;buildJsonObject{put("limit",input["max_tokens"]!!)}},
            {buildJsonObject{}},{null},{null},access)
        server.start(2000,false)
        fun request(path:String,body:String?=null,key:String=issued.key):Pair<Int,String> {
            val connection=URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
            connection.connectTimeout=3000;connection.readTimeout=3000
            connection.setRequestProperty("Authorization","Bearer $key")
            if(body!=null){connection.requestMethod="POST";connection.doOutput=true;connection.setRequestProperty("Content-Type","application/json");connection.outputStream.use{it.write(body.toByteArray())}}
            return try{val status=connection.responseCode;status to (if(status>=400) connection.errorStream else connection.inputStream).bufferedReader().use{it.readText()}}finally{connection.disconnect()}
        }
        try {
            val listed=request("/v1/models");assertEquals(200,listed.first);assertTrue(listed.second.contains("allowed"));assertFalse(listed.second.contains("denied"))
            assertEquals(403,request("/mcp","{}").first)
            assertEquals(400,request("/v1/chat/completions","""{"model":"denied"}""").first)
            assertEquals(400,request("/v1/chat/completions","""{"model":"allowed","max_tokens":33}""").first)
            assertEquals(0,calls)
            val completion=request("/v1/chat/completions","""{"model":"allowed"}""")
            assertEquals(200,completion.first);assertTrue(completion.second.contains("32"));assertEquals(issued.client.id,contextId)
            records=listOf(issued.client.copy(revoked=true));assertEquals(401,request("/health").first)
            assertEquals(401,request("/health",key="bad").first)
        }finally{server.stop()}
    }
}
