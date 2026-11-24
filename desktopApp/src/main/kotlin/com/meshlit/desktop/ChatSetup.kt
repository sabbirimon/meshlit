package com.meshlit.desktop

internal val chatSetupLabels = mapOf(
    "local" to "Local model", "cloud" to "Online provider", "ollama" to "Ollama",
    "lmstudio" to "LM Studio", "bionic" to "LM Studio Bionic", "device" to "Pair a device",
    "management" to "Dashboards", "ide" to "Coding IDE"
)
private val setupCommands = mapOf(
    "device" to Regex("(?:pair|connect(?: to)?) (?:a |my |the )?(?:local |remote )?(?:device|phone|laptop|desktop)"),
    "local" to Regex("(?:load|choose|use|select) (?:a |the |my )?local model"),
    "cloud" to Regex("(?:connect(?: to)?|choose|use|select) (?:an? |the )?(?:online|cloud) (?:model|provider)"),
    "ollama" to Regex("(?:connect(?: to)?|use) ollama"),
    "lmstudio" to Regex("(?:connect(?: to)?|use) lm studio"),
    "bionic" to Regex("(?:connect(?: to)?|use) (?:lm (?:studio )?)?bionic"),
    "management" to Regex("open (?:the )?(?:management )?dashboard"),
    "ide" to Regex("open (?:the )?(?:coding )?ide")
)
private val prefix = Regex("^(?:please|can you|could you) +")
private val whitespace = Regex("\\s+")

internal fun chatSetupIntent(input: String): String? {
    if (input.length > 100) return null
    val text = input.trim().lowercase().replace(whitespace, " ").replace(prefix, "").trimEnd('.', '!', '?')
    return setupCommands.entries.firstOrNull { it.value.matches(text) }?.key
}
