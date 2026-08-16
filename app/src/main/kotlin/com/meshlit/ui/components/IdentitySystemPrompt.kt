package com.meshlit.ui.components

/**
 * Build the system-prompt prefix that tells the model how to
 * identify itself.
 *
 * The Jobs screen and the Agent screen both prepend this string
 * (or something equivalent) before the user's prompt so when the
 * user asks "what's your name / identify yourself", the model
 * answers with the right tags.
 *
 * Format:
 *   "You are Meshlit (<appVersion>) running <modelName> on the
 *    <originTag> engine (<engineTag>). When asked your name or
 *    identity, answer exactly: 'I am Meshlit (<appVersion>),
 *    running <modelName> on <originTag> (<engineTag>)'."
 *
 * The model is told to answer with the tags inline rather than
 * parroting the system prompt verbatim — most chat-tuned
 * instruction models follow this contract; the few that don't
 * still benefit from the prefix because the chat-template
 * exposes it as the assistant's grounding context.
 */
fun identitySystemPrompt(identity: Identity): String {
    val origin = identity.originTag()
    val engine = identity.engineTag.ifBlank { "unknown engine" }
    val model = identity.modelName.ifBlank { "an unnamed model" }
    val version = identity.appVersion.ifBlank { "dev" }
    return "You are Meshlit ($version) running $model on the $origin engine ($engine). " +
        "When asked your name, your identity, or who you are, answer: " +
        "'I am Meshlit ($version), running $model on $origin ($engine).' " +
        "Keep the answer concise and surface the version / engine tags in the same breath."
}