package com.meshlit.core.mcp.security

import kotlinx.serialization.Serializable

@Serializable data class SecurityAssessment(val id:String,val owner:String,val target:String,val sha256:String,
    val sshHostId:String,val expiresAtMs:Long,val agentAllowed:Boolean=false,val tools:Set<String> = emptySet(),val environmentId:String="") {
    fun validate() {require(id.matches(Regex("[A-Za-z0-9_-]{1,80}")));require(owner.length in 1..100)
        require(target.startsWith('/') && target.length<=1000 && target.none {it=='\u0000' || it=='\n' || it=='\r'})
        require(sha256.matches(Regex("[a-fA-F0-9]{64}")));require(sshHostId.matches(Regex("[A-Za-z0-9-]{1,80}")))
        require(environmentId.isEmpty() || environmentId.matches(Regex("[a-f0-9]{64}")))
        require(tools.all {it in CyberTools.ids});require(expiresAtMs>0)
    }
    fun authorize(tool:String,host:String,digest:String,now:Long,agent:Boolean) {
        validate();require(tool in tools && host==sshHostId && digest.equals(sha256,true) && now<expiresAtMs) {"Assessment grant does not match or expired"}
        require(!agent || agentAllowed) {"Agent assessment access is disabled"}
    }
}
data class CyberTool(val id:String,val name:String,val role:String,val implemented:Boolean=false)
object CyberTools {
    val all=listOf(
        CyberTool("apk_inventory","APK inventory","Read-only original ZIP/DEX inventory",true),
        CyberTool("pcap_summary","PCAP summary","Bounded classic-PCAP parser",true),
        CyberTool("elf_inventory","Linux ELF inventory","Read-only architecture/header metadata",true),
        CyberTool("sqlite_metadata","SQLite metadata","Immutable schema/page metadata; no row export",true),
        CyberTool("tshark","Wireshark / tshark","Host packet metadata",true),
        CyberTool("androguard","Androguard","Static APK/DEX analysis"),CyberTool("quark","Quark","APK rules"),
        CyberTool("objection","Objection / Frida","Owned-app runtime inspection"),CyberTool("drozer","Drozer","Android IPC assessment"),
        CyberTool("metasploit","Metasploit","Approved Linux lab"),CyberTool("havoc","Havoc","Isolated lab report/session import"),
        CyberTool("magisk","Magisk","User-managed root broker"),CyberTool("atomic","Atomic Red Team","Selected detection tests"),
        CyberTool("autopsy","Autopsy","Desktop forensic reports"),CyberTool("sleuthkit","Sleuth Kit","Read-only fsstat filesystem metadata",true),
        CyberTool("sandbox","Qualified sandbox","Guest isolation/reset acceptance"),CyberTool("pcapdroid","PCAPdroid","Separate Android capture companion")
    )
    val ids=all.map {it.id}.toSet()
}
