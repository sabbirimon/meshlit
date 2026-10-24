package com.meshlit.models

import android.content.Context
import com.meshlit.core.trust.EncryptedCredentialStore

class ModelCredentials(context:Context) {
    private val store=EncryptedCredentialStore(context.applicationContext,"model-source-credentials")
    fun token():String=store.get("huggingface").orEmpty()
    fun inferenceToken():String=store.get("huggingface-inference").orEmpty()
    fun saveInferenceToken(value:String) {validate(value);if(value.isBlank()) store.remove("huggingface-inference") else store.put("huggingface-inference",value)}
    fun hostedConfig():com.meshlit.core.inference.models.HostedModelConfig=store.get("hosted-config")?.let {
        runCatching{kotlinx.serialization.json.Json.decodeFromString<com.meshlit.core.inference.models.HostedModelConfig>(it)}.getOrNull()
    } ?: com.meshlit.core.inference.models.HostedModelConfig()
    fun saveHostedConfig(config:com.meshlit.core.inference.models.HostedModelConfig) {config.validate();store.put("hosted-config",kotlinx.serialization.json.Json.encodeToString(com.meshlit.core.inference.models.HostedModelConfig.serializer(),config))}
    private fun validate(value:String) {require(value.isBlank() || value.startsWith("hf_") && value.length<=512 && value.none{it.isWhitespace()}) { "Enter a Hugging Face token" }}
    fun saveToken(value:String) {
        require(value.isBlank() || value.startsWith("hf_") && value.length<=512 && value.none { it.isWhitespace() }) { "Enter a Hugging Face read token" }
        if(value.isBlank()) store.remove("huggingface") else store.put("huggingface",value)
    }
}
