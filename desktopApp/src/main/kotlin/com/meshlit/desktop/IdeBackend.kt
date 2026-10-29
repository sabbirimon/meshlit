package com.meshlit.desktop

import com.meshlit.workspace.UsageLedger
import com.meshlit.workspace.UsagePrice
import kotlinx.serialization.json.*
import java.net.http.*
import java.time.Duration
import java.util.concurrent.*

/** Private stdio backend for the Code OSS extension and explicit model routes. */
internal fun runIdeBackend() {
    val vault = DesktopUsage()
    val engine = LocalEngine()
    var session: LocalSession? = null
    val worker = Executors.newSingleThreadExecutor()
    val deadlines = Executors.newSingleThreadScheduledExecutor()
    val jobs = ConcurrentHashMap<String, FutureTask<Unit>>()
    val http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()
    val output = Any()
    fun emit(id: String, body: JsonObject) = synchronized(output) {
        println(buildJsonObject { put("id", id); body.forEach { (k, v) -> put(k, v) } })
        System.out.flush()
    }
    try {
        // A bounded line reader avoids unbounded allocations on the private IPC channel.
        val input = System.`in`.bufferedReader()
        while (true) {
            val line = StringBuilder()
            var next = input.read()
            if (next == -1) break
            while (next != -1 && next != 10) {
                require(line.length < 600_000) { "IPC request too large" }
                line.append(next.toChar()); next = input.read()
            }
            val request = boundedJson(line.toString(), 600_000)
            val id = request.getValue("id").jsonPrimitive.content
            require(id.matches(Regex("[a-f0-9-]{36}")))
            val operation = request.getValue("op").jsonPrimitive.content
            if (operation == "cancel") { jobs.remove(request["target"]?.jsonPrimitive?.content)?.cancel(true); continue }
            if (jobs.size >= 16) { emit(id, buildJsonObject { put("error", "Too many queued requests") }); continue }
            val task = FutureTask<Unit> {
                var attempt: String? = null
                try {
                    val result = when (operation) {
                        "unlock" -> {
                            val pass = request.getValue("password").jsonPrimitive.content.toCharArray()
                            try { vault.unlock(pass) } finally { pass.fill('\u0000') }
                            buildJsonObject { put("ready", true) }
                        }
                        "load_local" -> {
                            if (session == null || !engine.isRunning()) session = engine.start()
                            buildJsonObject {
                                put("ready", true); put("label", DesktopStarter.label)
                                put("context", session!!.context)
                            }
                        }
                        "usage" -> ideUsage(vault.journal())
                        "budget" -> {
                            val ledger = vault.journal()
                            updateIdeBudget(ledger, request)
                            ideUsage(ledger)
                        }
                        "complete" -> {
                            val ledger = vault.journal() // Fail before model load or request when locked.
                            val body = request.getValue("body").jsonObject
                            require(body.toString().length <= 131072)
                            val messages = body.getValue("messages").jsonArray
                            require(messages.size in 1..64)
                            val route = request["route"] as? JsonObject
                            val local = route == null || route["kind"]?.jsonPrimitive?.content == "local"
                            val active = if (local) session?.takeIf { engine.isRunning() } ?: engine.start().also { session = it } else null
                            val endpoint = active?.endpoint ?: HostEndpoint.parse(route!!.getValue("endpoint").jsonPrimitive.content)
                            val token = active?.token ?: route?.get("token")?.jsonPrimitive?.content.orEmpty()
                            require(token.length <= 8192 && token.none(Char::isISOControl))
                            require(endpoint.loopback || token.isNotBlank()) { "Remote routes require a token" }
                            val model = if(local) DesktopStarter.alias else route!!.getValue("model").jsonPrimitive.content
                            val price = if(local) UsagePrice(local = true, source = "Owned local IDE engine") else UsagePrice(
                                gateway = route?.get("gateway")?.jsonPrimitive?.booleanOrNull ?: true,
                                inputPerMillion = route?.get("inputRate")?.jsonPrimitive?.longOrNull,
                                outputPerMillion = route?.get("outputRate")?.jsonPrimitive?.longOrNull,
                                checkedAt = route?.get("checkedAt")?.jsonPrimitive?.longOrNull ?: 0,
                                source = "User-reviewed IDE connection")
                            val outbound = JsonObject(body.toMutableMap().apply {
                                put("model", JsonPrimitive(model))
                                put("stream", JsonPrimitive(false)); remove("stream_options")
                                remove("max_completion_tokens")
                                put("max_tokens", JsonPrimitive(1024))
                            })
                            attempt = ledger.begin(UsageLedger.route(if(local) "local:studio-ide" else endpoint.base.toString(), model),
                                model, price,
                                outbound.toString().length.toLong() * 4 + 1024, 1024)
                            val call = http.sendAsync(HttpRequest.newBuilder(endpoint.route("chat/completions"))
                                .timeout(Duration.ofSeconds(120)).apply { if(token.isNotBlank()) header("Authorization", "Bearer $token") }
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(outbound.toString())).build(),
                                HttpResponse.BodyHandlers.ofInputStream())
                            val response = try { call.get(150, TimeUnit.SECONDS) } finally { if (!call.isDone) call.cancel(true) }
                            val timeout = deadlines.schedule({ response.body().close() }, 120, TimeUnit.SECONDS)
                            val parsed = try { response.body().use { stream ->
                                check(response.statusCode() == 200) { "Model rejected the request" }
                                val bytes = stream.readNBytes(2_000_001); require(bytes.size <= 2_000_000)
                                boundedJson(bytes.decodeToString(throwOnInvalidSequence = true), 2_000_000)
                            } } finally { timeout.cancel(false) }
                            val usage = parsed["usage"] as? JsonObject
                            ledger.finish(attempt!!, "complete", usage?.get("prompt_tokens")?.jsonPrimitive?.longOrNull,
                                usage?.get("completion_tokens")?.jsonPrimitive?.longOrNull)
                            attempt = null
                            buildJsonObject { put("response", parsed) }
                        }
                        else -> error("Unsupported operation")
                    }
                    emit(id, result)
                } catch (e: Exception) {
                    attempt?.let { runCatching { vault.journal().finish(it, if (Thread.currentThread().isInterrupted) "cancelled" else "failed", null, null) } }
                    emit(id, buildJsonObject { put("error", when (operation) {
                        "load_local" -> "Bundled model could not load. Check packaged model/engine files and available memory. No online fallback was attempted."
                        "unlock" -> "Cannot unlock usage: check passphrase and close the older Studio if it owns this vault."
                        "usage" -> "Unlock your Meshlit usage ledger to view history and manage budgets."
                        "budget" -> "Budget was not saved. Unlock Usage and use a positive monthly limit with a per-request limit no greater than it, or leave both amounts blank to remove that currency limit."
                        else -> "Request failed or was cancelled. Check Usage unlock, free-only/budget rules, model context, endpoint and credentials. Opaque gateways cannot use local spending budgets."
                    }) })
                } finally { jobs.remove(id) }
            }
            jobs[id] = task; worker.execute(task)
        }
    } finally {
        jobs.values.forEach { it.cancel(true) }; worker.shutdownNow()
        engine.close(); worker.awaitTermination(5, TimeUnit.SECONDS); vault.lock()
        deadlines.shutdownNow()
    }
}
