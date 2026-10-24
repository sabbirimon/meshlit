package com.meshlit.desktop

/** Alters only the local system message; no authority, model-weight, or provider-policy changes. */
internal enum class ResponsePolicy(val label: String) {
    ASSISTANT("Meshlit instructions"), CUSTOM("My instructions"), MODEL_NATIVE("Model native · no app system prompt");
    fun localPrompt(custom: String): String? = when (this) {
        ASSISTANT -> DesktopStarter.prompt
        CUSTOM -> { require(custom.isNotBlank() && custom.length <= 8192); custom }
        MODEL_NATIVE -> null
    }
}
