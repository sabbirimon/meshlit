package com.meshlit.core.ssh
import org.junit.Assert.*
import org.junit.Test

class NodeSshProtocolTest {
    @Test fun rejectsPublicBindingsUnknownCommandsAndArguments() {
        NodeSshBind("127.0.0.1").validate();NodeSshBind("192.168.1.2").validate();NodeSshBind("172.16.1.2").validate()
        for(ip in listOf("0.0.0.0","8.8.8.8","172.15.1.2","localhost","192.168.01.2","10.0.0.256","::1")) {
            assertThrows(IllegalArgumentException::class.java) { NodeSshBind(ip).validate() }
        }
        assertEquals(NodeSshAction.STATUS,NodeSshRequest.parse("status").action)
        for(command in listOf("uname -a","{}","{\"action\":\"STATUS\",\"root\":true}","{\"action\":\"VM_START\",\"argv\":[\"sh\"]}","{\"action\":\"VM_EXEC\",\"argv\":[]}")) {
            assertTrue(runCatching { NodeSshRequest.parse(command) }.isFailure)
        }
    }
    @Test fun agentCannotGainAppOrConfigureRuntimeAndScopesAreSeparate() {
        val grant=NodeSshGrant("one","Client","meshlit","public-key",agent=true)
        grant.requireRequest(NodeSshRequest(NodeSshAction.STATUS))
        assertThrows(IllegalStateException::class.java) { grant.requireRequest(NodeSshRequest(NodeSshAction.VM_START)) }
        assertThrows(IllegalArgumentException::class.java) { grant.copy(scopes=setOf(NodeSshScope.APP_EXEC)).validate() }
        NodeSshRequest(NodeSshAction.APP_EXEC,listOf("/system/bin/id")).requireAppDiagnostics()
        for(program in listOf("/system/bin/su","/system/bin/sh","/system/bin/cat","/tmp/id")) {
            assertThrows(IllegalArgumentException::class.java) { NodeSshRequest(NodeSshAction.APP_EXEC,listOf(program)).requireAppDiagnostics() }
        }
    }
}
