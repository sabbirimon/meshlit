package com.meshlit.desktop

import java.util.prefs.Preferences
import kotlinx.serialization.json.*

internal data class OnlineConnection(val endpoint: String, val protocol: HostProtocol, val gateway: Boolean)
/** Non-secret connection metadata only; credentials and session consent are stored separately. */
internal class OnlineConnections(private val store: Preferences = Preferences.userRoot().node("com/meshlit/online-connections")) {
    fun load(name: String): OnlineConnection? = runCatching {
        val data = boundedJson(store.get(name, null) ?: return null)
        val protocol = HostProtocol.valueOf(data.getValue("protocol").jsonPrimitive.content)
        val host = HostEndpoint.parse(data.getValue("endpoint").jsonPrimitive.content, protocol)
        OnlineConnection(host.base.toString(), protocol, data.getValue("gateway").jsonPrimitive.boolean)
    }.getOrNull()
    fun save(name: String, endpoint: String, protocol: HostProtocol, gateway: Boolean) {
        require(onlinePresets.any { it.name == name })
        val host = HostEndpoint.parse(endpoint, protocol)
        val text = buildJsonObject {
            put("endpoint", host.base.toString()); put("protocol", protocol.name); put("gateway", gateway)
        }.toString()
        store.put(name, text); store.flush()
    }
    fun remove(name: String) { require(onlinePresets.any { it.name == name }); store.remove(name); store.flush() }
}
