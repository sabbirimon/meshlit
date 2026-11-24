package com.meshlit.core.mcp.control
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.security.MessageDigest

@Serializable data class SensorObservation(val type:Int,val name:String,val units:List<String> = emptyList(),val values:List<Double>,val observedAtMs:Long)
@Serializable data class DeviceObservation(val receiptId:String,val sourceId:String,val sourceName:String,val observedAtMs:Long,val receivedAtMs:Long,
    val availableRamBytes:Long?,val totalRamBytes:Long?,val cpuCores:Int?,val sensors:List<SensorObservation>,val payloadSha256:String,val untrustedData:Boolean=true) {
    fun fresh(now:Long,maxAgeMs:Long=30_000)=maxAgeMs in 1..60_000 && now-observedAtMs in 0..maxAgeMs && now-receivedAtMs in 0..maxAgeMs && sensors.all{now-it.observedAtMs in 0..maxAgeMs}
}
/** Only typed observations cross into task context. Text supplied by a source is evidence, never instructions. */
object DeviceObservations {
    fun parse(sourceId:String,sourceName:String,body:JsonElement,now:Long):DeviceObservation {
        require(sourceId.length in 1..200 && sourceName.length in 1..100)
        require(body.toString().length<=262144){"Device response exceeds limit"}
        var root=body.jsonObject
        (root["result"] as? JsonObject)?.let{root=it}
        (root["structuredContent"] as? JsonObject)?.let{root=it}
        if(root["observedAtMs"]==null){
            val text=(root["content"] as? JsonArray)?.singleOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content ?: error("Resource snapshot not supplied")
            require(text.length<=262144)
            var depth=0;var quoted=false;var escaped=false
            text.forEach{c->if(quoted){if(escaped)escaped=false else if(c=='\\')escaped=true else if(c=='"')quoted=false}else when(c){'"'->quoted=true;'[','{'->{depth++;require(depth<=32)};']','}'->{depth--;require(depth>=0)}}}
            root=Json.parseToJsonElement(text).jsonObject
        }
        fun primitive(key:String):JsonPrimitive?=(root[key] as? JsonPrimitive)?.also{require(it is JsonNull || !it.isString){"Resource numbers must be JSON numbers"}}
        val observed=checkNotNull(primitive("observedAtMs")).long
        require(now-observed in -5000..60_000){"Stale resource report or source clock mismatch"}
        val free=primitive("availableRamBytes")?.longOrNull
        val total=primitive("totalRamBytes")?.longOrNull
        val cores=primitive("cpuCores")?.intOrNull
        require(free==null || free>=0);require(total==null || total>0);require(free==null || total==null || free<=total)
        require(cores==null || cores in 1..1024)
        val rows=(root["sharedLiveSensors"] as? JsonArray).orEmpty();require(rows.size<=128)
        val sensors=rows.map {entry->
            val row=entry.jsonObject;val values=row["values"]!!.jsonArray.map{it.jsonPrimitive.also{p->require(!p.isString)}.double}
            require(values.size in 1..16 && values.all{it.isFinite()})
            val at=row["observedAtMs"]!!.jsonPrimitive.long;require(now-at in -5000..60_000)
            val units=when(val unit=row["units"]){
                is JsonArray -> unit.map{it.jsonPrimitive.also{p->require(p.isString)}.content.take(64)}.also{require(it.size==values.size)}
                is JsonPrimitive -> List(values.size){unit.content.take(64)}
                else -> List(values.size){"unknown"}
            }
            SensorObservation(row["type"]!!.jsonPrimitive.int,row["name"]!!.jsonPrimitive.content.take(100),units,values,at)
        }
        require(free!=null || cores!=null || sensors.isNotEmpty()){"No supported observation values"}
        val digest=MessageDigest.getInstance("SHA-256").digest(root.toString().toByteArray()).joinToString(""){"%02x".format(it)}
        return DeviceObservation(java.util.UUID.randomUUID().toString(),sourceId,sourceName,observed,now,free,total,cores,sensors,digest)
    }
    /** Capacity recommendation only. Execution requires a separately approved and implemented task adapter. */
    fun rankForCpuTask(items:List<DeviceObservation>,requiredRam:Long,now:Long):List<String> {
        require(requiredRam>0)
        return items.filter{it.fresh(now) && (it.availableRamBytes ?: 0)>=requiredRam && it.cpuCores!=null}
            .sortedWith(compareByDescending<DeviceObservation>{it.cpuCores}.thenByDescending{it.availableRamBytes}).map{it.sourceId}
    }
}
