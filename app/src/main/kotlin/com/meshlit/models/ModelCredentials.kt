package com.meshlit.models

import android.content.Context
import com.meshlit.core.inference.models.isHubBearer
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
    private fun validate(value:String) {require(value.isEmpty() || isHubBearer(value)) { "Invalid Hugging Face access token" }}

    fun browserLogin():Boolean?=when(store.get("huggingface-source")){ "browser"->true; "manual"->false; else->null }
    fun saveToken(value:String,browserLogin:Boolean=false) {
        validate(value)
        store.updateCommitted(mapOf("huggingface" to value.takeIf { it.isNotEmpty() },
            "huggingface-source" to value.takeIf { it.isNotEmpty() }?.let { if(browserLogin) "browser" else "manual" }))
    }
}
