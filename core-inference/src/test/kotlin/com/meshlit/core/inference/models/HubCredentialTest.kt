package com.meshlit.core.inference.models

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class HubCredentialTest {
    @Test fun browserBearerCanBeOpaqueOrLongAndMustBeHeaderSafe() {
        val bearer="abc-._~+/".repeat(150)+"=="
        val grant=buildJsonObject {put("token_type","Bearer");put("access_token",bearer)}
        assertEquals(bearer,hubBrowserAccessToken(grant))
        assertTrue(isHubBearer("hf_legacy"))
        for(bad in listOf("","abc def","token\r\nHeader","in=middle","x".repeat(HF_BEARER_LIMIT+1))) assertFalse(isHubBearer(bad))
        assertTrue(runCatching {hubBrowserAccessToken(JsonObject(grant+("token_type" to JsonPrimitive("other"))))}.isFailure)
        assertTrue(runCatching {hubBrowserAccessToken(JsonObject(grant+("access_token" to JsonPrimitive(123))))}.isFailure)
    }

    @Test fun identityUsesHowTheCredentialWasAcquired()=runBlocking {
        val paths=mutableListOf<String>()
        val client=HuggingFaceClient(OkHttpClient.Builder().addInterceptor { chain ->
            val request=chain.request()
            paths+=request.url.encodedPath
            assertEquals("https://huggingface.co",request.url.scheme+"://"+request.url.host)
            assertTrue(request.header("Authorization")=="Bearer opaque-token")
            val json=if(request.url.encodedPath=="/oauth/userinfo") """{"preferred_username":"browser-user"}""" else """{"name":"personal-user"}"""
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(json.toResponseBody()).build()
        }.build())
        assertEquals("browser-user",client.whoAmI("opaque-token",browserLogin=true))
        assertEquals("personal-user",client.whoAmI("opaque-token"))
        assertEquals(listOf("/oauth/userinfo","/api/whoami-v2"),paths)
    }
}
