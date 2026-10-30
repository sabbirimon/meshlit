package com.meshlit.core.mcp.gateway

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class GatewayResourceAdmissionTest {
    @Test fun agentsCannotSelfGrantProtectionChangesOrChangeMandatoryPolicy() {
        assertThrows(IllegalArgumentException::class.java){GatewayPolicy().agentPromptProtection(false)}
        val policy=GatewayPolicy(maxInputChars=100,inputTerms=listOf("blocked"),allowAgentPromptProtectionChanges=true)
        assertTrue(policy.agentPromptProtection(true).blockPromptInjection)
        val off=policy.agentPromptProtection(true).agentPromptProtection(false)
        assertEquals(policy,off)
        assertThrows(IllegalArgumentException::class.java){off.check("blocked")}
    }
    @Test fun allowanceSurvivesSerializedRestartAndDoesNotRefundFailedWork() {
        val issued=GatewayClientAccess.issue("External client",setOf("m"),maxRequests=2)
        var persisted=Json.encodeToString(listOf(issued.client))
        fun access()=GatewayClientAccess(records={Json.decodeFromString<List<GatewayClient>>(persisted)},commit={before,after->
            val records=Json.decodeFromString<List<GatewayClient>>(persisted)
            if(before !in records) false else {persisted=Json.encodeToString(records.map{if(it.id==before.id) after else it});true}
        })
        val first=access();val principal=first.authenticate(issued.key)!!
        assertTrue(first.acquire(principal));first.release(principal)
        val restarted=access()
        assertTrue(restarted.acquire(principal));restarted.release(principal)
        assertFalse(access().acquire(principal))
        assertEquals(2L,Json.decodeFromString<List<GatewayClient>>(persisted).single().usedRequests)
    }

    @Test fun failedPersistenceBlocksAdmissionAndDoesNotConsumeRateSlot() {
        val issued=GatewayClientAccess.issue("A",setOf("m"),requestsPerMinute=1)
        var record=issued.client;var writable=false
        val access=GatewayClientAccess({listOf(record)},commit={_,after->
            check(writable){"storage failed"};record=after;true
        })
        val principal=access.authenticate(issued.key)!!
        assertFalse(access.acquire(principal));assertEquals(0L,record.usedRequests)
        writable=true;assertTrue(access.acquire(principal));assertEquals(1L,record.usedRequests)
        access.release(principal)
    }

    @Test fun changedOrConflictingGrantCannotUsePreviouslyAuthenticatedAuthority() {
        val issued=GatewayClientAccess.issue("A",setOf("m","secret"))
        var record=issued.client
        val access=GatewayClientAccess({listOf(record)},commit={_,_->false})
        val principal=access.authenticate(issued.key)!!
        assertFalse(access.acquire(principal)) // compare-and-set conflict must fail closed
        record=record.copy(modelIds=setOf("m"))
        assertFalse(access.acquire(principal))
    }

    @Test fun legacyKeysMigrateToFiniteAllowanceAndInvalidBudgetsFail() {
        val issued=GatewayClientAccess.issue("A",setOf("m"))
        val legacy=JsonObject(Json.encodeToJsonElement(issued.client).jsonObject-filterSet()).toString()
        assertEquals(1000L,Json.decodeFromString<GatewayClient>(legacy).maxRequests)
        assertThrows(IllegalArgumentException::class.java){GatewayClientAccess.issue("A",setOf("m"),maxRequests=0)}
        assertThrows(IllegalArgumentException::class.java){issued.client.copy(usedRequests=1001).validate()}
    }
    private fun filterSet()=setOf("maxRequests","usedRequests")

    @Test fun mcpRouteAliasesCannotBypassModelOrOutputLimits() {
        val principal=GatewayPrincipal("client",setOf(GatewayScope.MCP),setOf("route:allowed"),setOf("gateway_route_execute"),32,Long.MAX_VALUE)
        val allowed=UnifiedRoute("allowed",RouteProtocol.LLM,listOf("m"))
        assertEquals(32,principal.authorizeRouteArguments(allowed,buildJsonObject{put("prompt","hi")})["maxTokens"]!!.jsonPrimitive.int)
        assertThrows(IllegalArgumentException::class.java){principal.authorizeRouteArguments(allowed,buildJsonObject{put("maxTokens",33)})}
        assertThrows(IllegalArgumentException::class.java){principal.authorizeRouteArguments(allowed.copy(id="denied"),buildJsonObject{})}
    }

    @Test fun catalogDoesNotInventResourcesForReadOnlyClients() {
        val principal=GatewayPrincipal("client",setOf(GatewayScope.MODELS),setOf("m"),emptySet(),32,Long.MAX_VALUE)
        val catalog=gatewayResources(principal,buildJsonArray{add(buildJsonObject{put("id","m")})})
        assertTrue(catalog["resources"]!!.jsonArray.isEmpty())
        assertFalse(catalog["transport"]!!.jsonObject["automatic_nat_traversal"]!!.jsonPrimitive.boolean)
    }
}
