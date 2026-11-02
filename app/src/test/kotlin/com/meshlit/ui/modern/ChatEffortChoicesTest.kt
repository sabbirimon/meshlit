package com.meshlit.ui.modern

import com.meshlit.chat.ChatOptions
import com.meshlit.core.inference.models.*
import org.junit.Assert.*
import org.junit.Test

class ChatEffortChoicesTest {
    @Test fun routesOfferOnlyTheIntersectionOfEveryCurrentParticipant() {
        val levels=setOf(ReasoningEffort.LOW,ReasoningEffort.HIGH)
        val first=OnlineProfile("a","A","https://example.com/v1","model",enabled=true,
            effortSupport=EffortSupport("https://example.com/v1","model",EffortAdapter.OPENAI,levels))
        val second=first.copy(id="b",effortSupport=first.effortSupport!!.copy(values=setOf(ReasoningEffort.HIGH)))
        val route=ModelRoute("chain","Chain",enabled=true,mode=ModelRouteMode.CHAIN,steps=listOf(ModelRouteStep("cloud:a"),ModelRouteStep("cloud:b")))
        assertEquals(setOf(ReasoningEffort.HIGH),chatSupportedEfforts(ChatOptions(routeId="chain"),listOf(first,second),listOf(route)))
        assertTrue(chatSupportedEfforts(ChatOptions(routeId="chain"),listOf(first,second.copy(model="changed")),listOf(route)).isEmpty())
        assertTrue(chatSupportedEfforts(ChatOptions(routeId="chain"),listOf(first,second.copy(enabled=false)),listOf(route)).isEmpty())
        val mixed=route.copy(id="mixed",steps=listOf(ModelRouteStep("local")))
        assertTrue(chatSupportedEfforts(ChatOptions(routeId="__auto__"),listOf(first,second),listOf(route,mixed)).isEmpty())
        assertTrue(chatSupportedEfforts(ChatOptions(),listOf(first),listOf(route)).isEmpty())
    }
}
