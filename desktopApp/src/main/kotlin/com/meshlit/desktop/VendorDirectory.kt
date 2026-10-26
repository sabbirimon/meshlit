package com.meshlit.desktop

import java.net.URI
import java.util.prefs.Preferences

internal data class VendorPage(val description: String, val dashboard: String, val catalog: String, val guide: String, val billing: String = "", val local: Boolean = false)
internal val vendorPages = mapOf(
    "OpenRouter" to VendorPage("Compare hosted models and manage your OpenRouter account.", "https://openrouter.ai/settings/keys", "https://openrouter.ai/models", "https://openrouter.ai/docs/quickstart", "https://openrouter.ai/activity"),
    "Hugging Face" to VendorPage("Explore the model Hub and configure Inference Providers for your account.", "https://huggingface.co/settings/inference-providers", "https://huggingface.co/models", "https://huggingface.co/docs/inference-providers", "https://huggingface.co/settings/billing"),
    "OpenAI" to VendorPage("Manage API projects, keys and models in the OpenAI platform.", "https://platform.openai.com/api-keys", "https://platform.openai.com/docs/models", "https://platform.openai.com/docs/quickstart", "https://platform.openai.com/usage"),
    "OmniRoute" to VendorPage("Manage providers, routes and endpoint keys in your running OmniRoute dashboard.", "http://127.0.0.1:20128", "https://www.omniroute.online/", "https://github.com/cheaper-inference/OmniRoute", local = true),
    "FreeLLMAPI" to VendorPage("Manage provider keys and routing in your own FreeLLMAPI installation. Personal experimentation only.", "http://127.0.0.1:3001", "https://freellmapi.co/models", "https://github.com/tashfeenahmed/freellmapi", local = true),
)
/** Browser navigation only: never transfer API credentials, queries or URL user-info. */
internal fun dashboardUri(value: String): URI {
    require(value.length in 1..2048)
    val uri = URI(value)
    require(uri.host != null && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null)
    require(uri.scheme == "https" || uri.scheme == "http" && uri.host in setOf("127.0.0.1", "[::1]", "::1"))
    return uri
}
internal class VendorDashboardAddresses(private val store: Preferences = Preferences.userRoot().node("com/meshlit/vendor-dashboards")) {
    fun load(service: String, fallback: String): String = runCatching { dashboardUri(store.get(service, fallback)).toString() }.getOrDefault(fallback)
    fun save(service: String, value: String) { require(service in vendorPages || service == "Custom compatible"); store.put(service, dashboardUri(value).toString()); store.flush() }
}
