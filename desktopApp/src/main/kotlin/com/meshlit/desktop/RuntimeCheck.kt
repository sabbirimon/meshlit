package com.meshlit.desktop

import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path

/** Explicit developer checks. No fake snapshots, automated commands or telemetry. */
internal fun monitorCheck(output: String) {
    val reader = ResourceReader()
    val first = reader.sample(); Thread.sleep(2100); val second = reader.sample()
    check(first.total > 0 && second.total > 0 && second.available in 0..second.total)
    check(second.at > first.at && second.cpu != null && second.processCount > 0)
    Files.writeString(Path.of(output), buildJsonObject {
        put("hostOs", System.getProperty("os.name")); put("hostArch", System.getProperty("os.arch"))
        put("firstSampleAt", first.at.toString()); put("secondSampleAt", second.at.toString())
        put("physicalMemoryBytes", second.total); put("availableMemoryBytes", second.available)
        put("cpuLoadFraction", checkNotNull(second.cpu)); put("processCountReported", second.processCount)
        put("processRowsAvailable", second.processes.size)
        put("scope", "Actual OSHI local sampling only; no process termination, remote sensors, energy cost or accelerator execution proof")
    }.toString()+"\n")
    println("Actual local OSHI CPU/memory/process sampling passed.")
}
internal fun hubCheck(output: String) {
    HubClient().use { client ->
        val results = client.search("Qwen2.5-1.5B-Instruct")
        val model = client.details("Qwen/Qwen2.5-1.5B-Instruct-GGUF")
        val file = model.files.single { it.name == DesktopStarter.filename }
        check(file.size == DesktopStarter.size && file.sha256 == DesktopStarter.sha256)
        Files.writeString(Path.of(output), buildJsonObject {
            put("repository", model.id); put("observedRevision", model.revision); put("file", file.name)
            put("sizeBytes", file.size); put("sha256", file.sha256); put("searchResultCount", results.size)
            put("scope", "Real HTTPS metadata and known model hash/size; no new weight download, gated access or provider inference proof")
        }.toString()+"\n")
    }
    println("Actual Hugging Face search and model metadata passed.")
}

internal fun engineOptionsCheck(output: String) {
    val results = mutableListOf<JsonObject>()
    for (keyType in listOf("f16", "q8_0", "q4_0")) {
        val engine = LocalEngine()
        try {
            val options = EngineOptions(context=1024,threads=minOf(2,Runtime.getRuntime().availableProcessors()),
                batchThreads=minOf(4,Runtime.getRuntime().availableProcessors()),batch=128,microBatch=64,keyCache=keyType)
            val session = engine.start(options=options)
            check(session.context==1024)
            val reply=StringBuilder()
            val usage=HostClient(session.endpoint,session.token).use {client -> client.generateDeterministic(
                DesktopStarter.alias,listOf(com.meshlit.workspace.ChatTurn("user","What is two plus two? Answer briefly.")),
                com.meshlit.workspace.GenerationBudget(64)) {reply.append(it)} }
            check(reply.isNotBlank() && (usage.outputTokens ?: 0)>0)
            results += buildJsonObject {
                put("keyCache",keyType);put("contextVerified",session.context);put("generationThreads",options.threads)
                put("batchThreads",options.batchThreads);put("batch",options.batch);put("microBatch",options.microBatch)
                put("estimatedKvBytes",session.admission.kvBytes);put("outputTokens",checkNotNull(usage.outputTokens))
                put("backend",session.backend);put("arithmeticAnswerObservedCorrect",reply.toString().trim().lowercase().let {it=="4" || it.startsWith("two plus two equals four") || it.startsWith("two plus two is four")})
                put("endToEndTokensPerSecond",checkNotNull(usage.tokensPerSecond));put("answer",reply.toString())
            }
        } finally {engine.close()}
        Thread.sleep(2500)
    }
    Files.writeString(Path.of(output), buildJsonObject {
        put("host",System.getProperty("os.name")+" "+System.getProperty("os.arch"))
        put("modelSha256",DesktopStarter.sha256);put("checks",JsonArray(results))
        put("scope","Real starter generation with three key-cache settings and native context identity; short smoke cases, not a controlled speed/quality comparison or physical cluster proof")
    }.toString()+"\n")
    println("Actual native cache-key controls produced text; inspect answers separately for quality.")
}


/** Serial, matched configuration trials; timings exclude load and a short warm-up. */
internal fun cpuBenchmarkCheck(output: String) {
    val results = mutableListOf<JsonObject>()
    val prompt = "Explain how to make reliable backups of important files. Give concrete steps and explain why each step matters."
    val options = EngineOptions(context=1024,threads=minOf(4,Runtime.getRuntime().availableProcessors()),
        batchThreads=minOf(8,Runtime.getRuntime().availableProcessors()),batch=512,microBatch=128,idleMinutes=0)
    for ((trial, mode) in listOf(CpuMode.BASELINE, CpuMode.AVX2, CpuMode.AVX2, CpuMode.BASELINE).withIndex()) {
        val engine = LocalEngine()
        try {
            val started = System.nanoTime()
            val session = engine.start(options=options.copy(cpuMode=mode))
            val loadNanos = System.nanoTime()-started
            val reply = StringBuilder()
            val usage = HostClient(session.endpoint,session.token).use { client ->
                client.generateDeterministic(DesktopStarter.alias,listOf(com.meshlit.workspace.ChatTurn("user","What is two plus two?")),com.meshlit.workspace.GenerationBudget(64)) {}
                client.generateDeterministic(DesktopStarter.alias,listOf(com.meshlit.workspace.ChatTurn("user",prompt)),com.meshlit.workspace.GenerationBudget(128)) {reply.append(it)}
            }
            check(reply.isNotBlank() && (usage.outputTokens ?: 0)>0 && session.context==1024)
            println("CPU trial ${trial+1}/4 · ${session.backend} · ${usage.outputTokens} reported tokens · ${usage.tokensPerSecond} end-to-end tokens/sec")
            results += buildJsonObject {
                put("trial",trial+1);put("backend",session.backend);put("loadSeconds",loadNanos/1e9)
                put("outputTokens",checkNotNull(usage.outputTokens));put("endToEndTokensPerSecond",checkNotNull(usage.tokensPerSecond))
                put("answerSha256",java.security.MessageDigest.getInstance("SHA-256").digest(reply.toString().toByteArray()).joinToString("") {"%02x".format(it)})
                put("answer",reply.toString())
            }
        } finally {engine.close()}
        Thread.sleep(2500)
    }
    Files.writeString(Path.of(output),buildJsonObject {
        put("host",System.getProperty("os.name")+" "+System.getProperty("os.arch"));put("logicalCpus",Runtime.getRuntime().availableProcessors())
        put("modelSha256",DesktopStarter.sha256);put("prompt",prompt);put("temperature",0);put("seed",42);put("outputLimit",128)
        put("context",options.context);put("threads",options.threads);put("promptThreads",options.batchThreads)
        put("batch",options.batch);put("microBatch",options.microBatch);put("keyCache",options.keyCache);put("trials",JsonArray(results))
        put("scope","Actual single-host CPU trials, baseline/AVX2/AVX2/baseline order with short warm-up. End-to-end native-reported completion usage; load excluded. Not isolated OS/thermal conditions, general quality, multi-model or physical sharding evidence.")
    }.toString()+"\n")
    println("Actual matched CPU baseline/AVX2 generation trials passed.")
}
