package com.meshlit.workspace

/** Public revision/hash pins shared by Android and Studio. Hardware advice is not a quality benchmark. */
data class RecommendedModelArtifact(val id:String,val name:String,val purpose:String,val repo:String,val revision:String,
    val fileName:String,val bytes:Long,val sha256:String,val tier:Int,val reasoning:Boolean)
object RecommendedModelCatalog {
    val models=listOf(
        RecommendedModelArtifact("qwen3-small","Qwen3 0.6B · Q4_K_M","Smallest download; short reasoning and everyday text",
            "unsloth/Qwen3-0.6B-GGUF","50968a4468ef4233ed78cd7c3de230dd1d61a56b","Qwen3-0.6B-Q4_K_M.gguf",396705472,
            "ac2d97712095a558e31573f62f466a3f9d93990898b0ec79d7c974c1780d524a",0,true),
        RecommendedModelArtifact("qwen3-balanced","Qwen3 1.7B · Q4_K_M","More capacity for reasoning, writing and code",
            "unsloth/Qwen3-1.7B-GGUF","d7f544eead698dbd1f15126ef60b45a1e1933222","Qwen3-1.7B-Q4_K_M.gguf",1107409472,
            "b139949c5bd74937ad8ed8c8cf3d9ffb1e99c866c823204dc42c0d91fa181897",1,true),
        RecommendedModelArtifact("qwen25-general","Qwen2.5 1.5B Instruct · Q4_K_M","General chat alternative using the existing Qwen2 backend",
            "Qwen/Qwen2.5-1.5B-Instruct-GGUF","91cad51170dc346986eccefdc2dd33a9da36ead9","qwen2.5-1.5b-instruct-q4_k_m.gguf",1117320736,
            "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e",1,false),
        RecommendedModelArtifact("qwen3-capable","Qwen3 4B · Q4_K_M","Larger option for devices with more memory; slower and heavier",
            "Qwen/Qwen3-4B-GGUF","bc640142c66e1fdd12af0bd68f40445458f3869b","Qwen3-4B-Q4_K_M.gguf",2497280256,
            "7485fe6f11af29433bc51cab58009521f205840f5b4ae3a32fa7f92e8534fdf5",2,true),
    )
}
