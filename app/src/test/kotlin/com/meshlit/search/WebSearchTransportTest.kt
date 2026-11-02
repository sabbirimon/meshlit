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
    @Test fun duckDuckGoZeroSetupSearchesWithoutKey()=runBlocking {
        var recorded:Request?=null
        val html="""
            <div class="result">
                <a class="result__a" href="https://duckduckgo.com/l/?uddg=https%3A%2F%2Fmeshlit.org&amp;rut=1">Meshlit Docs</a>
                <a class="result__snippet" href="https://duckduckgo.com/l/?uddg=https%3A%2F%2Fmeshlit.org&amp;rut=1">Free instant search snippets.</a>
            </div>
        """.trimIndent()
        val client=OkHttpClient.Builder().addInterceptor{chain->recorded=chain.request();Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(html.toResponseBody("text/html".toMediaType())).build()}.build()

        val bridge=DuckDuckGoWebSearchBridge(client)
        val rows=bridge.search("meshlit architecture","")
        assertEquals(1,rows.size)
        assertEquals("Meshlit Docs",rows.first().title)
        assertEquals("https://meshlit.org",rows.first().url)
        assertEquals("Free instant search snippets.",rows.first().snippet)
        assertEquals("html.duckduckgo.com",recorded!!.url.host)
        assertEquals("POST",recorded!!.method)
    }
    @Test fun universalWebSearchBridgeDefaultsToDuckDuckGoWhenNoKey()=runBlocking {
        val html="""
            <div class="result">
                <a class="result__a" href="https://duckduckgo.com/l/?uddg=https%3A%2F%2Fkotlinlang.org&amp;rut=1">Kotlin</a>
                <a class="result__snippet" href="https://duckduckgo.com/l/?uddg=https%3A%2F%2Fkotlinlang.org&amp;rut=1">Kotlin programming language.</a>
            </div>
        """.trimIndent()
        val client=OkHttpClient.Builder().addInterceptor{chain->Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(html.toResponseBody("text/html".toMediaType())).build()}.build()

        val bridge=UniversalWebSearchBridge(
            client=client,
            vendorProvider={com.meshlit.core.cloudmcp.web.WebSearchVendor.DuckDuckGo}
        )
        val rows=bridge.search("kotlin coroutines","")
        assertEquals(1,rows.size)
        assertEquals("Kotlin",rows.first().title)
        assertEquals("https://kotlinlang.org",rows.first().url)
    }
    @Test fun universalWebSearchBridgeRoutesCustomWithAnyKey()=runBlocking {
        var recorded:Request?=null
        val jsonPayload="""{"results":[{"title":"Custom Entry","url":"https://custom.api/item","snippet":"Content snippet"}]}"""
        val client=OkHttpClient.Builder().addInterceptor{chain->recorded=chain.request();Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(jsonPayload.toResponseBody("application/json".toMediaType())).build()}.build()

        val bridge=UniversalWebSearchBridge(
            client=client,
            vendorProvider={com.meshlit.core.cloudmcp.web.WebSearchVendor.Custom},
            customEndpointProvider={"https://mysearch.example/v1/search"},
            customHeaderProvider={"X-My-Token"}
        )
        val rows=bridge.search("distributed compute","my-arbitrary-api-key")
        assertEquals(1,rows.size)
        assertEquals("Custom Entry",rows.first().title)
        assertEquals("https://custom.api/item",rows.first().url)
        assertEquals("mysearch.example",recorded!!.url.host)
        assertEquals("my-arbitrary-api-key",recorded!!.header("X-My-Token"))
    }
    @Test fun duckDuckGoChallengeIsReportedAsUnavailable()=runBlocking {
        val client=OkHttpClient.Builder().addInterceptor { chain->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(202).message("challenge")
                .body("<form id='challenge-form'></form>".toResponseBody("text/html".toMediaType())).build()
        }.build()
        val failure=runCatching { DuckDuckGoWebSearchBridge(client).search("science","") }.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().contains("human verification"))
    }
    @Test fun unsupportedVendorNeverSendsItsKeyToAnotherProvider()=runBlocking {
        var calls=0
        val client=OkHttpClient.Builder().addInterceptor { calls++; error("Unexpected provider request") }.build()
        val bridge=UniversalWebSearchBridge(client=client,vendorProvider={com.meshlit.core.cloudmcp.web.WebSearchVendor.Tavily})
        assertTrue(runCatching { bridge.search("science","tavily-test-key") }.isFailure)
        assertEquals(0,calls)
    }
    @Test fun storedBraveKeyPreservesBraveRouting()=runBlocking {
        var recorded:Request?=null
        val client=OkHttpClient.Builder().addInterceptor { chain->
            recorded=chain.request()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .body("{\"web\":{\"results\":[]}}".toResponseBody("application/json".toMediaType())).build()
        }.build()
        UniversalWebSearchBridge(client=client).search("science","brave-test-key")
        assertEquals("api.search.brave.com",recorded!!.url.host)
        assertEquals("brave-test-key",recorded!!.header("X-Subscription-Token"))
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
