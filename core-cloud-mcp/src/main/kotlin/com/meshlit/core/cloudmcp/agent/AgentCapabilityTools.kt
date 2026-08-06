package com.meshlit.core.cloudmcp.agent

import com.meshlit.core.cloudmcp.McpTool
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Tool descriptor factory for the on-device agent capabilities.
 * Each [AgentCapability] maps to a fixed `agent_*` tool name (or
 * set of names for `Storage`) that surfaces to the LLM only when
 * the capability is enabled.
 *
 * Runtime dispatch (which Android API to call) lives in
 * `app/.../AgentCapabilityDispatch.kt`. This file is the
 * schema-only side so it can be unit-tested without Android.
 */
object AgentCapabilityTools {

    /** Provider id used for every `agent_*` tool in the ToolRegistry. */
    const val PROVIDER_ID = "agent-capabilities"

    /**
     * Tool descriptors for one capability, in the canonical order
     * they're advertised to the LLM.
     */
    fun toolsFor(capability: AgentCapability): List<McpTool> = when (capability) {
        AgentCapability.Camera -> listOf(cameraCaptureTool())
        AgentCapability.Microphone -> listOf(micListenTool())
        AgentCapability.Location -> listOf(locationGetTool())
        AgentCapability.DataState -> listOf(dataStateTool())
        AgentCapability.Call -> listOf(callDialTool())
        AgentCapability.Sms -> listOf(smsSendTool())
        AgentCapability.Storage -> listOf(
            storageListTool(), storageReadTool(), storageWriteTool(),
        )
    }

    // ---- Camera ---------------------------------------------------

    private fun cameraCaptureTool() = McpTool(
        name = "agent_camera_capture",
        description = "Capture a single photo using the device's rear camera and return "
            + "the JPEG bytes as base64. Risk: MEDIUM. The user must approve each capture "
            + "unless they have marked this capability as 'always allow'.",
        inputSchema = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("lensFacing", buildJsonObject {
                    put("type", "string")
                    put("enum", JsonArray(listOf(JsonPrimitive("back"), JsonPrimitive("front"))))
                    put("default", JsonPrimitive("back"))
                })
                put("maxWidthPx", buildJsonObject {
                    put("type", "integer")
                    put("minimum", JsonPrimitive(320))
                    put("default", JsonPrimitive(1280))
                })
                put("flashMode", buildJsonObject {
                    put("type", "string")
                    put("enum", JsonArray(listOf(
                        JsonPrimitive("off"),
                        JsonPrimitive("auto"),
                        JsonPrimitive("on"),
                    )))
                    put("default", JsonPrimitive("auto"))
                })
            })
            put("required", JsonArray(emptyList()))
        },
        providerId = PROVIDER_ID,
    )

    // ---- Microphone -----------------------------------------------

    private fun micListenTool() = McpTool(
        name = "agent_mic_listen",
        description = "Capture a short audio clip (default 5 seconds, max 30) from the "
            + "device microphone. Returns base64-encoded opus bytes, or a transcript if "
            + "STT is enabled. Risk: MEDIUM. The user must approve each capture.",
        inputSchema = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("durationMs", buildJsonObject {
                    put("type", "integer")
                    put("minimum", JsonPrimitive(500))
                    put("maximum", JsonPrimitive(30_000))
                    put("default", JsonPrimitive(5_000))
                })
                put("transcribe", buildJsonObject {
                    put("type", "boolean")
                    put("default", JsonPrimitive(true))
                })
            })
            put("required", JsonArray(emptyList()))
        },
        providerId = PROVIDER_ID,
    )

    // ---- Location --------------------------------------------------

    private fun locationGetTool() = McpTool(
        name = "agent_location_get",
        description = "Return the device's last-known location (lat, lon, accuracy "
            + "metres, fix timestamp). Does NOT request a fresh fix. Risk: MEDIUM. "
            + "The user must approve each request.",
        inputSchema = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("maxAgeMs", buildJsonObject {
                    put("type", "integer")
                    put("minimum", JsonPrimitive(0))
                    put("default", JsonPrimitive(60_000))
                    put("description", JsonPrimitive(
                        "Reject the cached fix if it's older than this.",
                    ))
                })
            })
            put("required", JsonArray(emptyList()))
        },
        providerId = PROVIDER_ID,
    )

    // ---- Network state --------------------------------------------

    private fun dataStateTool() = McpTool(
        name = "agent_data_state",
        description = "Return the device's network state — Wi-Fi SSID + link speed, "
            + "cellular type (LTE / NR / …), whether internet is reachable, whether "
            + "the device is metered. Risk: LOW. No data leaves the phone.",
        inputSchema = buildJsonObject {
            put("type", "object")
            put("properties", JsonObject(emptyMap()))
            put("required", JsonArray(emptyList()))
        },
        providerId = PROVIDER_ID,
    )

    // ---- Call (dial only) -----------------------------------------

    private fun callDialTool() = McpTool(
        name = "agent_call_dial",
        description = "Open the system dialer with a pre-filled number. Does NOT place "
            + "the call — the user still has to hit the green button. Risk: LOW. No "
            + "permission required.",
        inputSchema = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("number", buildJsonObject {
                    put("type", "string")
                    put("description", JsonPrimitive(
                        "E.164 phone number, e.g. +15551234567.",
                    ))
                })
                put("contactName", buildJsonObject {
                    put("type", "string")
                    put("description", JsonPrimitive(
                        "Optional. Surfaces in the confirmation dialog.",
                    ))
                })
            })
            put("required", JsonArray(listOf(JsonPrimitive("number"))))
        },
        providerId = PROVIDER_ID,
    )

    // ---- SMS -------------------------------------------------------

    private fun smsSendTool() = McpTool(
        name = "agent_sms_send",
        description = "Send an SMS to a recipient on the user's allowlist. Risk: HIGH. "
            + "Recipients must be approved one at a time; there is no wildcard allowlist. "
            + "If the recipient isn't allowlisted the call returns a permission error so "
            + "the agent can prompt the user.",
        inputSchema = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("number", buildJsonObject {
                    put("type", "string")
                    put("description", JsonPrimitive(
                        "E.164 phone number, e.g. +15551234567.",
                    ))
                })
                put("body", buildJsonObject {
                    put("type", "string")
                    put("maxLength", JsonPrimitive(1600))
                })
            })
            put("required", JsonArray(listOf(
                JsonPrimitive("number"),
                JsonPrimitive("body"),
            )))
        },
        providerId = PROVIDER_ID,
    )

    // ---- Storage ---------------------------------------------------

    private fun storageListTool() = McpTool(
        name = "agent_storage_list",
        description = "List files in a directory the user has previously granted via "
            + "the system file picker. Returns `{path, name, sizeBytes, mimeType, "
            + "lastModifiedMs, isDirectory}` per entry. Risk: HIGH. Path traversal "
            + "outside the granted tree is rejected.",
        inputSchema = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("path", buildJsonObject {
                    put("type", "string")
                    put("description", JsonPrimitive(
                        "Path relative to the granted tree root. Use '.' for the root.",
                    ))
                })
            })
            put("required", JsonArray(listOf(JsonPrimitive("path"))))
        },
        providerId = PROVIDER_ID,
    )

    private fun storageReadTool() = McpTool(
        name = "agent_storage_read",
        description = "Read a file from a directory the user has previously granted. "
            + "Returns base64-encoded bytes + mimeType. Risk: HIGH.",
        inputSchema = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("path", buildJsonObject {
                    put("type", "string")
                    put("description", JsonPrimitive(
                        "Path relative to the granted tree root.",
                    ))
                })
                put("maxBytes", buildJsonObject {
                    put("type", "integer")
                    put("minimum", JsonPrimitive(1))
                    put("maximum", JsonPrimitive(10 * 1024 * 1024))
                    put("default", JsonPrimitive(1024 * 1024))
                })
            })
            put("required", JsonArray(listOf(JsonPrimitive("path"))))
        },
        providerId = PROVIDER_ID,
    )

    private fun storageWriteTool() = McpTool(
        name = "agent_storage_write",
        description = "Write (create / overwrite) a file in a directory the user has "
            + "previously granted. Path traversal outside the granted tree is rejected. "
            + "Risk: HIGH.",
        inputSchema = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("path", buildJsonObject {
                    put("type", "string")
                    put("description", JsonPrimitive(
                        "Path relative to the granted tree root.",
                    ))
                })
                put("contentBase64", buildJsonObject {
                    put("type", "string")
                    put("description", JsonPrimitive("File contents as base64."))
                })
                put("mimeType", buildJsonObject {
                    put("type", "string")
                    put("default", JsonPrimitive("application/octet-stream"))
                })
            })
            put("required", JsonArray(listOf(
                JsonPrimitive("path"),
                JsonPrimitive("contentBase64"),
            )))
        },
        providerId = PROVIDER_ID,
    )

    // ---- helpers ---------------------------------------------------

    @Suppress("unused")
    private fun JsonObjectBuilder.unused() = Unit // keep import marker
}