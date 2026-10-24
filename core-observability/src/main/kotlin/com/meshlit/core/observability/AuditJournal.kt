package com.meshlit.core.observability

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface AuditStorage { suspend fun read():String?; suspend fun write(jsonl:String) }
/** Bounded encrypted-at-rest adapter supplied by the app. Failed commits never claim success. */
class AuditJournal(private val storage:AuditStorage,private val clock:()->Long=System::currentTimeMillis) {
    private val lock=Mutex()
    private val _records=MutableStateFlow<List<AuditRecord>>(emptyList())
    val records=_records.asStateFlow()
    private val _status=MutableStateFlow("Not opened")
    val status=_status.asStateFlow()
    private var loaded=false
    var maxEntries=2000;private set
    var retentionDays=7;private set
    suspend fun open(limit:Int=maxEntries,days:Int=retentionDays)=lock.withLock {
        require(limit in 100..5000 && days in 1..90)
        if(!loaded){
            maxEntries=limit;retentionDays=days
            val data=storage.read()
            require(data==null || data.length<=8_000_000) { "Audit journal exceeds size limit" }
            val parsed=data?.lineSequence()?.filter{it.isNotBlank()}?.map(AuditRecord::parse)?.toList().orEmpty()
            val retained=trim(parsed)
            val normalized=retained.joinToString("\n"){it.jsonLine()}
            if(data!=null && data!=normalized)storage.write(normalized)
            _records.value=retained;loaded=true;_status.value="Opened ${_records.value.size} retained records"
        }
    }
    suspend fun configure(limit:Int,days:Int)=lock.withLock {
        require(limit in 100..5000 && days in 1..90)
        val oldLimit=maxEntries;val oldDays=retentionDays
        maxEntries=limit;retentionDays=days
        try{commit(trim(_records.value))}catch(t:Exception){maxEntries=oldLimit;retentionDays=oldDays;throw t}
    }
    suspend fun append(batch:List<AuditRecord>)=lock.withLock {
        check(loaded){"Audit journal not opened"}
        commit(trim(_records.value+batch.map(AuditRecord::safe)))
    }
    suspend fun clear()=lock.withLock { commit(emptyList()) }
    private fun trim(records:List<AuditRecord>)=records.filter{it.timeMs>=clock()-retentionDays*86_400_000L && it.timeMs<=clock()+60_000}.takeLast(maxEntries)
    private suspend fun commit(next:List<AuditRecord>) {
        try{storage.write(next.joinToString("\n"){it.jsonLine()});_records.value=next;_status.value="Saved ${next.size} records at ${clock()}"}
        catch(e:Exception){_status.value="Audit storage failed; batch not committed";throw e}
    }
    fun snapshot(filter:AuditFilter=AuditFilter())=records.value.filter(filter::matches)
}
data class AuditFilter(val query:String="",val source:AuditSource?=null,val actor:AuditActor?=null,val outcome:AuditOutcome?=null,val sinceMs:Long=0) {
    fun matches(r:AuditRecord)=r.timeMs>=sinceMs && (source==null || source==r.source) && (actor==null || actor==r.actor) && (outcome==null || outcome==r.outcome) && "${r.action} ${r.targetHash.orEmpty()} ${r.measurements.keys}".contains(query.trim(),true)
}
object AuditExport {
    fun csvHeader()="id,time_ms,source,action,actor,outcome,target_hash,measurements_json"
    fun csv(r:AuditRecord):String {
        val s=r.safe();val measurements=kotlinx.serialization.json.Json.parseToJsonElement(s.jsonLine()).let{(it as kotlinx.serialization.json.JsonObject).getValue("measurements").toString()}
        return listOf(s.id,s.timeMs.toString(),s.source.name,s.action,s.actor.name,s.outcome.name,s.targetHash.orEmpty(),measurements).joinToString(","){"\"${it.replace("\"","\"\"")}\""}
    }
}
