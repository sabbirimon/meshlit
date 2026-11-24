package com.meshlit.workspace

object UsagePresentation {
    const val scope = "This device only · calendar month in UTC. Token costs are estimates from reviewed rates, not invoices. Other devices, media, provider tools, taxes and gateway retries are not covered. Set a provider-side cap for a billing guarantee."
    fun totals(state: UsageSnapshot, currency: String, month: String): String {
        val spent = state.spent(currency, month); val held = state.held(currency)
        val limit = state.budgets.firstOrNull { it.currency == currency }?.monthly
        val count = state.attempts.count { it.price.currency == currency && it.unresolved }
        return "${UsageMoney.format(spent)} $currency estimated / reconciled · ${UsageMoney.format(held)} reserved" +
            (limit?.let { " · ${UsageMoney.format((it - spent - held).coerceAtLeast(0))} remaining" } ?: " · no local budget") +
            " · $count unresolved" + if(limit != null && spent > limit) " · OVER BUDGET" else ""
    }
    fun routeSummary(requests: List<UsageAttempt>): String {
        val known = requests.mapNotNull { it.charged }.fold(0L, Math::addExact)
        return "${requests.first().model}: ${requests.size} requests · ${UsageMoney.format(known)} ${requests.first().price.currency} known · ${requests.count { it.charged == null }} costs unknown"
    }
    fun detail(a: UsageAttempt): String = "${a.status} · input ${a.input ?: "unknown"} / output ${a.output ?: "unknown"} tokens\n" +
        (a.charged?.let { "${UsageMoney.format(it)} ${a.price.currency} ${if(a.reconciled != null) "manually reconciled" else if(a.price.local) "local API cost" else "estimated"}" }
            ?: "Cost unknown") + (a.elapsedMs?.let { " · ${it} ms" } ?: "") +
        "\n${if(a.price.gateway) "Gateway · downstream usage unavailable" else a.price.source}" +
        if(!a.price.local) "\nRates / million: ${a.price.inputPerMillion?.let(UsageMoney::format) ?: "unknown"} input, ${a.price.outputPerMillion?.let(UsageMoney::format) ?: "unknown"} output ${a.price.currency}" +
            (if(a.price.checkedAt > 0) " · reviewed ${java.time.Instant.ofEpochMilli(a.price.checkedAt)}" else " · not reviewed") else ""
}
