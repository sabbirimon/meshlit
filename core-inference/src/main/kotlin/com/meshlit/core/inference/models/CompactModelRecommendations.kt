package com.meshlit.core.inference.models

/** Pinned public artifacts. A hardware estimate is not a runtime/quality benchmark. */
data class CompactModel(val id:String,val name:String,val purpose:String,val repo:String,val revision:String,
    val fileName:String,val bytes:Long,val sha256:String,val tier:Int,val reasoning:Boolean) {
    val url get()="https://huggingface.co/$repo/resolve/$revision/$fileName"
    val artifact get()=HubArtifact(repo,revision,fileName,bytes,sha256,url)
}

data class CompactModelAdvice(val model:CompactModel,val hardwareFit:Boolean,val readyNow:Boolean,
    val storageNeeded:Long,val memoryEstimate:Long,val context:Int,val reason:String)

object CompactModelRecommendations {
    private const val MIB=1024L*1024
    private const val GIB=1024L*MIB
    val models=listOf(
        CompactModel("qwen3-small","Qwen3 0.6B · Q4_K_M","Smallest download; short reasoning and everyday text",
            "unsloth/Qwen3-0.6B-GGUF","50968a4468ef4233ed78cd7c3de230dd1d61a56b","Qwen3-0.6B-Q4_K_M.gguf",396705472,
            "ac2d97712095a558e31573f62f466a3f9d93990898b0ec79d7c974c1780d524a",0,true),
        CompactModel("qwen3-balanced","Qwen3 1.7B · Q4_K_M","More capacity for reasoning, writing and code",
            "unsloth/Qwen3-1.7B-GGUF","d7f544eead698dbd1f15126ef60b45a1e1933222","Qwen3-1.7B-Q4_K_M.gguf",1107409472,
            "b139949c5bd74937ad8ed8c8cf3d9ffb1e99c866c823204dc42c0d91fa181897",1,true),
        CompactModel("qwen25-general","Qwen2.5 1.5B Instruct · Q4_K_M","General chat alternative using the existing Qwen2 backend",
            "Qwen/Qwen2.5-1.5B-Instruct-GGUF","91cad51170dc346986eccefdc2dd33a9da36ead9","qwen2.5-1.5b-instruct-q4_k_m.gguf",1117320736,
            "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e",1,false),
        CompactModel("qwen3-capable","Qwen3 4B · Q4_K_M","Larger option for devices with more memory; slower and heavier",
            "Qwen/Qwen3-4B-GGUF","bc640142c66e1fdd12af0bd68f40445458f3869b","Qwen3-4B-Q4_K_M.gguf",2497280256,
            "7485fe6f11af29433bc51cab58009521f205840f5b4ae3a32fa7f92e8534fdf5",2,true),
    )
    fun assess(device:DeviceRuntimeSnapshot,smallestFirst:Boolean=false):List<CompactModelAdvice> {
        val plan=DeviceRuntimePolicy.plan(device)
        // Device class chooses an upper bound, while CURRENT headroom can lower the suggestion.
        val tier=when { device.lowRam || device.cpuCores<4 || device.api<29 || device.totalRam<4*GIB -> 0
            device.totalRam<8*GIB -> 1
            else -> 2 }
        val possibleBudget=(device.totalRam*0.6).toLong()-256*MIB
        return models.map { model ->
            val memory=(model.bytes*1.15).toLong()+384*MIB // conservative KV/compute allowance at short context
            val storage=model.bytes*2+256*MIB // download plus possible SDK runtime copy and reserve
            val hardware=plan.localInferenceSupported && model.tier<=tier && memory<possibleBudget
            val ready=hardware && plan.allowHeavyWork && memory<plan.memoryBudget && device.freeStorage>=storage
            val reason=when {
                !plan.localInferenceSupported -> "No supported local engine for this OS / CPU architecture"
                !hardware -> "Above this device's conservative CPU / RAM class"
                device.freeStorage<storage -> "Free more storage before downloading"
                !plan.allowHeavyWork && (device.thermal ?: 0)>=3 -> "Device is hot; wait before loading"
                memory>=plan.memoryBudget -> "Hardware candidate; unload the current model or close apps before loading"
                !plan.allowHeavyWork -> "Not enough current memory headroom to load"
                else -> "Estimated fit now; actual load and response still need verification"
            }
            CompactModelAdvice(model,hardware,ready,storage,memory,if(device.runAnywhereInstalled) 2048 else minOf(plan.recommendedContext,2048),reason)
        }.sortedWith(compareByDescending<CompactModelAdvice>{it.readyNow}.thenByDescending{it.hardwareFit}
            .thenBy { if(smallestFirst) it.model.bytes else -it.model.tier.toLong() }
            .thenByDescending{it.model.reasoning}.thenBy{it.model.bytes})
    }
}
