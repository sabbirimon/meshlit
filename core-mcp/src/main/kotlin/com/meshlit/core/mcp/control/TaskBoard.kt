package com.meshlit.core.mcp.control

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable enum class TaskPhase { OPEN, IN_PROGRESS, BLOCKED, DONE, CANCELLED }
@Serializable enum class TaskPriority { LOW, NORMAL, HIGH, URGENT }
@Serializable data class ManagedTask(val id:String,val title:String,val notes:String="",val phase:TaskPhase=TaskPhase.OPEN,
    val priority:TaskPriority=TaskPriority.NORMAL,val tags:Set<String> = emptySet(),val dueAtMs:Long?=null,
    val parentId:String?=null,val linkedJobId:String?=null,val revision:Long=1,
    val createdAtMs:Long=System.currentTimeMillis(),val updatedAtMs:Long=createdAtMs)
@Serializable data class TaskMutation(val id:String?=null,val expectedRevision:Long?=null,val title:String?=null,
    val notes:String?=null,val phase:TaskPhase?=null,val priority:TaskPriority?=null,val tags:Set<String>?=null,
    val dueAtMs:Long?=null,val clearDue:Boolean=false,val parentId:String?=null,val linkedJobId:String?=null,
    val ids:List<String> = emptyList())
interface TaskBoardStore { suspend fun load():List<ManagedTask>;suspend fun save(tasks:List<ManagedTask>) }
/** Manual planning records; IN_PROGRESS/DONE do not assert engine execution. */
class TaskBoard(private val store:TaskBoardStore,private val scope:CoroutineScope) {
    private val lock=Mutex();private val state=MutableStateFlow<List<ManagedTask>>(emptyList())
    val tasks=state.asStateFlow()
    val ready=scope.async{lock.withLock{state.value=store.load();require(state.value.size<=200)}}
    private fun validate(task:ManagedTask){
        require(task.title.isNotBlank() && task.title.length<=200 && task.notes.length<=6000)
        require(task.tags.size<=10 && task.tags.all{it.isNotBlank() && it.length<=32})
        require(task.dueAtMs==null || task.dueAtMs>=0)
        require(task.linkedJobId==null || task.linkedJobId.matches(Regex("[A-Za-z0-9_-]{1,80}")))
    }
    private suspend fun save(next:List<ManagedTask>){store.save(next);state.value=next}
    suspend fun create(input:TaskMutation):ManagedTask {ready.await();return lock.withLock{
        require(state.value.size<200)
        require(input.parentId==null || state.value.any{it.id==input.parentId})
        val task=ManagedTask(UUID.randomUUID().toString(),input.title.orEmpty(),input.notes.orEmpty(),
            priority=input.priority ?: TaskPriority.NORMAL,tags=input.tags.orEmpty(),dueAtMs=input.dueAtMs,
            parentId=input.parentId,linkedJobId=input.linkedJobId)
        validate(task);save(state.value+task);task
    }}
    suspend fun update(input:TaskMutation):ManagedTask {ready.await();return lock.withLock{
        val old=state.value.single{it.id==input.id};require(input.expectedRevision==old.revision){"Task changed; refresh before saving"}
        val next=old.copy(title=input.title ?: old.title,notes=input.notes ?: old.notes,phase=input.phase ?: old.phase,
            priority=input.priority ?: old.priority,tags=input.tags ?: old.tags,
            dueAtMs=if(input.clearDue) null else input.dueAtMs ?: old.dueAtMs,
            linkedJobId=input.linkedJobId ?: old.linkedJobId,revision=old.revision+1,updatedAtMs=System.currentTimeMillis())
        validate(next);save(state.value.map{if(it.id==old.id) next else it});next
    }}
    suspend fun batch(ids:List<String>,phase:TaskPhase):List<ManagedTask>{ready.await();return lock.withLock{
        require(ids.isNotEmpty() && ids.size<=200 && ids.distinct().size==ids.size)
        require(ids.all{id ->state.value.any{it.id==id}}){"Unknown task; no records changed"}
        val next=state.value.map{if(it.id in ids) it.copy(phase=phase,revision=it.revision+1,updatedAtMs=System.currentTimeMillis()) else it}
        save(next);next.filter{it.id in ids}
    }}
    suspend fun delete(id:String,expectedRevision:Long){ready.await();lock.withLock{
        require(state.value.single{it.id==id}.revision==expectedRevision)
        require(state.value.none{it.parentId==id}){"Delete child tasks first"}
        save(state.value.filterNot{it.id==id})
    }}
}
