package com.meshlit.core.inference.cluster

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

/** Shared JSON codec for [PeerCapabilities]. */
object PeerCapabilitiesJson {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "kind"
    }

    fun encode(cap: PeerCapabilities): String = json.encodeToString(cap)

    fun decode(raw: String): PeerCapabilities = json.decodeFromString(PeerCapabilities.serializer(), raw)
}
