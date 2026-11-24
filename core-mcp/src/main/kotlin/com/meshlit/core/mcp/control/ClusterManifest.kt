package com.meshlit.core.mcp.control
import kotlinx.serialization.Serializable

@Serializable data class ClusterSelector(val capability:String="resource-read",val minimumCpuCores:Int=1) {
    fun validate(){require(capability in setOf("resource-read","model-jobs","hyperl-cpu","sensor-source") && minimumCpuCores in 1..128)}
}
@Serializable data class DesiredCpuConfiguration(val mode:String="BALANCED",val threads:Int=4) {
    fun validate(){require(mode in setOf("BALANCED","BATTERY_SAVER","PERFORMANCE","OVERDRIVE","OVERDRIVE_PLUS") && threads in 1..128)}
}
@Serializable data class ClusterGroup(val name:String,val select:ClusterSelector=ClusterSelector(),val cpu:DesiredCpuConfiguration=DesiredCpuConfiguration())
@Serializable data class ClusterManifest(val schemaVersion:Int=1,val name:String,val revision:Long,val groups:List<ClusterGroup>) {
    fun validate(){require(schemaVersion==1 && name.matches(Regex("[A-Za-z0-9_-]{1,80}")) && revision>=0 && groups.size in 1..16 && groups.map{it.name}.distinct().size==groups.size)
        groups.forEach{require(it.name.matches(Regex("[A-Za-z0-9_-]{1,80}")));it.select.validate();it.cpu.validate()}}
}
data class ObservedClusterNode(val id:String,val approved:Boolean,val capabilities:Set<String>,val cores:Int,val observedAtMs:Long,val configRevision:Long,val cpu:DesiredCpuConfiguration)
@Serializable data class NodeConfigurationChange(val nodeId:String,val expectedRevision:Long,val before:DesiredCpuConfiguration,val after:DesiredCpuConfiguration)
@Serializable data class ClusterConfigurationPlan(val cluster:String,val manifestRevision:Long,val changes:List<NodeConfigurationChange>,val blocked:Map<String,String>,val unchanged:List<String>)
/** A selector never grants authority. Preview only; effects must use authenticated CAS settings adapters. */
object DeclarativeClusterController {
    fun preview(manifest:ClusterManifest,nodes:List<ObservedClusterNode>,now:Long):ClusterConfigurationPlan {
        manifest.validate();require(nodes.size<=10000 && nodes.map{it.id}.distinct().size==nodes.size)
        val changes=mutableListOf<NodeConfigurationChange>();val blocked=mutableMapOf<String,String>();val unchanged=mutableListOf<String>()
        for(node in nodes){
            val matched=manifest.groups.filter{it.select.capability in node.capabilities && node.cores>=it.select.minimumCpuCores}
            if(matched.isEmpty())continue
            if(!node.approved){blocked[node.id]="Owner enrollment required";continue}
            if(now-node.observedAtMs !in 0..30_000){blocked[node.id]="Resource observation stale";continue}
            val settings=matched.map{it.cpu}.distinct()
            if(settings.size!=1){blocked[node.id]="Conflicting group configuration";continue}
            val desired=settings.single()
            if(desired.threads>node.cores && desired.mode !in setOf("OVERDRIVE","OVERDRIVE_PLUS")){blocked[node.id]="Requested workers exceed reported CPU cores";continue}
            if(node.cpu==desired)unchanged+=node.id else changes+=NodeConfigurationChange(node.id,node.configRevision,node.cpu,desired)
        }
        return ClusterConfigurationPlan(manifest.name,manifest.revision,changes,blocked,unchanged)
    }
}
@Serializable data class ConfigurationDelta(val baseSha256:String,val changed:Map<String,kotlinx.serialization.json.JsonElement>,val removed:Set<String>)
object ConfigurationSnapshots {
    private fun hash(value:kotlinx.serialization.json.JsonObject)=java.security.MessageDigest.getInstance("SHA-256").digest(value.toString().toByteArray()).joinToString(""){"%02x".format(it)}
    fun delta(before:kotlinx.serialization.json.JsonObject,after:kotlinx.serialization.json.JsonObject):ConfigurationDelta {
        require(before.toString().length<=1_000_000 && after.toString().length<=1_000_000)
        return ConfigurationDelta(hash(before),after.filter{(key,value)->before[key]!=value},before.keys-after.keys)
    }
    fun restore(base:kotlinx.serialization.json.JsonObject,delta:ConfigurationDelta):kotlinx.serialization.json.JsonObject {
        require(hash(base)==delta.baseSha256){"Backup base does not match; no changes applied"}
        require(delta.changed.size<=256 && delta.removed.size<=256 && delta.changed.keys.intersect(delta.removed).isEmpty())
        return kotlinx.serialization.json.JsonObject((base-delta.removed)+delta.changed).also{require(it.toString().length<=1_000_000)}
    }
}
