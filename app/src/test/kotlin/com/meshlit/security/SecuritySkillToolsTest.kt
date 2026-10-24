package com.meshlit.security

import com.meshlit.core.common.control.*
import com.meshlit.core.mcp.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class SecuritySkillToolsTest {
    @Test fun referencesNeverGrantExecutionAndUnknownIdsFail()=runBlocking {
        val tools=SecuritySkillTools(OperationGate()).specs()
        val read=tools.single{it.name=="cyber_skill_read"}
        assertTrue(read.handler(buildJsonObject{put("id","not-a-skill")}) is McpToolResult.Error)
        val result=read.handler(buildJsonObject{put("id","security-report")})
        assertTrue(result is McpToolResult.Json)
        assertTrue(result.toString().contains("does not authorize tool execution"))
    }
    @Test fun emergencyStopAndAgentRevocationStillApply()=runBlocking {
        val gate=OperationGate();val read=SecuritySkillTools(gate).specs().first()
        gate.setFeature(ManagedFeature.CYBER,false,agentOnly=true)
        try{read.handler(buildJsonObject{});fail("Agent revocation must block references")}catch(_:IllegalStateException){}
        gate.setFeature(ManagedFeature.CYBER,true,agentOnly=true);gate.emergencyStop()
        try{read.handler(buildJsonObject{});fail("Emergency stop must remain active")}catch(_:IllegalStateException){}
    }
}
