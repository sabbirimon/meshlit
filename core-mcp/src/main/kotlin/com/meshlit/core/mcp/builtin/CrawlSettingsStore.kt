package com.meshlit.core.mcp.builtin

import android.content.Context
import com.meshlit.core.trust.EncryptedCredentialStore
import kotlinx.serialization.json.*

/** One encrypted configuration record keeps host and credential reads atomic. */
class CrawlSettingsStore(context: Context) {
    private val secrets by lazy {
        EncryptedCredentialStore(context.applicationContext, "meshlit_crawler_credentials")
    }
    fun load(): CrawlSettings {
        val raw = secrets.get("configuration") ?: return CrawlSettings()
        val value = Json.parseToJsonElement(raw).jsonObject
        return CrawlSettings(
            enabled = value["enabled"]?.jsonPrimitive?.booleanOrNull ?: false,
            endpoint = value["endpoint"]?.jsonPrimitive?.content.orEmpty(),
            token = value["token"]?.jsonPrimitive?.content.orEmpty(),
        )
    }
    @Synchronized
    fun save(settings: CrawlSettings, replacementToken: String = "") {
        val endpoint = if (settings.endpoint.isBlank() && !settings.enabled) ""
            else validateCrawlerEndpoint(settings.endpoint)
        require(replacementToken.isEmpty() ||
            (replacementToken.length >= 32 && replacementToken.all { it.code in 33..126 })
        ) { "Token must contain at least 32 ASCII characters without whitespace" }
        val current = load()
        val token = replacementToken.ifEmpty { if (endpoint == current.endpoint) current.token else "" }
        require(!settings.enabled || token.isNotBlank()) { "A token is required for this host" }
        write(CrawlSettings(settings.enabled, endpoint, token))
    }
    @Synchronized
    fun disable() {
        val current = load()
        write(CrawlSettings(false, current.endpoint, current.token))
    }
    private fun write(value: CrawlSettings) {
        secrets.put("configuration", buildJsonObject {
            put("enabled", value.enabled); put("endpoint", value.endpoint); put("token", value.token)
        }.toString())
    }
}
