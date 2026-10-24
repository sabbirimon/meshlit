package com.meshlit.core.ssh

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable enum class NodeSshAction { STATUS, VM_STATUS, VM_START, VM_WAIT, VM_STOP, VM_EXEC, APP_EXEC }
@Serializable enum class NodeSshScope { STATUS, VM_CONTROL, VM_EXEC, APP_EXEC }
@Serializable data class NodeSshRequest(val action:NodeSshAction, val argv:List<String> = emptyList()) {
    fun validate() {
        if(action in setOf(NodeSshAction.APP_EXEC, NodeSshAction.VM_EXEC)) {
            require(argv.size in 1..32 && argv.all { it.length in 1..512 && '\u0000' !in it }) { "Expected 1–32 bounded argv strings" }
        } else require(argv.isEmpty()) { "This action does not accept argv" }
    }
    fun requireAppDiagnostics() {
        require(action==NodeSshAction.APP_EXEC)
        require(argv.first() in setOf("/system/bin/id", "/system/bin/uname", "/system/bin/uptime", "/system/bin/df")) {
            "App SSH is limited to installed read-only Android diagnostics; use the VM for shell programs"
        }
    }
    val scope get()=when(action) {
        NodeSshAction.STATUS, NodeSshAction.VM_STATUS -> NodeSshScope.STATUS
        NodeSshAction.VM_START, NodeSshAction.VM_WAIT, NodeSshAction.VM_STOP -> NodeSshScope.VM_CONTROL
        NodeSshAction.VM_EXEC -> NodeSshScope.VM_EXEC
        NodeSshAction.APP_EXEC -> NodeSshScope.APP_EXEC
    }
    companion object {
        fun parse(command:String):NodeSshRequest {
            require(command.toByteArray(Charsets.UTF_8).size in 1..4096 && '\u0000' !in command)
            return (if(command=="status") NodeSshRequest(NodeSshAction.STATUS)
                else Json.decodeFromString<NodeSshRequest>(command)).also { it.validate() }
        }
    }
}
@Serializable data class NodeSshGrant(val id:String, val name:String, val username:String,
    val publicKey:String, val scopes:Set<NodeSshScope> = setOf(NodeSshScope.STATUS), val agent:Boolean=false) {
    fun validate() {
        require(id.matches(Regex("[A-Za-z0-9-]{1,80}")) && name.length in 1..100)
        require(username.matches(Regex("[a-z][a-z0-9_-]{0,31}")))
        require(publicKey.length in 1..4096 && '\n' !in publicKey && '\r' !in publicKey)
        require(scopes.isNotEmpty() && (!agent || NodeSshScope.APP_EXEC !in scopes)) { "Agent keys cannot grant Android app execution" }
    }
    fun requireRequest(request:NodeSshRequest) {
        validate();request.validate();check(request.scope in scopes) { "SSH key lacks this command scope" }
        check(!agent || request.action!=NodeSshAction.APP_EXEC) { "Agent app execution is blocked" }
    }
}
@Serializable data class NodeSshBind(val address:String="127.0.0.1",val port:Int=2223) {
    fun validate() {
        require(port in 1024..65535)
        val numbers=address.split('.').map { it.toIntOrNull() }
        require(numbers.size==4 && numbers.all { it!=null && it in 0..255 }) { "Enter a literal private IPv4 address" }
        require(address==numbers.joinToString(".")) { "Use canonical IPv4" }
        require(address=="127.0.0.1" || numbers[0]==10 || (numbers[0]==172 && numbers[1] in 16..31) ||
            (numbers[0]==192 && numbers[1]==168)) { "Bind loopback or one private LAN address; public/all-interface binding is blocked" }
    }
}
@Serializable data class NodeSshReply(val ok:Boolean, val text:String, val exitCode:Int?=null, val truncated:Boolean=false)
