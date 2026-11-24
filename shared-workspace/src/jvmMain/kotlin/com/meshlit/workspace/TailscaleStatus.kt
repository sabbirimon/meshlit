package com.meshlit.workspace

import kotlinx.serialization.json.*

data class TailscalePeer(val name: String?, val addresses: List<String>, val online: Boolean?)
data class TailscaleSnapshot(val observedAt: Long, val backend: String, val kernelTunnel: Boolean?,
    val self: TailscalePeer?, val addresses: List<String>, val peers: List<TailscalePeer>?,
    val exitNode: TailscalePeer?, val healthIssues: Int?) {
    val summary get() = when(backend) {
        "Running" -> if(kernelTunnel == false) "Tailscale running in userspace mode" else "Tailscale daemon running"
        "Stopped" -> "Tailscale stopped"
        "NeedsLogin" -> "Sign in through Tailscale"
        "NeedsMachineAuth" -> "Device approval required in Tailscale"
        "Starting" -> "Tailscale starting"
        else -> "Tailscale has no active state"
    }
}

/** Read only the documented status subset. Never retain login URLs, node keys, profiles or raw output. */
object TailscaleStatus {
    const val MAX_BYTES = 2_097_152
    fun parse(text: String, observedAt: Long): TailscaleSnapshot {
        require(text.length <= MAX_BYTES && observedAt >= 0)
        var depth = 0; var quoted = false; var escaped = false
        text.forEach { c ->
            if(quoted) { if(escaped) escaped = false else if(c == '\\') escaped = true else if(c == '"') quoted = false }
            else when(c) { '"' -> quoted = true; '{','[' -> { depth++; require(depth <= 32) }; '}',']' -> { depth--; require(depth >= 0) } }
        }
        require(depth == 0 && !quoted)
        val root = Json.parseToJsonElement(text).jsonObject
        fun JsonObject.string(key: String): String? = this[key]?.takeUnless { it == JsonNull }?.jsonPrimitive?.let {
            require(it.isString); it.content.also { s -> require(s.length <= 256 && s.none(Char::isISOControl)) }
        }
        fun JsonObject.bool(key: String): Boolean? = this[key]?.takeUnless { it == JsonNull }?.jsonPrimitive?.let {
            require(!it.isString); it.boolean
        }
        fun JsonObject.ips(prefixes: Boolean = false): List<String> = this["TailscaleIPs"]?.takeUnless { it == JsonNull }?.jsonArray?.also { require(it.size <= 32) }?.map {
            require(it.jsonPrimitive.isString)
            val ip = it.jsonPrimitive.content
            require(ip.length <= 64 && ip.all { c -> c in "0123456789abcdefABCDEF:./" } && (':' in ip || '.' in ip))
            require(prefixes || '/' !in ip)
            val parts = ip.split('/'); require(parts.size <= 2)
            val address = parts[0]
            if(':' in address) require(java.net.InetAddress.getByName(address) is java.net.Inet6Address)
            else require(address.split('.').let { octets -> octets.size == 4 && octets.all { part -> part.toIntOrNull() in 0..255 && part.all(Char::isDigit) } })
            if(parts.size == 2) require(parts[1].toIntOrNull() in 0..if(':' in address) 128 else 32)
            ip
        }.orEmpty()
        fun peer(value: JsonElement?, exit: Boolean = false): TailscalePeer? = value?.takeUnless { it == JsonNull }?.jsonObject?.let {
            TailscalePeer(it.string("HostName") ?: it.string("DNSName"), it.ips(exit), it.bool("Online"))
        }
        val state = root.string("BackendState") ?: error("Missing Tailscale state")
        require(state in setOf("NoState", "NeedsLogin", "NeedsMachineAuth", "Stopped", "Starting", "Running"))
        val peers = root["Peer"]?.takeUnless { it == JsonNull }?.jsonObject?.also { require(it.size <= 1024) }?.values?.map { checkNotNull(peer(it)) }
        val health = root["Health"]?.takeUnless { it == JsonNull }?.jsonArray?.also { require(it.size <= 128) }?.size
        return TailscaleSnapshot(observedAt, state, root.bool("TUN"), peer(root["Self"]), root.ips(), peers, peer(root["ExitNodeStatus"], true), health)
    }
}
