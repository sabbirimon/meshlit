package com.meshlit.devices.studio

import android.content.Context
import com.meshlit.MeshlitApplication
import com.meshlit.core.trust.PeerCrypto
import com.meshlit.core.trust.RecoverableIdentity
import com.meshlit.peering.DeviceIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.koin.android.ext.android.get
import java.net.Socket
import java.util.UUID

data class StudioRelayPeer(val key: String, val node: String, val name: String)

class StudioRelayClient(context: Context) {
    private val app = context.applicationContext as MeshlitApplication

    suspend fun localDeviceId(): String = withContext(Dispatchers.IO) {
        val identity = app.get<DeviceIdentity>().get(app.nodeIdHex)
        PeerCrypto.fingerprint(identity.publicKey).take(20)
    }

    suspend fun request(origin: String, token: String, deviceId: String, pinnedKey: String?, pair: Boolean): StudioRelayPeer =
        withTimeout(80000) {
            withContext(Dispatchers.IO) {
                val identity = app.get<DeviceIdentity>().get(app.nodeIdHex)
                suspendCancellableCoroutine { continuation ->
                    val socket = Socket()
                    continuation.invokeOnCancellation { runCatching { socket.close() } }
                    val result = runCatching {
                        socket.use { studioRequest(it, identity, app.displayName, origin, token, deviceId, pinnedKey, pair) }
                    }
                    continuation.resumeWith(result)
                }
            }
        }
}

internal fun studioRequest(socket: Socket, identity: RecoverableIdentity, localName: String, origin: String,
                          token: String, deviceId: String, pinnedKey: String?, pair: Boolean): StudioRelayPeer {
    require(token.isNotBlank() && token.length <= 512) { "Invalid relay credential." }
    require(deviceId.matches(Regex("[0-9a-f]{20}"))) { "Enter the 20-character Studio device ID." }
    require(pair || pinnedKey != null) { "Pair this device first." }
    if (pinnedKey != null) require(pinnedKey.matches(Regex("[0-9a-f]{182}")) &&
        PeerCrypto.fingerprint(pinnedKey).take(20) == deviceId) { "Saved identity does not match the device ID." }
    val ws = StudioWebSocket(socket)
    ws.open(origin)
    fun text() = ws.read(StudioWebSocket.TEXT).toString(Charsets.UTF_8)
    check(text().matches(Regex("[0-9a-f]{64}"))) { "Unexpected Studio relay challenge." }
    val dial = JSONObject().put("op", "dial").put("id", deviceId).put("token", token)
    ws.send(StudioWebSocket.TEXT, dial.toString().toByteArray(Charsets.UTF_8))
    check(text() == "ready") { "Relay could not reach this device." }
    val request = if (pair) {
        val node = runCatching { UUID.fromString(identity.nodeId) }.getOrElse {
            UUID.nameUUIDFromBytes(("meshlit.studio.node:" + identity.nodeId).toByteArray(Charsets.UTF_8))
        }
        val name = localName.filterNot { it.isISOControl() }.trim().take(128).ifBlank { "Meshlit Android" }
        JSONObject().put("op", "pair").put("node", node.toString()).put("name", name)
    } else JSONObject().put("op", "identity")
    val (key, payload) = StudioTls(ws, studioEngine(identity, deviceId, pinnedKey)).exchange(request.toString())
    val reply = JSONObject(payload)
    check(!reply.has("error")) { reply.optString("error").filterNot { it.isISOControl() }.take(200) }
    val card = if (pair) reply else reply.getJSONObject("identity")
    val node = card.getString("node")
    check(UUID.fromString(node).toString() == node.lowercase()) { "Invalid Studio node identity." }
    if (pair) {
        check(reply.getBoolean("ok"))
        val name = reply.getString("name")
        check(name.isNotBlank() && name.length <= 128 && name.none { it.isISOControl() }) { "Invalid Studio device name." }
        return StudioRelayPeer(key, node, name)
    }
    check(card.getString("device") == deviceId && card.getString("contributor") == PeerCrypto.fingerprint(key) &&
        card.getString("application") == "bd.com.imon.meshlit.studio") { "Studio identity did not match the saved device." }
    return StudioRelayPeer(key, node, "")
}
