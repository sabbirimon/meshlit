package com.meshlit.desktop

import com.meshlit.workspace.UsageBudget
import com.meshlit.workspace.UsageLedger
import com.meshlit.workspace.UsageMoney
import kotlinx.serialization.json.*

/** Non-secret view of the existing ledger; no prompts, replies, keys or endpoint URLs. */
internal fun ideUsage(ledger: UsageLedger, usageMode: String = "protected"): JsonObject {
    require(usageMode in setOf("session", "protected"))
    val snapshot = ledger.snapshot()
    val rows = snapshot.attempts
    val month = UsageLedger.month(System.currentTimeMillis())
    return buildJsonObject {
        put("usageMode", usageMode)
        put("persistent", usageMode == "protected")
        put("requests", rows.size)
        put("inputTokens", rows.mapNotNull { it.input }.sum())
        put("outputTokens", rows.mapNotNull { it.output }.sum())
        put("unknownUsageRequests", rows.count { it.input == null || it.output == null })
        put("freeOnly", snapshot.freeOnly)
        put("month", month)
        putJsonArray("budgets") {
            snapshot.budgets.forEach { b -> addJsonObject {
                put("currency", b.currency); put("monthly", UsageMoney.format(b.monthly))
                put("perRequest", UsageMoney.format(b.perRequest))
                put("spent", UsageMoney.format(snapshot.spent(b.currency, month)))
                put("held", UsageMoney.format(snapshot.held(b.currency)))
            } }
        }
        putJsonArray("history") {
            rows.takeLast(200).asReversed().forEach { r -> addJsonObject {
                put("model", r.model); put("startedAt", r.startedAt); put("status", r.status)
                put("local", r.price.local); put("gateway", r.price.gateway)
                put("currency", r.price.currency)
                put("input", r.input?.let(::JsonPrimitive) ?: JsonNull)
                put("output", r.output?.let(::JsonPrimitive) ?: JsonNull)
                put("cost", r.charged?.let { JsonPrimitive(UsageMoney.format(it)) } ?: JsonNull)
                put("elapsedMs", r.elapsedMs?.let(::JsonPrimitive) ?: JsonNull)
                put("unresolved", r.unresolved)
            } }
        }
    }
}

internal fun updateIdeBudget(ledger: UsageLedger, request: JsonObject) {
    val free = request.getValue("freeOnly").jsonPrimitive
    require(!free.isString)
    val currency = request.getValue("currency").jsonPrimitive.content
    require(currency.matches(Regex("[A-Z]{3}")))
    val monthly = request.getValue("monthly").jsonPrimitive.content.trim()
    val perRequest = request.getValue("perRequest").jsonPrimitive.content.trim()
    require(monthly.length <= 30 && perRequest.length <= 30)
    require(monthly.isBlank() == perRequest.isBlank())
    val budget = if (monthly.isBlank()) null else UsageBudget(currency, UsageMoney.parse(monthly), UsageMoney.parse(perRequest))
    // The existing ledger persists atomically, preserves other currencies and existing request reservations.
    ledger.setPolicy(free.boolean, budget, currency)
}

/** Local-only, process-memory accounting is independent from the encrypted history.
 * A lease pins the selected journal throughout load, admission and settlement. No
 * session operation creates a vault, acquires its owner lock or weakens its budget. */
internal class IdeUsageSession(private val vault: DesktopUsage) {
    @Volatile private var session: UsageLedger? = null
    @Volatile private var epoch = 0L
    private var activeRequests = 0
    @Synchronized fun mode(): String = when { vault.isUnlocked() -> "protected"; session != null -> "session"; else -> "locked" }
    @Synchronized fun startSession(): String {
        check(activeRequests == 0) { "Finish current requests before changing usage mode" }
        if (vault.isUnlocked()) return "protected"
        if (session == null) {
            val openedEpoch = ++epoch
            session = UsageLedger({ null }, {
                check(epoch == openedEpoch && session != null) { "Usage session changed" }
            })
            // Defense in depth. Hosted requests are rejected before acquiring this journal.
            session!!.setPolicy(true, null, "USD")
        }
        return "session"
    }
    @Synchronized fun unlock(password: CharArray) {
        check(activeRequests == 0) { "Finish current requests before unlocking protected history" }
        epoch++; session = null
        // Any failed unlock ends locked. Never fall back to a previous session silently.
        vault.lock()
        try { vault.unlock(password) } catch (error: Exception) { vault.lock(); throw error }
    }
    @Synchronized fun protectedLedger(): UsageLedger {
        check(vault.isUnlocked()) { "Unlock protected usage history for hosted models and saved budgets" }
        return vault.journal()
    }
    @Synchronized fun view(): JsonObject = ideUsage(vault.takeIf { it.isUnlocked() }?.journal()
        ?: checkNotNull(session) { "Choose session chat or unlock protected usage history" }, mode())
    @Synchronized fun acquire(local: Boolean): Lease {
        val ledger = if (vault.isUnlocked()) vault.journal() else {
            check(local) { "Unlock protected usage history before using an online or remote model" }
            checkNotNull(session) { "Start local session chat first" }
        }
        activeRequests++
        return Lease(ledger) { synchronized(this) { activeRequests-- } }
    }
    @Synchronized fun lock() { epoch++; session = null; vault.lock() }
    class Lease internal constructor(val ledger: UsageLedger, private val release: () -> Unit) : AutoCloseable {
        private val closed = java.util.concurrent.atomic.AtomicBoolean(false)
        override fun close() { if (closed.compareAndSet(false, true)) release() }
    }
}
