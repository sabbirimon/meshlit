package com.meshlit.core.mcp.integrations
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
class CompanionHttpTest {
    @Test fun vikingUsesScopedFindWithoutCapturingSessionsOrExecutingSkills()=runBlocking {
        val server=MockWebServer();server.start()
        try {
            server.enqueue(MockResponse().setHeader("Content-Type","application/json").setBody("""{"status":"ok","result":{"memories":[],"resources":[{"uri":"viking://resources/project/doc","level":0,"abstract":"Observed evidence","score":3.7}],"skills":[]}}"""))
            val client=OpenVikingClient(CompanionHttp(server.url("/").newBuilder().host("127.0.0.1").build().toString(),"fixture-key",true),"viking://resources/project")
            assertEquals("Observed evidence",client.find("query").single().excerpt)
            val request=server.takeRequest();assertEquals("/api/v1/search/find",request.path);assertEquals("fixture-key",request.getHeader("X-API-Key"))
            val body=Json.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertEquals("viking://resources/project",body.getValue("target_uri").jsonPrimitive.content);assertFalse(body.getValue("read_content").jsonPrimitive.boolean)
            server.enqueue(MockResponse().setHeader("Content-Type","application/json").setBody("""{"status":"ok","result":{"memories":[{"uri":"viking://resources/other","abstract":"outside"}],"resources":[],"skills":[]}}"""))
            try{client.find("query");fail()}catch(_:IllegalArgumentException){}
        }finally{server.shutdown()}
    }
    @Test fun redirectsDoNotForwardSecretsAndUnsafeEndpointsFailClosed()=runBlocking {
        val server=MockWebServer();server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location","https://example.invalid"))
            try{CompanionHttp(server.url("/").newBuilder().host("127.0.0.1").build().toString(),"fixture").request("/api/status");fail()}catch(_:IllegalArgumentException){}
            assertEquals(1,server.requestCount)
            listOf("http://192.0.2.1","https://user:password@example.invalid","https://example.invalid?key=test").forEach{url->try{CompanionHttp(url,"fixture");fail()}catch(_:IllegalArgumentException){}}
        }finally{server.shutdown()}
    }
    @Test fun artemisBindsTaskToExplicitSerialAppAndStableUuidAndChecksStopAcknowledgment()=runBlocking {
        val server=MockWebServer();server.start()
        try {
            val id="11111111-1111-4111-8111-111111111111"
            server.enqueue(MockResponse().setHeader("Content-Type","application/json").setBody("""{"status":"running","tasks":[{"session_id":"$id"}]}"""))
            val client=ArtemisClient(CompanionHttp(server.url("/").newBuilder().host("127.0.0.1").build().toString(),"fixture"))
            client.submit(id,"Inspect this test app","fixture-serial","com.meshlit","flash")
            val request=server.takeRequest();val body=Json.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertEquals("/api/run",request.path);assertEquals(id,body.getValue("session_id").jsonPrimitive.content)
            assertEquals("com.meshlit",body.getValue("locked_app_package").jsonPrimitive.content);assertEquals("fixture-serial",body.getValue("device_serial").jsonPrimitive.content)
            server.enqueue(MockResponse().setHeader("Content-Type","application/json").setBody("""{"status":"pending"}"""));assertFalse(client.stop(id))
            Unit
        }finally{server.shutdown()}
    }
}
