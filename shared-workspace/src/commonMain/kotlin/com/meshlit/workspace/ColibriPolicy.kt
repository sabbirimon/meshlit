package com.meshlit.workspace

enum class ColibriMode { OFF, ON, AUTO;
    companion object { fun parse(value: String?) = entries.firstOrNull { it.name == value } ?: OFF }
}
enum class ColibriRoute { LOCAL, HOST, BLOCKED }
data class ColibriDecision(val route: ColibriRoute, val reason: String)

/** Evidence is a recent model-list observation, never an invented GPU/power measurement. */
fun colibriDecision(mode: ColibriMode, access: Boolean, freshModel: Boolean,
                    localReady: Boolean, preferHost: Boolean = false): ColibriDecision = when {
    mode == ColibriMode.OFF -> ColibriDecision(ColibriRoute.LOCAL, "Colibri is off")
    mode == ColibriMode.ON && (!access || !freshModel) -> ColibriDecision(ColibriRoute.BLOCKED, "Authorize and refresh the configured Colibri model first")
    mode == ColibriMode.ON -> ColibriDecision(ColibriRoute.HOST, "Colibri selected by user")
    access && freshModel && (preferHost || !localReady) -> ColibriDecision(ColibriRoute.HOST, "Auto selected the authorized, recently observed host model")
    localReady -> ColibriDecision(ColibriRoute.LOCAL, "Auto retained the loaded local model")
    else -> ColibriDecision(ColibriRoute.BLOCKED, "No loaded local model or authorized, recently observed Colibri model")
}

/** Agent overrides are ephemeral and subordinate to saved human grants. */
class ColibriSessionModes(private val now: () -> Long) {
    private data class Override(val mode: ColibriMode, val expires: Long)
    private val overrides = mutableMapOf<String, Override>()
    fun request(chat: String, mode: ColibriMode, hostGrant: Boolean, chatGrant: Boolean) {
        require(hostGrant && chatGrant) { "Saved host and conversation agent grants are required" }
        require(chat.isNotBlank() && chat.length <= 80)
        overrides[chat] = Override(mode, now() + 30 * 60_000L)
    }
    fun effective(chat: String, humanMode: ColibriMode, hostGrant: Boolean, chatGrant: Boolean): ColibriMode {
        val item = overrides[chat]
        if (!hostGrant || !chatGrant || item == null || now() >= item.expires) { overrides.remove(chat); return humanMode }
        return item.mode
    }
    fun clear() = overrides.clear()
}
