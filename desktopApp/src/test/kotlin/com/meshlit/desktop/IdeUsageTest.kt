package com.meshlit.desktop

import com.meshlit.workspace.*
import kotlinx.serialization.json.*
import kotlin.test.*

class IdeUsageTest {
    private fun ledger(): UsageLedger {
        var saved: String? = null
        return UsageLedger({ saved }, { saved = it })
    }
    private fun policy(currency: String = "USD", monthly: String = "10", request: String = "1") = buildJsonObject {
        put("freeOnly", false); put("currency", currency); put("monthly", monthly); put("perRequest", request)
    }
    @Test fun usageViewPreservesUnknownsAndExcludesPrivateEndpoints() {
        val ledger = ledger()
        val privateUrl = "https://owner-private.invalid/v1"
        val first = ledger.begin(UsageLedger.route(privateUrl, "test-model"), "test-model", UsagePrice(), 20, 20)
        ledger.finish(first, "failed", null, null)
        val second = ledger.begin(UsageLedger.route("local:studio-ide", "local-model"), "local-model", UsagePrice(local = true), 20, 20)
        ledger.finish(second, "complete", 11, 5)
        val view = ideUsage(ledger)
        assertEquals(2, view["requests"]!!.jsonPrimitive.int)
        assertEquals(1, view["unknownUsageRequests"]!!.jsonPrimitive.int)
        assertEquals(11, view["inputTokens"]!!.jsonPrimitive.int)
        val failure = view["history"]!!.jsonArray.last().jsonObject
        assertEquals(JsonNull, failure["cost"]); assertEquals(JsonNull, failure["input"])
        assertTrue(failure["unresolved"]!!.jsonPrimitive.boolean)
        assertFalse(view.toString().contains(privateUrl))
    }
    @Test fun policyUpdatesOneCurrencyAndRejectsInvalidInputsAtomically() {
        val ledger = ledger()
        updateIdeBudget(ledger, policy("EUR", "20", "2"))
        updateIdeBudget(ledger, policy())
        assertEquals(2, ledger.snapshot().budgets.size)
        val before = ledger.snapshot()
        for (bad in listOf(policy(request = "11"), policy(monthly = "-1"), policy(monthly = ""), policy(currency = "usd"))) {
            assertFails { updateIdeBudget(ledger, bad) }
            assertEquals(before, ledger.snapshot())
        }
        updateIdeBudget(ledger, policy(monthly = "", request = ""))
        assertEquals("EUR", ledger.snapshot().budgets.single().currency)
        val amount = ideUsage(ledger)["budgets"]!!.jsonArray.single().jsonObject["monthly"]!!.jsonPrimitive
        assertTrue(amount.isString); assertEquals("20", amount.content)
    }
    @Test fun configuredBudgetUsesExistingAdmissionRules() {
        val ledger = ledger()
        updateIdeBudget(ledger, policy(monthly = "1", request = "0.01"))
        assertFails { ledger.begin(UsageLedger.route("gateway-test", "opaque"), "opaque", UsagePrice(gateway = true), 100, 100) }
        val request = buildJsonObject { policy().forEach { (k,v) -> put(k,v) }; put("freeOnly", true) }
        updateIdeBudget(ledger, request)
        assertFails { ledger.begin(UsageLedger.route("paid-test", "paid"), "paid", UsagePrice(inputPerMillion = UsageMoney.parse("1"), outputPerMillion = UsageMoney.parse("1"), checkedAt = System.currentTimeMillis(), source = "test"), 100, 100) }
        assertNotNull(ledger.begin(UsageLedger.route("local-test", "local"), "local", UsagePrice(local = true), 100, 100))
    }
}
