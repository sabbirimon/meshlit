package com.meshlit.agent

import android.content.Context
import android.util.AtomicFile
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** Human-created job templates. A profile contains no credential or tool/device grant. */
@Serializable data class AgentProfile(
    val id:String=UUID.randomUUID().toString(),
    val name:String,
    val modelId:String,
    val instruction:String="",
    val enabled:Boolean=false,
) {
    fun validate() {
        UUID.fromString(id)
        require(name.isNotBlank() && name.length<=48 && name.none(Char::isISOControl))
        require(modelId.length in 1..128 && modelId.none(Char::isISOControl))
        require(instruction.length<=1200 && instruction.none { it=='\u0000' })
    }
}

class AgentProfileStore(context:Context) {
    private val file=AtomicFile(File(context.noBackupFilesDir,"agent-profiles.json"))
    private val json=Json { ignoreUnknownKeys=false }
    @Synchronized fun load():List<AgentProfile> {
        if(!file.baseFile.exists()) return emptyList()
        require(file.baseFile.length()<=65536) { "Agent profiles are too large" }
        val bytes=file.openRead().use { com.meshlit.util.readBounded(it, 65536) }
        require(bytes.size<=65536) { "Agent profiles are too large" }
        return json.decodeFromString<List<AgentProfile>>(bytes.toString(Charsets.UTF_8)).also(::validate)
    }
    @Synchronized fun save(profiles:List<AgentProfile>) {
        validate(profiles)
        val bytes=json.encodeToString(profiles).toByteArray(Charsets.UTF_8)
        require(bytes.size<=65536)
        val output=file.startWrite()
        try { output.write(bytes);file.finishWrite(output) }
        catch(error:Exception) { file.failWrite(output);throw error }
    }
    private fun validate(profiles:List<AgentProfile>) {
        require(profiles.size<=16 && profiles.map{it.id}.distinct().size==profiles.size)
        profiles.forEach(AgentProfile::validate)
    }
}
