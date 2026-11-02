package com.meshlit.chat

import com.meshlit.core.mcp.LocalToolAnswer
import com.meshlit.core.mcp.control.*
import kotlinx.serialization.json.*

data class DeviceConnectionSelection(val offerId:String,val sourceId:String)

/** Only fresh, explicit connection requests. Free text never becomes an address or credential. */
internal fun deviceConnectionChatIntent(message:String,options:ChatOptions):DirectChatToolIntent? {
    if(!options.connectionTools || !options.supportsLocalToolSelection)return null
    val text=message.trim().removeSuffix("?").removeSuffix("!")
    if(text.length !in 5..240 || text.any(Char::isISOControl) || text.startsWith('"') || text.startsWith('`'))return null
    val normalized=text.lowercase()
    if(Regex("\\b(don't|dont|do not|never|without)\\b").containsMatchIn(normalized))return null
    val command=text.replace(Regex("^(?:please |can you |could you )",RegexOption.IGNORE_CASE),"")
    if(Regex("^(?:show|check|list) (?:device )?connection (?:status|results)$",RegexOption.IGNORE_CASE).matches(command))
        return DirectChatToolIntent("device_connection",buildJsonObject{put("action","status")})
    if(Regex("^(?:list|show) (?:the )?(?:(?:available|saved|approved|nearby) )?devices$",RegexOption.IGNORE_CASE).matches(command))
        return DirectChatToolIntent("device_connection",buildJsonObject{put("action","list")})
    val match=Regex("^(?:connect|reconnect)(?: (.+))?$",RegexOption.IGNORE_CASE).matchEntire(command) ?: return null
    var query=match.groupValues[1].trim()
    query=query.replace(Regex("^(?:to |the device |my device |device )",RegexOption.IGNORE_CASE),"")
    if(query.lowercase() in setOf("","a device","another device","devices","device","my devices","a node","another node"))query=""
    // Unknown URLs and credential-looking instructions produce local setup choices only.
    if(query.length>160 || query.contains("://") || '@' in query || Regex("\\b(password|token|secret|api key)\\b",RegexOption.IGNORE_CASE).containsMatchIn(query))query=""
    return DirectChatToolIntent("device_connection",buildJsonObject{put("action","request");put("query",query)})
}

internal fun deviceConnectionAnswer(root:JsonObject):LocalToolAnswer {
    (root["offer"] as? JsonObject)?.let { value ->
        val offer=Json.decodeFromJsonElement<DeviceConnectionOffer>(value)
        val text=when {
            !offer.enabled->"Device connection assistance is off. Open Devices → Connection assistance to enable it and approve specific saved sources. No connection was started."
            offer.choices.isEmpty()->"No saved or opt-in discovered devices match this request. Add a paired web device, pinned SSH node or model API in Devices. New identities need owner approval. No network scan or enrollment was started."
            else->"Choose a saved device below. Approved sources can be checked automatically; first identities and setup-only transports need owner setup. This checks contact and identity, and keeps other device actions separately scoped."
        }
        return LocalToolAnswer(text,1,emptyList(),deviceOffer=offer)
    }
    (root["receipt"] as? JsonObject)?.let { value ->
        val receipt=Json.decodeFromJsonElement<DeviceConnectionReceipt>(value)
        return LocalToolAnswer("${receipt.name}: ${receipt.phase.name.lowercase().replace('_',' ')}. ${receipt.detail}",1,emptyList(),deviceReceipt=receipt)
    }
    val receipts=(root["receipts"] as? JsonArray)?.map{Json.decodeFromJsonElement<DeviceConnectionReceipt>(it)}
    if(receipts!=null)return LocalToolAnswer(if(receipts.isEmpty())"No connection checks are recorded for this conversation." else receipts.takeLast(5).joinToString("\n"){"${it.name}: ${it.phase.name.lowercase().replace('_',' ')} · ${it.detail}"},1,emptyList())
    if(root["cancelRequested"]?.jsonPrimitive?.booleanOrNull==true)return LocalToolAnswer("Connection check cancellation requested. Completed work and existing shared sessions are not reversed.",1,emptyList())
    error("The connection handler returned no usable choices or receipt")
}
