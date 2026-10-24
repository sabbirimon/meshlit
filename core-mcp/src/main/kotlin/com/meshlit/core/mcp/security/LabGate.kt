package com.meshlit.core.mcp.security

/** Root availability never substitutes for isolation/readiness. PRoot is not a security sandbox. */
object LabGate {
    fun requireReady(mode:String,state:String,agent:Boolean=false,agentEnabled:Boolean=false) {
        require(mode=="VM_SSH" && state=="SSH_READY") {"Security Lab requires an SSH-ready VM"}
        require(!agent || agentEnabled) {"Agent VM access disabled"}
    }
}
