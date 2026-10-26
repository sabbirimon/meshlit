package com.meshlit.workspace

import kotlin.test.*
import java.util.concurrent.Executors
import java.util.concurrent.Callable

class UsageLedgerTest {
    private var stored: String? = null
    private var now = 1_790_000_000_000L
    private fun ledger() = UsageLedger({ stored }, { stored = it }, { now })
    private val route = UsageLedger.route("https://test.invalid/v1", "model")
    private fun price(currency: String = "USD") = UsagePrice(currency, UsageMoney.parse("1"), UsageMoney.parse("2"), "Reviewed", now)
    private fun start(l: UsageLedger, p: UsagePrice = price()) = l.begin(route, "model", p, 1000, 100)
    private fun budget(l: UsageLedger, monthly: String = "1", request: String = "0.6") = l.setPolicy(false, UsageBudget("USD", UsageMoney.parse(monthly), UsageMoney.parse(request)), "USD")

    @Test fun decimalAccountingAndCeilingDoNotLoseSmallCosts() {
        assertEquals(1L, UsageMoney.cost(1, 0, price().copy(inputPerMillion = 1)))
        assertEquals("0.000000001", UsageMoney.format(UsageMoney.parse("0.000000001")))
        assertFails { UsageMoney.parse("-1") }; assertFails { UsageMoney.parse("NaN") }; assertFails { UsageMoney.parse("0.0000000001") }
    }
    @Test fun restartKeepsRealUsageAndPriceSnapshot() {
        val l = ledger(); val id = start(l); l.finish(id, "complete", 1000, 100)
        val a = ledger().snapshot().attempts.single()
        assertEquals(UsageMoney.parse("0.0012"), a.cost); assertEquals(1000L, a.input)
        assertEquals(price(), a.price); assertFalse(stored!!.contains("test.invalid"))
        l.finish(id, "failed"); assertEquals("complete", l.snapshot().attempts.single().status)
    }
    @Test fun concurrentAdmissionReservesBeforeDispatch() {
        val l = ledger(); budget(l)
        val pool = Executors.newFixedThreadPool(8)
        try {
            val results = pool.invokeAll((1..8).map { Callable { runCatching { start(l) }.isSuccess } }).map { it.get() }
            assertEquals(1, results.count { it }); assertEquals(UsageMoney.parse("0.6"), l.snapshot().held("USD"))
        } finally { pool.shutdownNow() }
    }
    @Test fun interruptedAndMissingUsageNeverRefundAutomatically() {
        val l = ledger(); budget(l); val id = start(l)
        val recovered = ledger(); recovered.recoverInterrupted()
        assertEquals("cancelled", recovered.snapshot().attempts.single().status)
        assertFails { start(recovered) }
        recovered.reconcile(id, 0); val next = start(recovered); recovered.finish(next, "complete", null, 2)
        assertNull(recovered.snapshot().attempts.last().cost); assertFails { start(recovered) }
    }
    @Test fun unknownWithoutBudgetRemainsUnknownAndBlocksWhenBudgetEnabled() {
        val l = ledger(); val id = start(l, UsagePrice()); l.finish(id, "complete", 10, 10)
        assertNull(l.snapshot().attempts.single().cost)
        budget(l); assertFails { start(l) }
        l.reconcile(id, 0); assertNotNull(start(l))
    }
    @Test fun inFlightUnpricedRequestBlocksNewBudgetedRequests() {
        val l = ledger(); start(l, UsagePrice()); budget(l); assertFails { start(l) }
    }
    @Test fun currenciesRemainSeparateAndReservationsSurviveMonthBoundary() {
        val l = ledger(); budget(l); start(l)
        assertEquals(0, l.snapshot().spent("EUR", UsageLedger.month(now)))
        now += 40L * 86400000
        assertEquals(0, l.snapshot().spent("USD", UsageLedger.month(now)))
        assertFails { start(l) }
        assertNotNull(start(l, price("EUR")))
    }
    @Test fun freeOnlyRejectsUnknownStaleAndGatewayEvenIfRatesZero() {
        val l = ledger(); l.setPolicy(true, null, "USD")
        assertFails { start(l, UsagePrice()) }
        val free = price().copy(inputPerMillion = 0, outputPerMillion = 0)
        assertNotNull(start(l, free))
        assertFails { start(l, free.copy(gateway = true)) }
        assertFails { start(l, free.copy(checkedAt = now - 86400001)) }
        assertNotNull(start(l, UsagePrice(local = true)))
    }
    @Test fun budgetBlocksOpaqueGatewayStaleUnknownAndOversizeEstimates() {
        val l = ledger(); budget(l)
        assertFails { start(l, price().copy(gateway = true)) }
        assertFails { start(l, price().copy(checkedAt = now - 86400001)) }
        assertFails { start(l, UsagePrice()) }
        assertFails { l.begin(route, "model", price(), 1_000_000, 100) }
        assertTrue(l.snapshot().attempts.isEmpty())
    }
    @Test fun storageFailureDoesNotAdmitOrMutateAccounting() {
        var fail = false
        val l = UsageLedger({ stored }, { if(fail) error("disk full") else stored = it }, { now })
        budget(l); fail = true; assertFails { start(l) }; assertFails { l.snapshot() }
        fail = false
        val reopened = UsageLedger({ stored }, { if(fail) error("disk full") else stored = it }, { now })
        assertTrue(reopened.snapshot().attempts.isEmpty())
        val id = start(reopened); fail = true; assertFails { reopened.finish(id, "complete", 1, 1) }
        assertFails { reopened.snapshot() }
        assertEquals("pending", ledger().snapshot().attempts.single().status)
    }
    @Test fun actualEstimateCanOverrunAndStopsNextAdmission() {
        val l = ledger(); budget(l); val id = start(l); l.finish(id, "complete", 2_000_000, 1)
        assertTrue(l.snapshot().spent("USD", UsageLedger.month(now)) > UsageMoney.parse("1"))
        assertFails { start(l) }
    }
    @Test fun corruptJournalFailsClosedAndReconciliationPreservesEvents() {
        stored = "{}"; assertFails { ledger().snapshot() }; stored = null
        val l = ledger(); val id = start(l); assertFails { l.reconcile(id, 0) }
        l.finish(id, "failed"); l.reconcile(id, UsageMoney.parse("0.1")); l.reconcile(id, UsageMoney.parse("0.2"))
        assertEquals(UsageMoney.parse("0.2"), ledger().snapshot().attempts.single().charged)
        assertTrue(stored!!.contains("100000000")); assertTrue(stored!!.contains("200000000"))
    }
}
