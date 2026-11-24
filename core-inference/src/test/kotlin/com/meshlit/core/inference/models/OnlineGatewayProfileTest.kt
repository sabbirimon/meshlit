package com.meshlit.core.inference.models

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.junit.Test
import org.junit.Assert.*

class OnlineGatewayProfileTest {
    @Test fun oldProfilesRemainDirectAndGatewaysRoundTripWithoutEnablement() {
        val old = Json.decodeFromString<OnlineProfile>("""{"id":"direct","name":"Existing","endpoint":"https://host.invalid/v1"}""")
        assertEquals(OnlineGateway.DIRECT, old.gateway)
        listOf(OnlineGateway.OMNIROUTE, OnlineGateway.FREELLMAPI, OnlineGateway.CUSTOM).forEach { gateway ->
            val profile = old.copy(gateway=gateway)
            profile.validate()
            assertEquals(profile, Json.decodeFromString<OnlineProfile>(Json.encodeToString(profile)))
            assertFalse(profile.enabled); assertFalse(profile.agentAllowed)
            assertNull(profile.inputPricePerMillion); assertNull(profile.outputPricePerMillion)
        }
    }
    @Test fun gatewaysPreserveTlsAndProtocolBoundaries() {
        val base = OnlineProfile("gateway", "Optional", "https://host.invalid/v1", gateway=OnlineGateway.OMNIROUTE)
        for (p in listOf(base.copy(endpoint="http://host.invalid/v1"), base.copy(endpoint="https://key@host.invalid/v1"), base.copy(protocol=OnlineProtocol.GEMINI))) {
            try { p.validate(); fail("Invalid gateway accepted") } catch(expected: IllegalArgumentException) { }
        }
    }
    @Test fun preflightRejectsInvalidGenerationBeforeUsageReservation() {
        val client = OnlineModelClient()
        val profile = OnlineProfile("direct", "Direct", "https://host.invalid/v1", "model", enabled=true)
        try { client.validateGeneration(profile, "", listOf(OnlineMessage("user", "hello")), "", 100, 0.7f); fail("Key required") } catch(expected: IllegalArgumentException) { }
        try { client.validateGeneration(profile, "key", listOf(OnlineMessage("user", "hello")), "", 9000, 0.7f); fail("Token bound required") } catch(expected: IllegalArgumentException) { }
        client.validateGeneration(profile, "key", listOf(OnlineMessage("user", "hello")), "", 100, 0.7f)
    }
}
