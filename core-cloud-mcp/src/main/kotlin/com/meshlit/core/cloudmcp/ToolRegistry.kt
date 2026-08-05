package com.meshlit.core.cloudmcp

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Process-wide merge of every connected provider's tool list.
 *
 * The LLM (NaraRouter) gets a single tool list — it doesn't care
 * which provider owns a tool, just that the union covers what's
 * available. When the user connects an AWS session, the AWS
 * `list_ec2` tool is added; when a Custom OpenAPI spec is parsed,
 * its paths become additional tools. The registry keys every
 * tool by `providerId::name` so collisions resolve deterministically.
 *
 * Read with [tools]. Mutate through [put] / [remove] /
 * [removeProvider].
 */
class ToolRegistry {
    private val _tools = MutableStateFlow<Map<String, McpTool>>(emptyMap())
    val tools: StateFlow<Map<String, McpTool>> = _tools.asStateFlow()

    /** Add or replace a tool. */
    fun put(tool: McpTool) {
        val key = "${tool.providerId}::${tool.name}"
        _tools.update { it + (key to tool) }
    }

    /** Add or replace a batch of tools (one provider at a time). */
    fun putAll(providerId: String, tools: List<McpTool>) {
        _tools.update { current ->
            current.filterKeys { !it.startsWith("$providerId::") } +
                tools.associateBy { "$providerId::${it.name}" }
        }
    }

    fun remove(providerId: String, name: String) {
        _tools.update { it - "$providerId::$name" }
    }

    /** Drop every tool owned by [providerId] (called on disconnect). */
    fun removeProvider(providerId: String) {
        _tools.update { current ->
            current.filterKeys { !it.startsWith("$providerId::") }
        }
    }

    /** Tool list in deterministic order — what we hand to the LLM. */
    fun ordered(): List<McpTool> = _tools.value.values
        .sortedWith(compareBy({ it.providerId }, { it.name }))

    fun snapshot(): Map<String, McpTool> = _tools.value
}
