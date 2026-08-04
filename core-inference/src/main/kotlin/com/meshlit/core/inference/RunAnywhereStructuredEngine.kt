package com.meshlit.core.inference

import com.meshlit.core.common.MeshlitError
import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.common.logger
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.generateStructuredStream
import com.runanywhere.sdk.public.extensions.generateWithTools
import com.runanywhere.sdk.public.extensions.registerTool
import com.runanywhere.sdk.public.extensions.getRegisteredTools
import com.runanywhere.sdk.public.extensions.unregisterTool
import ai.runanywhere.proto.v1.ExecutionTarget
import ai.runanywhere.proto.v1.InferenceFramework
import ai.runanywhere.proto.v1.JSONSchema
import ai.runanywhere.proto.v1.JSONSchemaProperty
import ai.runanywhere.proto.v1.JSONSchemaType
import ai.runanywhere.proto.v1.LLMGenerationOptions
import ai.runanywhere.proto.v1.StructuredOutputStreamEvent
import ai.runanywhere.proto.v1.StructuredOutputStreamEventKind
import ai.runanywhere.proto.v1.ThinkingTagPattern
import ai.runanywhere.proto.v1.ToolCallingOptions
import ai.runanywhere.proto.v1.ToolCallingResult
import ai.runanywhere.proto.v1.ToolChoiceMode
import ai.runanywhere.proto.v1.ToolDefinition
import ai.runanywhere.proto.v1.ToolParameter
import ai.runanywhere.proto.v1.ToolParameterType
import ai.runanywhere.proto.v1.ToolValue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Phase 2.x — wraps the RunAnywhere SDK's structured-output +
 * tool-calling surfaces behind a single facade that the Structured
 * screen binds to.
 *
 * Two responsibilities:
 *
 *  1. **Structured output** — feed a [JSONSchema] and a prompt to
 *     `generateStructuredStream`, surface the partial-JSON stream so
 *     the UI can show the model assembling the document token by
 *     token, and resolve the final [StructuredStreamView.Done] with
 *     raw text + parsed payload.
 *
 *  2. **Tool calling** — let the host register a [ToolDefinition]
 *     via `RunAnywhere.registerTool` and drive the model loop via
 *     `generateWithTools`. The engine translates the SDK's typed
 *     result into a host [ToolRunView] the UI can render.
 *
 * Threading:
 *
 *  - Every flow is `flowOn(dispatcher)` (default IO). The SDK's
 *    network IO is on the producer side; collecting from
 *    `Dispatchers.Main` is safe.
 *  - Tool handlers execute on the SDK's worker pool. Pure lambdas
 *    are fine; anything that touches `Activity` / `Context` should
 *    post to the main thread inside the handler.
 */
class RunAnywhereStructuredEngine(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private val log = logger("RunAnywhereStructuredEngine")

    /**
     * Host-typed view of a structured stream. The UI maps each
     * kind onto a Compose Card; the terminal [Done] event replaces
     * the running partial with the final parsed payload.
     */
    sealed interface StructuredStreamView {
        /** Streaming a single token (incremental raw output). */
        data class Token(val text: String) : StructuredStreamView
        /** Streaming the partial JSON the model is building so far. */
        data class PartialJson(val json: String) : StructuredStreamView
        /** Validation result (valid / invalid + reason). */
        data class Validation(val isValid: Boolean, val reason: String?) :
            StructuredStreamView
        /** Terminal success. */
        data class Done(
            val rawText: String,
            val parsedJson: String?,
            val isValid: Boolean,
            val errorMessage: String?,
        ) : StructuredStreamView
        /** Terminal error. */
        data class Failed(val message: String) : StructuredStreamView
    }

    /**
     * Run a structured-output generation. Returns a cold flow of
     * [StructuredStreamView] events the UI collects to render the
     * running JSON.
     *
     * @param prompt natural-language input from the user.
     * @param schema JSON schema describing the target document.
     * @param options generation parameters — defaults favour low
     *   temperature for stable structured output.
     */
    fun generateStructured(
        prompt: String,
        schema: JSONSchema,
        options: LLMGenerationOptions = defaultGenerationOptions(),
    ): Flow<StructuredStreamView> = flow {
        val sdkFlow: Flow<StructuredOutputStreamEvent> = RunAnywhere.generateStructuredStream(
            prompt = prompt,
            schema = schema,
            options = options,
        )
        sdkFlow.collect { event ->
            when (event.kind) {
                StructuredOutputStreamEventKind.STRUCTURED_OUTPUT_STREAM_EVENT_KIND_TOKEN -> {
                    val tok = event.token
                    if (!tok.isNullOrEmpty()) {
                        emit(StructuredStreamView.Token(tok))
                    }
                }
                StructuredOutputStreamEventKind.STRUCTURED_OUTPUT_STREAM_EVENT_KIND_PARTIAL_JSON -> {
                    val pj = event.partial_json
                    if (!pj.isNullOrEmpty()) {
                        emit(StructuredStreamView.PartialJson(pj))
                    }
                }
                StructuredOutputStreamEventKind.STRUCTURED_OUTPUT_STREAM_EVENT_KIND_VALIDATION -> {
                    val v = event.validation
                    emit(
                        StructuredStreamView.Validation(
                            isValid = v?.is_valid ?: false,
                            reason = v?.error_message?.takeIf { it.isNotBlank() },
                        ),
                    )
                }
                StructuredOutputStreamEventKind.STRUCTURED_OUTPUT_STREAM_EVENT_KIND_COMPLETED -> {
                    val r = event.result
                    val raw = r?.raw_text.orEmpty()
                    val parsed = r?.parsed_json?.utf8()?.takeIf { it.isNotBlank() }
                        ?: raw.takeIf { it.trimStart().startsWith("{") }
                    emit(
                        StructuredStreamView.Done(
                            rawText = raw,
                            parsedJson = parsed,
                            isValid = r?.validation?.is_valid ?: false,
                            errorMessage = r?.error_message?.takeIf { it.isNotBlank() },
                        ),
                    )
                    return@collect
                }
                StructuredOutputStreamEventKind.STRUCTURED_OUTPUT_STREAM_EVENT_KIND_ERROR -> {
                    emit(StructuredStreamView.Failed(event.error_message ?: "unknown"))
                    return@collect
                }
                else -> { /* UNSPECIFIED — ignore */ }
            }
        }
    }.flowOn(dispatcher)

    /**
     * Same as [generateStructured] but takes a simple `(title,
     * description, fields)` shape instead of a `JSONSchema` proto.
     * Lets the host screens reference the schema by friendly triples
     * without pulling the Wire-generated `ai.runanywhere.proto.v1.*`
     * types onto their classpath.
     *
     * The fields use a `Map<String, String>` so the host can hand the
     * engine a parsed object or a JSON string; the engine converts
     * to a `JSONSchema` proto internally. The schema builder
     * recognises the keys `name` (required), `type` (`string`,
     * `number`, `integer`, `boolean`, `array`, `object`, `null`),
     * and `description`.
     */
    fun generateStructuredFromFields(
        prompt: String,
        title: String,
        description: String,
        fields: List<Triple<String, String, String>>,
        options: LLMGenerationOptions = defaultGenerationOptions(),
    ): Flow<StructuredStreamView> {
        val schema = buildObjectSchema(title, description, fields)
        return generateStructured(prompt, schema, options)
    }

    /**
     * Host-typed view of a tool-calling run. The terminal [Done]
     * carries the final assistant text plus the list of tool calls
     * the model made and the results the handlers returned.
     */
    sealed interface ToolRunView {
        data class Done(
            val text: String,
            val toolCalls: List<ToolCallView>,
            val isComplete: Boolean,
            val errorMessage: String?,
        ) : ToolRunView
        data class Failed(val message: String) : ToolRunView
    }

    /** One tool invocation the model made and its handler's reply. */
    data class ToolCallView(
        val name: String,
        val arguments: String,
        val resultJson: String?,
        val errorMessage: String?,
    )

    /**
     * Run a generation with tool-calling enabled. The SDK drives the
     * model loop end-to-end and returns a [ToolCallingResult] once
     * the model either emits a terminal reply or hits the
     * `max_tool_calls` budget.
     *
     * @param maxToolCalls upper bound on tool calls per generation.
     */
    suspend fun generateWithTools(
        prompt: String,
        maxToolCalls: Int = 8,
        options: LLMGenerationOptions = defaultGenerationOptions(),
    ): ToolRunView {
        return try {
            val toolOptions = ToolCallingOptions(
                tools = emptyList(),
                auto_execute = true,
                temperature = null,
                max_tokens = null,
                system_prompt = null,
                replace_system_prompt = false,
                keep_tools_available = true,
                format = null,
                max_tool_calls = maxToolCalls,
                tool_choice = ToolChoiceMode.TOOL_CHOICE_MODE_AUTO,
                forced_tool_name = null,
                require_json_arguments = true,
                disable_thinking = null,
            )
            val result: ToolCallingResult = RunAnywhere.generateWithTools(
                prompt = prompt,
                options = options,
                toolOptions = toolOptions,
                toolChoice = ToolChoiceMode.TOOL_CHOICE_MODE_AUTO,
                forcedToolName = null,
                validateCalls = true,
                history = emptyList<String>(),
            )
            val calls = result.tool_calls.orEmpty().mapNotNull { call ->
                val name = call?.name ?: return@mapNotNull null
                val args = call.arguments_json.orEmpty()
                val matched = result.tool_results.orEmpty()
                    .firstOrNull { it?.tool_call_id == call.id }
                ToolCallView(
                    name = name,
                    arguments = args,
                    resultJson = matched?.result_json?.takeIf { it.isNotBlank() },
                    errorMessage = matched?.error?.takeIf { it.isNotBlank() },
                )
            }
            ToolRunView.Done(
                text = result.text.orEmpty(),
                toolCalls = calls,
                isComplete = result.is_complete,
                errorMessage = result.error_message?.takeIf { it.isNotBlank() },
            )
        } catch (t: Throwable) {
            log.warn(
                "runanywhere.tools.failed",
                "Tool-calling generation failed",
                mapOf("error" to (t.message ?: t.javaClass.simpleName)),
            )
            ToolRunView.Failed(t.message ?: t.javaClass.simpleName)
        }
    }

    /**
     * Register a tool handler the model can invoke. The SDK's
     * `registerTool` takes a [ToolDefinition] plus a handler that
     * maps a `Map<String, ToolValue>` of named arguments to a
     * `Map<String, ToolValue>` of named results.
     */
    suspend fun registerMeshlitTool(
        name: String,
        description: String,
        parameters: List<ToolParameterSpec>,
        handler: suspend (args: Map<String, String>) -> Map<String, String>,
    ): MeshlitResult<Unit> {
        return try {
            val protoParams = parameters.map { p ->
                ToolParameter(
                    name = p.name,
                    type = p.wireType,
                    description = p.description,
                    required = p.required,
                    json_schema = null,
                    default_value = null,
                    enum_values = emptyList(),
                )
            }
            val definition = ToolDefinition(
                name = name,
                description = description,
                parameters = protoParams,
                category = "meshlit",
                json_schema = null,
                metadata = emptyMap(),
            )
            val sdkHandler: suspend (Map<String, ToolValue>) -> Map<String, ToolValue> =
                { args ->
                    val stringArgs = args.mapValues { (_, v) -> toolValueToString(v) }
                    val stringResult = handler(stringArgs)
                    stringResult.mapValues { (_, v) -> ToolValue(string_value = v) }
                }
            RunAnywhere.registerTool(definition, sdkHandler)
            log.info(
                "runanywhere.tools.registered",
                "Registered Meshlit tool",
                mapOf("name" to name, "params" to protoParams.size),
            )
            MeshlitResult.Success(Unit)
        } catch (t: Throwable) {
            log.warn(
                "runanywhere.tools.register_failed",
                "Failed to register Meshlit tool",
                mapOf("name" to name, "error" to (t.message ?: t.javaClass.simpleName)),
            )
            MeshlitResult.Failure(
                MeshlitError.Native(
                    "runanywhere.tools.register_failed:${t.message ?: t.javaClass.simpleName}",
                    t,
                ),
            )
        }
    }

    /** Unregister a tool by name. Called when a tool's permissions
     *  are revoked on the Structured screen. */
    suspend fun unregisterMeshlitTool(name: String): MeshlitResult<Unit> {
        return try {
            RunAnywhere.unregisterTool(name)
            MeshlitResult.Success(Unit)
        } catch (t: Throwable) {
            MeshlitResult.Failure(
                MeshlitError.Native(
                    "runanywhere.tools.unregister_failed:${t.message ?: t.javaClass.simpleName}",
                    t,
                ),
            )
        }
    }

    /** Names of currently-registered tools. The Structured screen
     *  renders this as the "available tools" checkboxes. */
    suspend fun listMeshlitTools(): List<String> = try {
        RunAnywhere.getRegisteredTools().mapNotNull { it?.name }
    } catch (t: Throwable) {
        log.warn(
            "runanywhere.tools.list_failed",
            "Failed to list registered tools",
            mapOf("error" to (t.message ?: t.javaClass.simpleName)),
        )
        emptyList()
    }

    /** Build a JSON schema for a simple flat object from a list of
     *  (name, type, description) triples. Convenience for the
     *  built-in templates on the Structured screen.
     *
     * **Why this is exposed as a public engine method rather than
     * constructed in the screen:** the SDK's `JSONSchema` proto
     * lives in `:core-inference` and isn't on the `:app` classpath.
     * Keeping the conversion here means the screen can hand the
     * engine a plain `List<Triple<String, String, String>>` and not
     * need to know about Wire-generated types.
     */
    fun buildObjectSchema(
        title: String,
        description: String,
        fields: List<Triple<String, String, String>>,
    ): JSONSchema {
        val props = fields.associate { (name, type, desc) ->
            name to JSONSchemaProperty(
                type = wireTypeFor(type),
                description = desc,
                enum_values = emptyList(),
                format = null,
                items_schema = null,
                object_schema = null,
                minimum = null,
                maximum = null,
                min_length = null,
                max_length = null,
                pattern = null,
                min_items = null,
                max_items = null,
                default_json = null,
            )
        }
        return JSONSchema(
            type = JSONSchemaType.JSON_SCHEMA_TYPE_OBJECT,
            properties = props,
            required = fields.map { it.first },
            items = null,
            additional_properties = false,
            schema_uri = null,
            id_uri = null,
            title = title,
            description = description,
            ref = null,
            not_schema = null,
            raw_json = null,
            definitions = emptyMap(),
            all_of = emptyList(),
            any_of = emptyList(),
            one_of = emptyList(),
        )
    }

    /** Default generation options for structured output: streaming on,
     *  low temperature for determinism, modest token budget. */
    private fun defaultGenerationOptions(): LLMGenerationOptions = LLMGenerationOptions(
        max_tokens = 1024,
        temperature = 0.2f,
        top_p = 0.9f,
        top_k = 40,
        repetition_penalty = 1.1f,
        stop_sequences = emptyList(),
        streaming_enabled = true,
        preferred_framework = InferenceFramework.INFERENCE_FRAMEWORK_UNSPECIFIED,
        system_prompt = null,
        json_schema = null,
        thinking_pattern = null,
        execution_target = ExecutionTarget.EXECUTION_TARGET_UNSPECIFIED,
        structured_output = null,
        enable_real_time_tracking = false,
        seed = 0L,
        frequency_penalty = 0f,
        presence_penalty = 0f,
        repeat_last_n = 64,
        min_p = 0.0f,
        grammar = null,
        response_format = null,
        echo_prompt = false,
        n_threads = 0,
        tool_calling = null,
        disable_thinking = false,
    )

    private fun wireTypeFor(name: String): JSONSchemaType = when (name.lowercase()) {
        "string" -> JSONSchemaType.JSON_SCHEMA_TYPE_STRING
        "number" -> JSONSchemaType.JSON_SCHEMA_TYPE_NUMBER
        "integer", "int" -> JSONSchemaType.JSON_SCHEMA_TYPE_INTEGER
        "boolean", "bool" -> JSONSchemaType.JSON_SCHEMA_TYPE_BOOLEAN
        "array", "list" -> JSONSchemaType.JSON_SCHEMA_TYPE_ARRAY
        "object" -> JSONSchemaType.JSON_SCHEMA_TYPE_OBJECT
        "null" -> JSONSchemaType.JSON_SCHEMA_TYPE_NULL
        else -> JSONSchemaType.JSON_SCHEMA_TYPE_STRING
    }

    /** Host-typed tool parameter — the Structured screen builds a
     *  list of these and hands them to [registerMeshlitTool]. */
    data class ToolParameterSpec(
        val name: String,
        val description: String,
        val wireType: ToolParameterType,
        val required: Boolean = true,
    ) {
        companion object {
            fun string(name: String, description: String, required: Boolean = true) =
                ToolParameterSpec(name, description, ToolParameterType.TOOL_PARAMETER_TYPE_STRING, required)
            fun number(name: String, description: String, required: Boolean = true) =
                ToolParameterSpec(name, description, ToolParameterType.TOOL_PARAMETER_TYPE_NUMBER, required)
            fun boolean(name: String, description: String, required: Boolean = true) =
                ToolParameterSpec(name, description, ToolParameterType.TOOL_PARAMETER_TYPE_BOOLEAN, required)
            fun array(name: String, description: String, required: Boolean = true) =
                ToolParameterSpec(name, description, ToolParameterType.TOOL_PARAMETER_TYPE_ARRAY, required)
            fun object_(name: String, description: String, required: Boolean = true) =
                ToolParameterSpec(name, description, ToolParameterType.TOOL_PARAMETER_TYPE_OBJECT, required)
        }
    }

    companion object {
        private val INSTANCE = java.util.concurrent.atomic.AtomicReference<RunAnywhereStructuredEngine?>(null)

        fun install() {
            INSTANCE.compareAndSet(null, RunAnywhereStructuredEngine())
        }

        fun get(): RunAnywhereStructuredEngine =
            INSTANCE.get() ?: error(
                "RunAnywhereStructuredEngine not installed — call install() from MeshlitApplication.onCreate",
            )
    }
}

/** Flatten a `ToolValue` proto oneof to a printable string. The
 *  SDK wraps strings, numbers, bools, lists, and nested objects in
 *  a single oneof; for the Structured screen we just need a string
 *  the host handler can parse. */
private fun toolValueToString(v: ToolValue): String {
    val s = v.string_value
    if (s != null) return s
    val n = v.number_value
    if (n != null) return n.toString()
    val b = v.bool_value
    if (b != null) return b.toString()
    if (v.null_value == true) return "null"
    val arr = v.array_value
    if (arr != null) {
        return arr.values.joinToString(prefix = "[", postfix = "]") { toolValueToString(it) }
    }
    val obj = v.object_value
    if (obj != null) {
        return obj.fields.entries.joinToString(prefix = "{", postfix = "}") {
            "${it.key}=${toolValueToString(it.value)}"
        }
    }
    return ""
}
