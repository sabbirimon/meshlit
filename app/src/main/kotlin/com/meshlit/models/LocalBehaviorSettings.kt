package com.meshlit.models
import android.content.Context
import com.meshlit.core.inference.models.LocalModelBehavior
import com.meshlit.core.trust.EncryptedCredentialStore
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
class LocalBehaviorSettings(context:Context) {
    private val store=EncryptedCredentialStore(context,"local-model-behavior")
    private val json=Json{ignoreUnknownKeys=true}
    private val _state=MutableStateFlow(runCatching{store.get("policy")?.let{json.decodeFromString<LocalModelBehavior>(it).also{it.validate()}}}.getOrNull() ?: LocalModelBehavior())
    val state=_state.asStateFlow()
    @Synchronized fun save(policy:LocalModelBehavior){policy.validate();store.put("policy",json.encodeToString(policy));_state.value=policy}
}
