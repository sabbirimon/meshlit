package com.meshlit.devices

/** Formats discovered IPv4, DNS and IPv6 hosts as a valid HTTP endpoint. */
fun endpointUrl(host: String, port: Int, secure: Boolean = false): String {
    require(port in 1..65535) { "Invalid endpoint port" }
    val address = host.trim().removePrefix("[").removeSuffix("]").replace("%25", "%")
    require(address.isNotBlank()) { "Missing endpoint host" }
    val authority = if (':' in address) "[${address.replace("%", "%25")}]" else address
    return "${if (secure) "https" else "http"}://$authority:$port"
}
