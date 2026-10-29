package com.meshlit.desktop

import com.meshlit.workspace.UsageBudget
import com.meshlit.workspace.UsageLedger
import com.meshlit.workspace.UsageMoney
import kotlinx.serialization.json.*

/** Non-secret view of the existing ledger; no prompts, replies, keys or endpoint URLs. */
internal fun ideUsage(ledger: UsageLedger): JsonObject {
    val snapshot = ledger.snapshot()
    val rows = snapshot.attempts
    val month = UsageLedger.month(System.currentTimeMillis())
    return buildJsonObject {
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
