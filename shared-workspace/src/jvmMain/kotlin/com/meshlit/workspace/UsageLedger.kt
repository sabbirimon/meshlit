package com.meshlit.workspace

import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.serialization.json.*

/** Decimal currency units; no floating point accounting or currency conversion. */
object UsageMoney {
    fun parse(value: String): Long = BigDecimal(value).also { require(it.signum() >= 0 && it <= BigDecimal("100000000")) }
        .movePointRight(9).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
    fun format(value: Long): String = BigDecimal.valueOf(value, 9).stripTrailingZeros().toPlainString()
    fun cost(input: Long, output: Long, price: UsagePrice): Long? {
        require(input in 0..10_000_000 && output in 0..10_000_000)
        price.validate()
        val a = price.inputPerMillion ?: return null
        val b = price.outputPerMillion ?: return null
        return BigDecimal.valueOf(a).multiply(BigDecimal.valueOf(input))
            .add(BigDecimal.valueOf(b).multiply(BigDecimal.valueOf(output)))
            .divide(BigDecimal(1_000_000), 0, RoundingMode.CEILING).longValueExact()
    }
}

data class UsagePrice(val currency: String = "USD", val inputPerMillion: Long? = null,
    val outputPerMillion: Long? = null, val source: String = "Unpriced", val checkedAt: Long = 0,
    val gateway: Boolean = false, val local: Boolean = false) {
    val free get() = local || !gateway && inputPerMillion == 0L && outputPerMillion == 0L && checkedAt > 0 && source.isNotBlank()
    fun validate() {
        require(currency.matches(Regex("[A-Z]{3}")))
        require(listOf(inputPerMillion, outputPerMillion).all { it == null || it in 0..100_000_000_000_000L })
        require(source.length <= 200 && source.none(Char::isISOControl) && checkedAt >= 0)
    }
}
data class UsageBudget(val currency: String, val monthly: Long, val perRequest: Long) {
    init { require(currency.matches(Regex("[A-Z]{3}")) && monthly in 1..100_000_000_000_000_000L && perRequest in 1..monthly) }
}
data class UsageAttempt(val id: String, val route: String, val model: String, val startedAt: Long, val price: UsagePrice,
    val reserved: Long, val status: String = "pending", val input: Long? = null, val output: Long? = null,
    val cost: Long? = null, val elapsedMs: Long? = null, val reconciled: Long? = null) {
    val unresolved get() = !price.local && reconciled == null && (status != "complete" || cost == null)
    val charged get() = reconciled ?: cost
}
data class UsageSnapshot(val freeOnly: Boolean, val budgets: List<UsageBudget>, val attempts: List<UsageAttempt>) {
    fun spent(currency: String, month: String): Long = attempts.filter { it.price.currency == currency && UsageLedger.month(it.startedAt) == month }
        .fold(0L) { total, it -> Math.addExact(total, it.charged ?: 0) }
    fun held(currency: String): Long = attempts.filter { it.price.currency == currency && (it.status == "pending" || it.unresolved) }
        .fold(0L) { total, it -> Math.addExact(total, it.reserved) }
}

/** Single authority per process. Durable append-only events; storage must atomically commit or throw.
 * No prompts, replies, credentials, URLs or provider error bodies are recorded. Budgets are device-local
 * estimate admission guards, not provider billing caps. Unknown/cancelled attempts are never refunded.
 */
class UsageLedger(private val read: () -> String?, private val write: (String) -> Unit, private val clock: () -> Long = System::currentTimeMillis) {
    companion object {
        fun month(time: Long): String = Instant.ofEpochMilli(time).atZone(ZoneOffset.UTC).toLocalDate().toString().take(7)
        fun route(endpoint: String, model: String): String = MessageDigest.getInstance("SHA-256")
            .digest((endpoint.trimEnd('/') + "\u0000" + model).toByteArray()).joinToString("") { "%02x".format(it) }
    }
    private var cached: JsonObject? = null
    private var storageFailed = false
    private fun load(): JsonObject {
        check(!storageFailed) { "Usage storage outcome is uncertain; reopen before another request" }
        return cached ?: (read()?.also { require(it.length <= 12_000_000) }?.let {
        Json.parseToJsonElement(it).jsonObject.also { root -> require(root["version"]?.jsonPrimitive?.int == 1) }
    } ?: buildJsonObject { put("version", 1); put("freeOnly", false); put("budgets", JsonArray(emptyList())); put("events", JsonArray(emptyList())) }).also { cached = it }
    }
    private fun commit(root: JsonObject) { val text = root.toString(); require(text.toByteArray().size <= 12_000_000) { "Usage storage full" }; try { write(text) } catch(e: Exception) { storageFailed = true; throw e }; cached = root }
    @Synchronized fun snapshot(): UsageSnapshot {
        val root = load()
        require(root.keys == setOf("version", "freeOnly", "budgets", "events"))
        require(!root.getValue("freeOnly").jsonPrimitive.isString)
        require(root.getValue("events").jsonArray.size <= 30_000)
        val attempts = linkedMapOf<String, UsageAttempt>()
        root.getValue("events").jsonArray.forEach { value ->
            val e = value.jsonObject; val id = e.getValue("id").jsonPrimitive.content
            require(id.matches(Regex("[a-f0-9-]{36}")))
            fun n(key: String): Long? = e[key]?.jsonPrimitive?.let { require(!it.isString); it.long }
            fun fields(required: Set<String>, optional: Set<String> = emptySet()) {
                require(e.keys.containsAll(required) && e.keys.all { it in required || it in optional })
            }
            when(e.getValue("type").jsonPrimitive.content) {
                "start" -> {
                    fields(setOf("type", "id", "route", "model", "time", "reserved", "currency", "source", "checkedAt", "gateway", "local"), setOf("inputRate", "outputRate"))
                    require(id !in attempts)
                    require(e.getValue("route").jsonPrimitive.content.matches(Regex("[a-f0-9]{64}")))
                    require(e.getValue("model").jsonPrimitive.let { it.isString && it.content.length in 1..512 && it.content.none(Char::isISOControl) })
                    require(n("time")!! >= 0 && n("reserved")!! in 0..100_000_000_000_000_000L)
                    require(!e.getValue("gateway").jsonPrimitive.isString && !e.getValue("local").jsonPrimitive.isString)
                    val price = UsagePrice(e.getValue("currency").jsonPrimitive.content, n("inputRate"), n("outputRate"),
                        e.getValue("source").jsonPrimitive.content, n("checkedAt")!!, e.getValue("gateway").jsonPrimitive.boolean, e.getValue("local").jsonPrimitive.boolean)
                    price.validate()
                    attempts[id] = UsageAttempt(id, e.getValue("route").jsonPrimitive.content, e.getValue("model").jsonPrimitive.content,
                        n("time")!!, price, n("reserved")!!)
                }
                "finish" -> {
                    fields(setOf("type", "id", "status", "elapsed"), setOf("input", "output", "cost"))
                    val a = attempts.getValue(id); require(a.status == "pending")
                    val status = e.getValue("status").jsonPrimitive.content
                    require(status in setOf("complete", "failed", "cancelled"))
                    val input = n("input"); val output = n("output")
                    require(listOf(input, output).all { it == null || it in 0..10_000_000 })
                    require(n("elapsed")!! >= 0)
                    val expectedCost = if(a.price.local) 0L else if(status == "complete" && input != null && output != null && !a.price.gateway) UsageMoney.cost(input, output, a.price) else null
                    require(n("cost") == expectedCost) { "Usage cost does not match its immutable price and reported units" }
                    attempts[id] = a.copy(status = status, input = input, output = output, cost = expectedCost, elapsedMs = n("elapsed"))
                }
                "reconcile" -> {
                    fields(setOf("type", "id", "amount"))
                    val a = attempts.getValue(id); require(a.status != "pending")
                    require(n("amount")!! in 0..100_000_000_000_000_000L)
                    attempts[id] = a.copy(reconciled = n("amount"))
                }
                else -> error("Unsupported usage event")
            }
        }
        val currencies = root.getValue("budgets").jsonArray.map { it.jsonObject.getValue("currency").jsonPrimitive.content }
        require(currencies.size == currencies.distinct().size)
        return UsageSnapshot(root.getValue("freeOnly").jsonPrimitive.boolean, root.getValue("budgets").jsonArray.map {
            val b = it.jsonObject
            require(b.keys == setOf("currency", "monthly", "request"))
            require(!b.getValue("monthly").jsonPrimitive.isString && !b.getValue("request").jsonPrimitive.isString)
            UsageBudget(b.getValue("currency").jsonPrimitive.content, b.getValue("monthly").jsonPrimitive.long, b.getValue("request").jsonPrimitive.long)
        }, attempts.values.toList())
    }
    @Synchronized fun setPolicy(freeOnly: Boolean, budget: UsageBudget?, currency: String) {
        require(currency.matches(Regex("[A-Z]{3}")) && (budget == null || budget.currency == currency))
        val old = snapshot(); val budgets = old.budgets.filterNot { it.currency == currency } + listOfNotNull(budget)
        commit(JsonObject(load() + mapOf("freeOnly" to JsonPrimitive(freeOnly), "budgets" to JsonArray(budgets.map {
            buildJsonObject { put("currency", it.currency); put("monthly", it.monthly); put("request", it.perRequest) }
        }))))
    }
    private fun append(event: JsonObject) {
        val root = load(); val events = root.getValue("events").jsonArray
        val type = event.getValue("type").jsonPrimitive.content
        val pending = snapshot().attempts.count { it.status == "pending" }
        val required = when(type) { "start" -> 2; "finish" -> 0; else -> 1 }
        require(events.size + pending + required <= 30_000) { "Usage journal full; requests blocked to preserve accounting" }
        commit(JsonObject(root + ("events" to JsonArray(events + event))))
    }
    @Synchronized fun begin(route: String, model: String, price: UsagePrice, inputUpperEstimate: Long, maxOutput: Long): String {
        price.validate(); require(route.matches(Regex("[a-f0-9]{64}")) && model.length in 1..512 && model.none(Char::isISOControl))
        require(inputUpperEstimate in 0..10_000_000 && maxOutput in 1..10_000_000)
        val now = clock(); require(now >= 0); val state = snapshot()
        require(!state.freeOnly || price.free && (price.local || now - price.checkedAt in 0..86_400_000)) { "Free-only requires a fresh, explicitly zero-price direct model; gateways and unknown pricing are blocked" }
        val budget = state.budgets.firstOrNull { it.currency == price.currency }
        var reserve = 0L
        if (budget != null && !price.local) {
            require(!price.gateway) { "Budgeted requests cannot use an opaque gateway; configure a provider-side limit and remove this local budget deliberately" }
            require(now - price.checkedAt in 0..86_400_000 && price.checkedAt > 0) { "Review pricing before a budgeted request (valid for 24 hours)" }
            val estimate = UsageMoney.cost(inputUpperEstimate, maxOutput, price) ?: error("Budget requires known input and output prices")
            require(estimate <= budget.perRequest) { "Estimated request exceeds the per-request budget" }
            require(state.attempts.none { it.price.currency == price.currency && it.unresolved && (it.status != "pending" || it.reserved == 0L && !it.price.free) }) { "Resolve unknown charges in Usage before sending another budgeted request" }
            // Reserve the entire configured per-request allowance, not a possibly optimistic token estimate.
            reserve = if (price.free) 0 else budget.perRequest
            require(Math.addExact(Math.addExact(state.spent(price.currency, month(now)), state.held(price.currency)), reserve) <= budget.monthly) { "Monthly budget is exhausted or reserved by another request" }
        }
        val id = UUID.randomUUID().toString()
        append(buildJsonObject {
            put("type", "start"); put("id", id); put("route", route); put("model", model); put("time", now); put("reserved", reserve)
            put("currency", price.currency); price.inputPerMillion?.let { put("inputRate", it) }; price.outputPerMillion?.let { put("outputRate", it) }
            put("source", price.source); put("checkedAt", price.checkedAt); put("gateway", price.gateway); put("local", price.local)
        })
        return id
    }
    @Synchronized fun finish(id: String, status: String, input: Long? = null, output: Long? = null) {
        require(status in setOf("complete", "failed", "cancelled"))
        val a = snapshot().attempts.first { it.id == id }; if (a.status != "pending") return
        require(listOf(input, output).all { it == null || it in 0..10_000_000 })
        val cost = if (a.price.local) 0L else if (status == "complete" && input != null && output != null && !a.price.gateway) UsageMoney.cost(input, output, a.price) else null
        append(buildJsonObject { put("type", "finish"); put("id", id); put("status", status); put("elapsed", (clock() - a.startedAt).coerceAtLeast(0))
            input?.let { put("input", it) }; output?.let { put("output", it) }; cost?.let { put("cost", it) } })
    }
    /** Human-entered bill reconciliation, not a provider-verified charge. Kept as a separate event. */
    @Synchronized fun reconcile(id: String, amount: Long) {
        require(amount in 0..100_000_000_000_000_000L)
        val a = snapshot().attempts.first { it.id == id }; require(a.status != "pending") { "A running request cannot be reconciled" }
        append(buildJsonObject { put("type", "reconcile"); put("id", id); put("amount", amount) })
    }
    /** Call once at process startup after acquiring exclusive ownership of the backing store. */
    @Synchronized fun recoverInterrupted() {
        val pending = snapshot().attempts.filter { it.status == "pending" }
        if(pending.isEmpty()) return
        val root = load(); val now = clock()
        val recovered = pending.map { a -> buildJsonObject {
            put("type", "finish"); put("id", a.id); put("status", "cancelled"); put("elapsed", (now - a.startedAt).coerceAtLeast(0))
            if(a.price.local) put("cost", 0)
        } }
        commit(JsonObject(root + ("events" to JsonArray(root.getValue("events").jsonArray + recovered))))
    }
}
