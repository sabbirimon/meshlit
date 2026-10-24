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
    val models=com.meshlit.workspace.RecommendedModelCatalog.models.map {
        CompactModel(it.id,it.name,it.purpose,it.repo,it.revision,it.fileName,it.bytes,it.sha256,it.tier,it.reasoning)
    }

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
