package com.meshlit.workspace

import kotlin.test.*
import kotlinx.serialization.json.*

class UsageReplayTest {
    private val now = 1_790_000_000_000L
    private val route = UsageLedger.route("https://fixture.invalid", "model")
    private fun completed(): String {
        var saved = ""
        val ledger = UsageLedger({ null }, { saved = it }, { now })
        val id = ledger.begin(route, "model", UsagePrice("USD", 1_000_000, 2_000_000, "Fixture", now), 1, 1)
        ledger.finish(id, "complete", 1, 1)
        return saved
    }
    private fun rejectMutation(index: Int, key: String, value: JsonElement) {
        val root = Json.parseToJsonElement(completed()).jsonObject
        val events = root.getValue("events").jsonArray.toMutableList()
        events[index] = JsonObject(events[index].jsonObject + (key to value))
        val text = JsonObject(root + ("events" to JsonArray(events))).toString()
        var writes = 0
        val ledger = UsageLedger({ text }, { writes++ }, { now })
        assertFails { ledger.snapshot() }; assertFails { ledger.begin(route, "model", UsagePrice(local = true), 1, 1) }
        assertEquals(0, writes)
    }
    @Test fun corruptedPersistedAccountingCannotLowerAdmissionTotals() {
        rejectMutation(0, "reserved", JsonPrimitive(-1))
        rejectMutation(0, "time", JsonPrimitive(-1))
        rejectMutation(0, "route", JsonPrimitive("secret-endpoint"))
        rejectMutation(1, "input", JsonPrimitive(-1))
        rejectMutation(1, "output", JsonPrimitive("1"))
        rejectMutation(1, "cost", JsonPrimitive(0))
        rejectMutation(1, "status", JsonPrimitive("invented"))
        rejectMutation(1, "elapsed", JsonPrimitive(-1))
        rejectMutation(1, "credential", JsonPrimitive("fixture-secret"))
    }
    @Test fun duplicateCurrenciesAndUnversionedDocumentsFailClosed() {
        val budget = buildJsonObject { put("currency", "USD"); put("monthly", 10); put("request", 5) }
        val root = Json.parseToJsonElement(completed()).jsonObject
        val corrupt = JsonObject(root + ("budgets" to JsonArray(listOf(budget, budget))))
        assertFails { UsageLedger({ corrupt.toString() }, {}).snapshot() }
        assertFails { UsageLedger({ JsonObject(root + ("version" to JsonPrimitive(2))).toString() }, {}).snapshot() }
    }
    @Test fun ambiguousCommitCannotReuseStaleAllowance() {
        var saved: String? = null
        var ambiguous = false
        val ledger = UsageLedger({ saved }, { saved = it; if(ambiguous) error("sync outcome unknown") }, { now })
        ledger.setPolicy(false, UsageBudget("USD", 10, 10), "USD")
        ambiguous = true
        val price = UsagePrice("USD", 1, 1, "Fixture", now)
        assertFails { ledger.begin(route, "model", price, 1, 1) }
        assertFails { ledger.begin(route, "model", price, 1, 1) }
        val reopened = UsageLedger({ saved }, { saved = it }, { now })
        assertEquals(10L, reopened.snapshot().held("USD"))
        reopened.recoverInterrupted()
        assertFails { reopened.begin(route, "model", price, 1, 1) }
    }
    @Test fun exactIntegerCostOracleAndMonotonicityAcrossBoundedInputs() {
        val random = java.util.Random(721)
        repeat(1000) {
            val a = random.nextInt(100000).toLong(); val b = random.nextInt(100000).toLong()
            val input = random.nextInt(10000).toLong(); val output = random.nextInt(10000).toLong()
            val price = UsagePrice(inputPerMillion = a, outputPerMillion = b)
            val exact = (a * input + b * output + 999999) / 1000000
            assertEquals(exact, UsageMoney.cost(input, output, price))
            assertTrue(checkNotNull(UsageMoney.cost(input + 1, output, price)) >= exact)
        }
        assertFails { UsageMoney.cost(-1, 0, UsagePrice()) }
    }
}
