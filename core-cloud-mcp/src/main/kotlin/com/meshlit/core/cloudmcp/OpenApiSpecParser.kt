package com.meshlit.core.cloudmcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * OpenAPI / Swagger spec parser. Converts each `paths.<x>.<verb>`
 * entry into one [McpTool] that the agent loop can route through
 * a Custom provider's MCP server.
 *
 * Supports both Swagger 2.0 (`swagger: "2.0"`) and OpenAPI 3.x
 * (`openapi: "3.0.x"` / `"3.1.x"`). The two formats differ in:
 *  - body parameter location: Swagger uses `in: "body"` + a
 *    `schema` ref; OpenAPI uses `requestBody` with a JSON Schema
 *    `content."application/json".schema`.
 *  - `$ref` resolution: Swagger uses `#/definitions/Foo`; OpenAPI
 *    uses `#/components/schemas/Foo`. The parser transparently
 *    falls back to the OpenAPI path when the Swagger lookup
 *    misses.
 *
 * The output schema is the **flattened** request body — the
 * parser inlines `$ref`s one level deep so the LLM sees a flat
 * JSON Schema it can fill in directly.
 */
object OpenApiSpecParser {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    /**
     * Parse a JSON-encoded OpenAPI / Swagger document and return
     * the inferred tool list. The `providerId` is stamped onto
     * every output so [ToolRegistry] can route calls back.
     */
    fun parse(specJson: String, providerId: String): List<McpTool> {
        val root = json.parseToJsonElement(specJson).jsonObject
        val paths = root["paths"]?.jsonObject ?: return emptyList()
        return buildList {
            paths.forEach { (path, node) ->
                val ops = node.jsonObject
                HTTP_VERBS.forEach { verb ->
                    val op = ops[verb]?.jsonObject ?: return@forEach
                    add(opToTool(path, verb, op, root, providerId))
                }
            }
        }
    }

    private fun opToTool(
        path: String,
        verb: String,
        op: JsonObject,
        root: JsonObject,
        providerId: String,
    ): McpTool {
        val operationId = op["operationId"]?.jsonPrimitive?.content
            ?: "${verb.uppercase()}_${path.replace("/", "_").replace("{", "").replace("}", "")}"
        val summary = op["summary"]?.jsonPrimitive?.content
            ?: op["description"]?.jsonPrimitive?.content
            ?: "$verb $path"
        val description = buildString {
            append(summary)
            append("\n\n")
            append(verb.uppercase())
            append(" ")
            append(path)
            op["description"]?.jsonPrimitive?.content?.let { d ->
                if (d != summary) {
                    append("\n\n")
                    append(d)
                }
            }
        }
        val parameters = op["parameters"]?.jsonArray?.let { flattenParameters(it, root) }
            ?: buildJsonObject {}
        val requestBody = op["requestBody"]?.jsonObject?.let { flattenRequestBody(it, root) }
        val schema = when {
            requestBody != null -> requestBody
            parameters.isNotEmpty() -> parameters
            else -> buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {})
            }
        }
        return McpTool(
            name = operationId,
            description = description.trim(),
            inputSchema = schema,
            providerId = providerId,
        )
    }

    /** Swagger 2.0 — body params via `in: "body"` + `schema.$ref`. */
    private fun flattenParameters(
        paramsNode: JsonArray,
        root: JsonObject,
    ): JsonObject {
        val properties = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
        val required = mutableListOf<String>()
        paramsNode.forEach { node ->
            val obj = node.jsonObject
            val name = obj["name"]?.jsonPrimitive?.content ?: return@forEach
            val schemaRef = obj["schema"]?.jsonObject
            val resolved = schemaRef?.let { resolveRef(it, root) } ?: obj
            val type = resolved["type"]?.jsonPrimitive?.content ?: "string"
            val desc = resolved["description"]?.jsonPrimitive?.content
            properties[name] = buildJsonObject {
                put("type", type)
                desc?.let { put("description", it) }
            }
            if (obj["required"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() == true) {
                required.add(name)
            }
        }
        return buildJsonObject {
            put("type", "object")
            put("properties", kotlinx.serialization.json.JsonObject(properties))
            if (required.isNotEmpty()) {
                put("required", kotlinx.serialization.json.JsonArray(required.map {
                    kotlinx.serialization.json.JsonPrimitive(it)
                }))
            }
        }
    }

    /** OpenAPI 3.x — body params via `requestBody.content."application/json".schema`. */
    private fun flattenRequestBody(
        rb: JsonObject,
        root: JsonObject,
    ): JsonObject {
        val content = rb["content"]?.jsonObject ?: return buildJsonObject {}
        val jsonNode = content["application/json"]?.jsonObject ?: return buildJsonObject {}
        val schemaNode = jsonNode["schema"]?.jsonObject ?: return buildJsonObject {}
        return resolveRef(schemaNode, root)
    }

    /**
     * Resolve `#/components/schemas/Foo` or `#/definitions/Foo`
     * against the spec root. Returns the original node if no `$ref`
     * is present.
     */
    private fun resolveRef(node: JsonObject, root: JsonObject): JsonObject {
        val ref = node["\$ref"]?.jsonPrimitive?.content ?: return node
        val parts = ref.removePrefix("#/").split("/")
        var current: kotlinx.serialization.json.JsonElement = root
        for (part in parts) {
            current = (current as? JsonObject)?.get(part) ?: return node
        }
        return (current as? JsonObject) ?: node
    }

    private val HTTP_VERBS = listOf("get", "post", "put", "delete", "patch")
}