package com.meshlit.search

import kotlinx.serialization.json.*

data class RemoteSettingsSnapshot(val route:String,val fields:Map<String,String>,val receivedAtMs:Long)
/** Fixed read-only public fields; remote payloads never populate grants, credentials or tool calls. */
internal val searchableDeviceFields=setOf("themeMode","accentHue","dynamicColors","uiFont","surfaceStyle","animationsEnabled","fontScale","highContrast","workspaceLayout","sidebarStyle")
internal fun parseRemoteSettings(result:JsonObject):Map<String,String> {
    check(result["isError"]?.jsonPrimitive?.booleanOrNull==false){"Remote device rejected settings read"}
    val content=result["content"] as? JsonArray ?: error("Remote settings content is missing")
    val text=content.singleOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.takeIf{it.isString}?.content ?: error("Expected one settings JSON response")
    require(text.toByteArray().size<=16384){"Remote settings response exceeds 16 KiB"}
    val root=Json.parseToJsonElement(text).jsonObject
    require(root["status"]?.jsonPrimitive?.contentOrNull=="ok" && root["schemaVersion"]?.jsonPrimitive?.intOrNull==1){"Unsupported device settings response"}
    val values=root["settings"] as? JsonObject ?: error("Settings object is missing")
    val fields=values.filterKeys{it in searchableDeviceFields}.mapNotNull{(key,value)->
        val primitive=value as? JsonPrimitive ?: return@mapNotNull null
        val textValue=primitive.contentOrNull?.takeIf{it.length<=128 && it.none(Char::isISOControl)} ?: return@mapNotNull null
        key to textValue
    }.toMap()
    require(fields.isNotEmpty()){"No supported settings were returned"}
    return fields
}
