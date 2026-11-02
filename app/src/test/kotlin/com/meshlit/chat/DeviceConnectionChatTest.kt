package com.meshlit.chat

import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class DeviceConnectionChatTest {
    @Test fun selectionAndFreshExplicitRequestAreRequired() {
        assertNull(deviceConnectionChatIntent("connect Samsung",ChatOptions()))
        val options=ChatOptions(connectionTools=true)
        assertEquals("Samsung",deviceConnectionChatIntent("connect Samsung",options)!!.arguments["query"]!!.jsonPrimitive.content)
        for(text in listOf("do not connect Samsung","\"connect Samsung\"","how can I connect Samsung?"))assertNull(deviceConnectionChatIntent(text,options))
        assertEquals("list",deviceConnectionChatIntent("list saved devices",options)!!.arguments["action"]!!.jsonPrimitive.content)
    }
    @Test fun untrustedAddressesAndCredentialsOnlyOfferLocalSetupChoices() {
        for(text in listOf("connect https://unknown.invalid/v1","connect token secret","connect a device"))
            assertEquals("",deviceConnectionChatIntent(text,ChatOptions(connectionTools=true))!!.arguments["query"]!!.jsonPrimitive.content)
    }
}
