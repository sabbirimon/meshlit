package com.meshlit.desktop

import com.meshlit.workspace.UsageLedger
import com.meshlit.workspace.UsagePrice
import com.meshlit.core.mcp.control.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.net.http.*
import java.time.Duration
import java.util.concurrent.*

/** Private stdio backend for the Code OSS extension and explicit model routes. */
internal fun runIdeBackend() {
    val vault = DesktopUsage()
    val usageSession = IdeUsageSession(vault)
    val engine = LocalEngine()
    val enginePreferences = EnginePreferences()
    val systemMonitor=StudioSystemMonitor(engine,enginePreferences)
    val localSession = IdeEngineSession({systemMonitor.apply(enginePreferences.load())}, engine::isRunning) { options, model ->
        val path = model.path(desktopResourceDirectory())
        check(model.verifyPresence(desktopResourceDirectory())) { "Bundled model missing or incorrect size" }
        engine.start(customModel = path, expectedModelSha256 = model.sha256, options = options, thinkingTemplates = model.thinkingSupported)
    }
    val worker = Executors.newSingleThreadExecutor()
    val controlWorker = Executors.newSingleThreadExecutor()
    val connectionWorker = Executors.newFixedThreadPool(2)
    val deadlines = Executors.newSingleThreadScheduledExecutor()
    val jobs = ConcurrentHashMap<String, FutureTask<Unit>>()
    val http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()
    val output = Any()
    fun emit(id: String, body: JsonObject) = synchronized(output) {
        println(buildJsonObject { put("id", id); body.forEach { (k, v) -> put(k, v) } })
        System.out.flush()
    }
    fun complete(request:JsonObject,id:String):JsonObject {
        var attempt:String?=null
        var lease:IdeUsageSession.Lease?=null
        try {
            val body = request.getValue("body").jsonObject
            require(body.toString().length <= 131072)
            val messages = body.getValue("messages").jsonArray
            require(messages.size in 1..64)
            val route = request["route"] as? JsonObject
            val local = route == null || route["kind"]?.jsonPrimitive?.content == "local"
            lease = usageSession.acquire(local) // Before engine load, network contact or budget admission.
            val ledger = lease.ledger
            val effort = request["effort"]?.jsonPrimitive?.content ?: "auto"
            val selectedModel = if (local) StudioBundledModels.resolve(route?.get("modelId")?.jsonPrimitive?.content) else null
            val thinkingMode = request["thinking"]?.jsonPrimitive?.content ?: "auto"
            val thinking = studioThinking(selectedModel, thinkingMode)
            require(!local || effort == "auto") { "Local reasoning effort is unavailable" }
            if(local && systemMonitor.needsReload())engine.stop()
            val active = if (local) localSession.load(selectedModel!!.id) else null
            val maxOutput = request["outputLimit"]?.jsonPrimitive?.int?.also{require(it in 1..2048)}?.coerceAtMost(ideOutputBudget(active, thinking)) ?: ideOutputBudget(active, thinking)
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
            val streaming = local && body["stream"]?.jsonPrimitive?.booleanOrNull == true
            val allowedToolNames = (body["tools"] as? JsonArray)?.mapNotNull {
                (it as? JsonObject)?.get("function")?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
            }?.toSet().orEmpty()
            val outbound = ideCompletionBody(body, route, effort, model, maxOutput, streaming, thinkingMode)
            attempt = ledger.begin(UsageLedger.route(if(local) "local:studio-ide" else endpoint.base.toString(), selectedModel?.id ?: model),
                selectedModel?.id ?: model, price,
                outbound.toString().length.toLong() * 4 + 1024, maxOutput.toLong())
            val seconds = if (thinking) 300L else 120L
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
            val call = http.sendAsync(HttpRequest.newBuilder(endpoint.route("chat/completions"))
                .timeout(Duration.ofSeconds(seconds)).apply { if(token.isNotBlank()) header("Authorization", "Bearer $token") }
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(outbound.toString())).build(),
                HttpResponse.BodyHandlers.ofInputStream())
            val response = try { call.get(seconds + 30, TimeUnit.SECONDS) } finally { if (!call.isDone) call.cancel(true) }
            val remaining = (deadline - System.nanoTime()).coerceAtLeast(0)
            val timeout = deadlines.schedule({ response.body().close() }, remaining, TimeUnit.NANOSECONDS)
            val parsed = try { response.body().use { stream ->
                check(response.statusCode() == 200) { "Model rejected the request" }
                if (streaming) {
                    require(response.headers().firstValue("Content-Type").orElse("").substringBefore(';').trim() == "text/event-stream")
                    readIdeCompletionStream(stream, allowedToolNames) { chunk -> emit(id, buildJsonObject { put("chunk", chunk) }) }
                } else {
                    val bytes = stream.readNBytes(2_000_001); require(bytes.size <= 2_000_000)
                    boundedJson(bytes.decodeToString(throwOnInvalidSequence = true), 2_000_000)
                }
            } } finally { timeout.cancel(false) }
            // Valid calls to declared tools may complete an SDK round without answer text.
            if (selectedModel?.thinkingSupported == true) requireStudioFinalAnswer(parsed, allowedToolNames)
            val usage = parsed["usage"] as? JsonObject
            ledger.finish(attempt!!, "complete", usage?.get("prompt_tokens")?.jsonPrimitive?.longOrNull,
                usage?.get("completion_tokens")?.jsonPrimitive?.longOrNull)
            attempt = null
            return buildJsonObject {
                put("response", parsed)
                selectedModel?.let { put("modelId", it.id); put("label", it.label); put("thinking", thinking); put("context", active!!.context) }
            }
        } catch(e:Exception) {
            attempt?.let{runCatching{lease?.ledger?.finish(it,if(Thread.currentThread().isInterrupted)"cancelled" else "failed",null,null)}}
            throw e
        } finally { lease?.close() }
    }
    fun newConnectionHost():DesktopDeviceConnections {
        var epoch:Long?=null
        return DesktopDeviceConnections(
            read={epoch=vault.teamEpoch();vault.deviceConnectionDocument()},
            save={vault.saveDeviceConnectionDocument(it,checkNotNull(epoch))},
            checkSession={val current=vault.teamEpoch();check(epoch==null || epoch==current){"Owner session changed; refresh connection choices"}},
            knownNodes={NodeStore().load()},
        )
    }
    val connectionHost=java.util.concurrent.atomic.AtomicReference(newConnectionHost())
    var teamEpoch:Long?=null
    val teams=DesktopTeams(
        read={teamEpoch=vault.teamEpoch();vault.teamDocument()},
        save={vault.saveTeamDocument(it,checkNotNull(teamEpoch))},
    ){member,prompt,route->
        val request=buildJsonObject{
            put("outputLimit",256);if(route!=null)put("route",route)
            put("body",buildJsonObject{put("stream",false);put("temperature",0);put("messages",buildJsonArray{
                add(buildJsonObject{put("role","system");put("content","You are Meshlit. Follow the fixed team protocol and provide concise analysis.")})
                add(buildJsonObject{put("role","user");put("content",prompt)})
            })})
        }
        val pending=worker.submit<JsonObject>{runBlocking{com.meshlit.core.inference.CpuWorkGate.run("Studio team generation"){complete(request,java.util.UUID.randomUUID().toString())}}}
        try {
            val response=runInterruptible(Dispatchers.IO){pending.get()}
            response.getValue("response").jsonObject.getValue("choices").jsonArray.first().jsonObject.getValue("message").jsonObject.getValue("content").jsonPrimitive.content
                .also{require(it.length<=12000)}
        } finally {if(!pending.isDone)pending.cancel(true)}
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
                try {
                    val result = when (operation) {
                        "session_usage" -> buildJsonObject { put("ready", true); put("usageMode", usageSession.startSession()) }
                        "unlock" -> {
                            val pass = (request["password"] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty().toCharArray()
                            try { connectionHost.get().close();usageSession.unlock(pass);connectionHost.set(newConnectionHost()) } finally { pass.fill('\u0000') }
                            buildJsonObject { put("ready", true); put("usageMode", "protected") }
                        }
                        "local_models" -> StudioBundledModels.catalog()
                        "load_local" -> {
                            val model = StudioBundledModels.resolve(request["modelId"]?.jsonPrimitive?.content)
                            val session = localSession.load(model.id)
                            buildJsonObject {
                                put("ready", true); put("label", model.label); put("modelId", model.id); put("thinkingSupported", model.thinkingSupported)
                                put("context", session.context)
                            }
                        }
                        "usage" -> usageSession.view()
                        "budget" -> {
                            val ledger = usageSession.protectedLedger()
                            updateIdeBudget(ledger, request)
                            ideUsage(ledger)
                        }
                        "complete" -> runBlocking{com.meshlit.core.inference.CpuWorkGate.run("Studio generation"){complete(request,id)}}
                        "device_connection_sync" -> connectionHost.get().sync(request.getValue("sources").jsonArray)
                        "device_connection_view" -> connectionHost.get().view()
                        "device_connection_policy" -> connectionHost.get().configure(request.getValue("policy").jsonObject)
                        "device_connection_action" -> {
                            val owner=request.getValue("owner").jsonPrimitive.content
                            require(owner=="settings" || owner.matches(Regex("chat:[a-f0-9-]{36}")))
                            runBlocking{connectionHost.get().action(owner,request.getValue("arguments").jsonObject,request["credential"]?.jsonPrimitive?.content.orEmpty())}
                        }
                        "device_connection_close_owner" -> {connectionHost.get().cancelOwner(request.getValue("owner").jsonPrimitive.content);buildJsonObject{put("cancelRequested",true)}}
                        "system_resources" -> systemMonitor.sample()
                        "cpu_policy_get" -> buildJsonObject{put("policy",systemMonitor.policy())}
                        "cpu_policy_set" -> systemMonitor.save(request)
                        "system_model_stop" -> {engine.stop();buildJsonObject{put("stopped",true)}}
                        "team_catalog" -> DesktopTeams.catalog()
                        "team_status" -> teams.status()
                        "team_agent_status" -> teams.status(agentOnly=true)
                        "team_start" -> {
                            val plan=Json.decodeFromJsonElement(TeamPlan.serializer(),request.getValue("plan"))
                            val run=teams.start(plan,request["routes"] as? JsonObject ?: JsonObject(emptyMap()))
                            buildJsonObject{put("runId",run)}
                        }
                        "team_agent_start" -> {
                            require(request.keys==setOf("id","op","goal","requestId"))
                            val run=teams.startDelegated(request.getValue("goal").jsonPrimitive.content,request.getValue("requestId").jsonPrimitive.content)
                            buildJsonObject{put("runId",run);put("replayed",false)}
                        }
                        "team_delegation" -> {
                            val plan=(request["plan"] as? JsonObject)?.let{Json.decodeFromJsonElement(TeamPlan.serializer(),it)}
                            teams.delegate(plan,request["routes"] as? JsonObject ?: JsonObject(emptyMap()));teams.status()
                        }
                        "team_control","team_agent_control" -> {
                            teams.control(request.getValue("runId").jsonPrimitive.content,request.getValue("action").jsonPrimitive.content,operation=="team_agent_control")
                            buildJsonObject{put("controlRequested",true)}
                        }
                        "team_revoke_profile" -> {teams.revokeProfile(request.getValue("profileId").jsonPrimitive.content);buildJsonObject{put("revoked",true)}}
                        "team_revoke_source" -> {teams.revokeSource(request.getValue("sourceId").jsonPrimitive.content);buildJsonObject{put("revoked",true)}}
                        else -> error("Unsupported operation")
                    }
                    emit(id, result)
                } catch (e: Exception) {
                    emit(id, buildJsonObject { put("usageMode", usageSession.mode()); put("error", if (e is StudioNoFinalAnswer) e.message!! else when (operation) {
                        "load_local" -> "Bundled model could not load. Check packaged model/engine files and available memory. No online fallback was attempted."
                        "device_connection_sync","device_connection_view","device_connection_policy","device_connection_action","device_connection_close_owner" -> "Connection check blocked or failed. Unlock Usage, refresh choices and review the saved source's identity and connection approval. No new device or action scope was enrolled."
                        "session_usage" -> "Local session chat could not start. Finish active requests and try again."
                        "unlock" -> "Cannot unlock usage: check passphrase and close the older Studio if it owns this vault."
                        "usage" -> "Start local session chat or unlock protected Usage to view saved history and manage budgets."
                        "team_start","team_agent_start","team_status","team_agent_status","team_delegation","team_control","team_agent_control","team_revoke_profile","team_revoke_source" -> "Team action failed. Unlock Usage; check saved delegation, active limits, model sources and role configuration."
                        "budget" -> "Budget was not saved. Unlock Usage and use a positive monthly limit with a per-request limit no greater than it, or leave both amounts blank to remove that currency limit."
                        else -> "Request failed or was cancelled. Check Usage unlock, free-only/budget rules, model context, endpoint and credentials. Opaque gateways cannot use local spending budgets."
                    }) })
                } finally { jobs.remove(id) }
            }
            jobs[id] = task
            if(operation=="device_connection_action")connectionWorker.execute(task)
            else if(operation.startsWith("team_") || operation.startsWith("device_connection_") || operation in setOf("system_resources","cpu_policy_get","cpu_policy_set","system_model_stop"))controlWorker.execute(task) else worker.execute(task)
        }
    } finally {
        connectionHost.get().close();teams.close();jobs.values.forEach { it.cancel(true) }; connectionWorker.shutdownNow();controlWorker.shutdownNow();worker.shutdownNow()
        engine.close(); worker.awaitTermination(5, TimeUnit.SECONDS); usageSession.lock()
        deadlines.shutdownNow()
    }
}
