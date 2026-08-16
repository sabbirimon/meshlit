package com.meshlit.core.mcp.builtin

import com.meshlit.core.mcp.McpToolResult
import com.meshlit.core.mcp.McpToolSpec
import com.meshlit.core.mcp.objectSchema
import com.meshlit.core.mcp.stringProp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Phase 4.x — Device-Info MCP tools.
 *
 * Lets the agent inspect the device without granting broader
 * filesystem / shell access. Wraps the open-source
 * [`mcp-device-info`](https://github.com/cyanheads/mcp-device-info)
 * surface (3 tools) but runs in-process — no Node.js child needed.
 *
 * The bridge is provided by the app module; it owns the
 * BatteryManager / NetworkCapabilities / Environment paths.
 */
interface DeviceInfoBridge {
    /** Battery percentage (0-100), charging status, charging type,
     *  temperature (tenths of °C). */
    fun battery(): BatteryInfo

    /** Network info: connectivity, type, ssid (if wifi). */
    fun network(): NetworkInfo

    /** Storage info: total / free bytes on the data partition. */
    fun storage(): StorageInfo

    data class BatteryInfo(
        val level: Int,
        val isCharging: Boolean,
        val chargingType: String,
        val temperatureTenthsC: Int,
    )

    data class NetworkInfo(
        val isConnected: Boolean,
        val type: String,
        val ssid: String?,
    )

    data class StorageInfo(
        val totalBytes: Long,
        val freeBytes: Long,
    )
}

/** No-op bridge used when the app hasn't wired one yet. */
object NoOpDeviceInfoBridge : DeviceInfoBridge {
    override fun battery() = DeviceInfoBridge.BatteryInfo(-1, false, "unknown", 0)
    override fun network() = DeviceInfoBridge.NetworkInfo(false, "unknown", null)
    override fun storage() = DeviceInfoBridge.StorageInfo(0L, 0L)
}

class DeviceInfoMcpTools(
    private val bridge: DeviceInfoBridge = NoOpDeviceInfoBridge,
) {
    fun specs(): List<McpToolSpec> = listOf(
        BatteryInfoTool(bridge).spec(),
        NetworkInfoTool(bridge).spec(),
        StorageInfoTool(bridge).spec(),
    )
}

private class BatteryInfoTool(private val bridge: DeviceInfoBridge) {
    fun spec(): McpToolSpec = McpToolSpec(
        name = "device_battery",
        description = "Returns current battery level, charging state, " +
            "charging type (ac/usb/wireless/none), and temperature (tenths of °C).",
        inputSchema = objectSchema(emptyMap()),
    ) { _ ->
        val info = bridge.battery()
        McpToolResult.Json(buildJsonObject {
            put("level_percent", info.level)
            put("is_charging", info.isCharging)
            put("charging_type", info.chargingType)
            put("temperature_tenths_c", info.temperatureTenthsC)
        })
    }
}

private class NetworkInfoTool(private val bridge: DeviceInfoBridge) {
    fun spec(): McpToolSpec = McpToolSpec(
        name = "device_network",
        description = "Returns current connectivity, network type (wifi/cellular/ethernet/none), " +
            "and the active SSID when connected to wifi (null otherwise).",
        inputSchema = objectSchema(emptyMap()),
    ) { _ ->
        val info = bridge.network()
        McpToolResult.Json(buildJsonObject {
            put("is_connected", info.isConnected)
            put("type", kotlinx.serialization.json.JsonPrimitive(info.type))
            put("ssid", info.ssid?.let { kotlinx.serialization.json.JsonPrimitive(it) }
                ?: kotlinx.serialization.json.JsonNull)
        })
    }
}

private class StorageInfoTool(private val bridge: DeviceInfoBridge) {
    fun spec(): McpToolSpec = McpToolSpec(
        name = "device_storage",
        description = "Returns total + free bytes on the data partition. " +
            "Use to check how much room is left for downloads / exports.",
        inputSchema = objectSchema(emptyMap()),
    ) { _ ->
        val info = bridge.storage()
        McpToolResult.Json(buildJsonObject {
            put("total_bytes", info.totalBytes)
            put("free_bytes", info.freeBytes)
        })
    }
}