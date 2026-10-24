package com.meshlit.search

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class WebSearchTransportTest {
    @Test fun malformedProviderPayloadCannotEchoSecretsThroughAnException()=runBlocking {
        val secret="private-credential-should-not-appear"
        val client=OkHttpClient.Builder().addInterceptor{chain->Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
            .body("malformed $secret".toResponseBody("application/json".toMediaType())).build()}.build()
        val result=runCatching{BraveWebSearchBridge(client).search("science",secret)}
        assertTrue(result.isFailure)
        assertEquals("Invalid search provider response",result.exceptionOrNull()!!.message)
        assertFalse(result.exceptionOrNull()!!.stackTraceToString().contains(secret))
    }
    @Test fun fixedOriginBoundKeyAndActualHttpFailuresAreVisible()=runBlocking {
        var recorded:Request?=null;var code=200
        val client=OkHttpClient.Builder().addInterceptor{chain->recorded=chain.request();Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("fixture")
            .body("""{"web":{"results":[{"title":"Article","url":"https://example.com","description":"snippet"}]}}""".toResponseBody("application/json".toMediaType())).build()}.build()
        val bridge=BraveWebSearchBridge(client)
        assertEquals(1,bridge.search("science & space","private-test-key").size)
        assertEquals("api.search.brave.com",recorded!!.url.host)
        assertEquals("science & space",recorded!!.url.queryParameter("q"))
        assertEquals("10",recorded!!.url.queryParameter("count"))
        assertEquals("private-test-key",recorded!!.header("X-Subscription-Token"))
        for(status in listOf(302,401,403,429,500)) {code=status;val result=runCatching{bridge.search("science","private-test-key")};assertTrue(result.isFailure);assertTrue(result.exceptionOrNull()!!.message!!.contains("HTTP $status"));assertFalse(result.exceptionOrNull()!!.message!!.contains("private-test-key"))}
    }
    @Test fun remoteSettingsRequireSuccessAndWhitelistPublicFields() {
        val fields=buildJsonObject{put("themeMode","DARK");put("fontScale",1.1);put("token","secret");put("nested",buildJsonObject{put("key","secret")})}
        val payload=buildJsonObject{put("status","ok");put("schemaVersion",1);put("settings",fields)}
        val reply=buildJsonObject{put("isError",false);put("content",buildJsonArray{add(buildJsonObject{put("type","text");put("text",payload.toString())})})}
        val parsed=parseRemoteSettings(reply)
        assertEquals(setOf("themeMode","fontScale"),parsed.keys);assertFalse(parsed.toString().contains("secret"))
        assertTrue(runCatching{parseRemoteSettings(JsonObject(reply+ ("isError" to JsonPrimitive(true))))}.isFailure)
        assertTrue(runCatching{parseRemoteSettings(buildJsonObject{put("isError",false);put("content",buildJsonArray{})})}.isFailure)
    }
}
