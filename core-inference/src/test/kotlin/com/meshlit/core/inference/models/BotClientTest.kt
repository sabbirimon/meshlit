package com.meshlit.core.inference.models

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class BotClientTest {
    private val session=UUID.randomUUID().toString()
    private val key="test_key_for_loopback"
    private fun connection(server:MockWebServer)=BotConnection(name="Astr",endpoint=server.url("/").newBuilder().host("127.0.0.1").build().toString(),
        configId="bot-a",enabled=true)
    private fun events(vararg rows:String)=MockResponse().setHeader("Content-Type","text/event-stream; charset=utf-8").setBody(rows.joinToString("\n\n",postfix="\n\n"){"data: $it"})
    private fun sessionEvent(id:String=session)="""{"type":"session_id","session_id":"$id","data":null}"""
    private val plain="""{"type":"plain","data":"Actual reply","chain_type":"normal"}"""
    private val end="""{"type":"end"}"""

    @Test fun astrBotSendsScopedChatAndAcceptsOnlyTheCompletedTextReply()=runBlocking {
        MockWebServer().use{server->
            server.enqueue(events(sessionEvent(),"""{"type":"plain","chain_type":"reasoning","data":"private reasoning"}""",plain,end))
            val profile=connection(server)
            assertEquals(BotResult.Reply("Actual reply"),BotClient().send(profile,key,"Hi",session))
            val sent=server.takeRequest();val body=Json.parseToJsonElement(sent.body.readUtf8()).jsonObject
            assertEquals("/api/v1/chat",sent.path);assertEquals("Bearer $key",sent.getHeader("Authorization"))
            assertEquals(session,body.getValue("session_id").jsonPrimitive.content)
            assertEquals(profile.astrBotUsername,body.getValue("username").jsonPrimitive.content)
            assertEquals("bot-a",body.getValue("config_id").jsonPrimitive.content)
        }
    }

    @Test fun wrongSessionTruncatedAndServerRetryResponsesAreNeverSuccessful()=runBlocking {
        MockWebServer().use{server->
            val p=connection(server);val client=BotClient()
            server.enqueue(events(sessionEvent(UUID.randomUUID().toString()),plain,end))
            server.enqueue(events(sessionEvent(),plain))
            server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After","0"))
            // An automatic follow-up could consume this response and incorrectly report success.
            server.enqueue(events(sessionEvent(),plain,end))
            repeat(3){assertTrue(runCatching{client.send(p,key,"Hi",session)}.isFailure)}
            assertEquals(3,server.requestCount)
        }
    }

    @Test fun externalBotDestinationsRejectCleartextCredentialsQueriesAndMissingWebhookPaths() {
        for(url in listOf("http://192.0.2.1:6185","https://user:password@example.test","https://example.test/?key=secret")) {
            assertThrows(IllegalArgumentException::class.java){BotConnection(name="Bot",endpoint=url).destination()}
        }
        val grok=BotConnection(name="Routine",endpoint="https://example.test/hooks/route",kind=BotConnectionKind.GROK_ROUTINE)
        assertEquals("/hooks/route",grok.destination().encodedPath)
        assertThrows(IllegalArgumentException::class.java){grok.copy(endpoint="http://127.0.0.1/hooks/route").destination()}
        assertThrows(IllegalArgumentException::class.java){grok.copy(endpoint="https://example.test").destination()}
    }
}
