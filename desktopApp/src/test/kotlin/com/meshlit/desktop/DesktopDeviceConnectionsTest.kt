package com.meshlit.desktop

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class DesktopDeviceConnectionsTest {
    @Test fun approvedContactUsesSavedIdentityAndOnlyReadsTheModelCatalog()=runBlocking {
        val calls=java.util.concurrent.atomic.AtomicInteger()
        val server=com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1",0),0)
        server.createContext("/v1/models") { exchange ->
            assertEquals("GET",exchange.requestMethod)
            assertEquals("Bearer fixture-token",exchange.requestHeaders.getFirst("Authorization"))
            calls.incrementAndGet()
            val bytes="""{"object":"list","data":[{"id":"fixture-model"}]}""".toByteArray()
            exchange.sendResponseHeaders(200,bytes.size.toLong());exchange.responseBody.use{it.write(bytes)}
        };server.start()
        var saved:String?=null;val host=DesktopDeviceConnections({saved},{saved=it},{})
        try {
            val view=host.sync(Json.parseToJsonElement("""[{"id":"model:test","name":"Fixture API","kind":"compatible","endpoint":"http://127.0.0.1:${server.address.port}/v1","model":"fixture-model"}]""").jsonArray)
            val binding=view["sources"]!!.jsonArray.single().jsonObject["reviewBinding"]!!.jsonPrimitive.content
            host.configure(buildJsonObject{put("enabled",true);put("sourceId","model:test");put("allowed",true);put("reviewBinding",binding)})
            val offer=host.action("chat:test",buildJsonObject{put("action","list")})["offer"]!!.jsonObject
            val receipt=host.action("chat:test",buildJsonObject{put("action","connect");put("offerId",offer["id"]!!.jsonPrimitive.content);put("sourceId","model:test")},"fixture-token")["receipt"]!!.jsonObject
            assertEquals("VERIFIED",receipt["phase"]!!.jsonPrimitive.content);assertEquals(1,calls.get())
            assertFalse(saved!!.contains("fixture-token"));assertFalse(receipt.toString().contains("fixture-token"))
        }finally{host.close();server.stop(0)}
    }
    @Test fun defaultOffExactReviewedBindingAndNoPolicyInModelAction()=runBlocking {
        var saved:String?=null
        val host=DesktopDeviceConnections({saved},{saved=it},{})
        val sources=Json.parseToJsonElement("""[{"id":"model:test","name":"Saved API","kind":"compatible","endpoint":"http://127.0.0.1:18792/v1","model":"test"}]""").jsonArray
        val view=host.sync(sources)
        assertFalse(view["enabled"]!!.jsonPrimitive.boolean)
        assertFalse(view["sources"]!!.jsonArray.single().jsonObject["approved"]!!.jsonPrimitive.boolean)
        val binding=view["sources"]!!.jsonArray.single().jsonObject["reviewBinding"]!!.jsonPrimitive.content
        assertFails {host.configure(buildJsonObject{put("sourceId","model:test");put("allowed",true);put("reviewBinding","stale")})}
        host.configure(buildJsonObject{put("enabled",true);put("sourceId","model:test");put("allowed",true);put("reviewBinding",binding)})
        assertTrue(host.view()["sources"]!!.jsonArray.single().jsonObject["approved"]!!.jsonPrimitive.boolean)
        assertFails {host.action("chat:test",buildJsonObject{put("action","policy");put("enabled",true)})}
        host.sync(Json.parseToJsonElement(sources.toString().replace("18792","18793")).jsonArray)
        assertFalse(host.view()["sources"]!!.jsonArray.single().jsonObject["approved"]!!.jsonPrimitive.boolean)
        assertFalse(saved!!.contains("credential"));host.close()
    }
}
