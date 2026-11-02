package com.meshlit.core.mcp.control
import kotlinx.serialization.Serializable

@Serializable data class ResourceBudget(val cpu:Int=0,val ramBytes:Long=0,val gpuSlots:Int=0) {
    fun validate(){require(cpu in 0..128 && ramBytes in 0..(1L shl 50) && gpuSlots in 0..64)}
    operator fun plus(other:ResourceBudget)=ResourceBudget(cpu+other.cpu,ramBytes+other.ramBytes,gpuSlots+other.gpuSlots)
    fun fits(cap:ResourceBudget)=cpu<=cap.cpu && ramBytes<=cap.ramBytes && gpuSlots<=cap.gpuSlots
}
@Serializable enum class PoolControlMode { SINGLE_COORDINATOR, MULTI_COORDINATOR }
@Serializable data class NodePoolPolicy(val revision:Long=0,val coordinators:Set<String> = emptySet(),val ceiling:ResourceBudget=ResourceBudget(),val mode:PoolControlMode=PoolControlMode.SINGLE_COORDINATOR) {
    fun validate(){ceiling.validate();require(revision>=0 && coordinators.size<=8 && coordinators.all{it.matches(Regex("[A-Za-z0-9_-]{1,128}"))});if(mode==PoolControlMode.SINGLE_COORDINATOR)require(coordinators.size<=1)}
}
@Serializable data class NodeCapacity(val observedAtMs:Long,val budget:ResourceBudget)
@Serializable data class AllocationRequest(val requestId:String,val taskId:String,val policyRevision:Long,val budget:ResourceBudget,val durationMs:Long=30_000) {
    fun validate(){require(requestId.matches(Regex("[A-Za-z0-9_-]{1,128}")) && taskId.matches(Regex("[A-Za-z0-9_-]{1,128}")));budget.validate();require(budget!=ResourceBudget() && durationMs in 1000..60_000)}
}
@Serializable enum class AllocationState { RESERVED, EXPIRED, REVOKED, RELEASED }
@Serializable data class ResourceLease(val id:String,val coordinator:String,val request:AllocationRequest,val createdAtMs:Long,val expiresAtMs:Long,val state:AllocationState=AllocationState.RESERVED)
@Serializable data class PoolNodeSnapshot(val policy:NodePoolPolicy=NodePoolPolicy(),val leases:List<ResourceLease> = emptyList(),val recordedAtMs:Long=0)
interface PoolNodeStore { fun load():PoolNodeSnapshot;fun save(value:PoolNodeSnapshot) }
/** Per-node durable authority serializes competing coordinators. Expiry requests cancellation; capacity is released only after worker acknowledgement. */
class LogicalComputePoolNode(private val store:PoolNodeStore,private val capacity:()->NodeCapacity,private val now:()->Long={System.currentTimeMillis()}) {
    private var snapshot=store.load().also{it.policy.validate()}
    private fun commit(value:PoolNodeSnapshot){store.save(value);snapshot=value}
    @Synchronized fun read():PoolNodeSnapshot=snapshot
    @Synchronized fun configure(policy:NodePoolPolicy,expectedRevision:Long) {
        policy.validate();require(snapshot.policy.revision==expectedRevision && policy.revision==expectedRevision+1){"Policy revision conflict"}
        val leases=snapshot.leases.map{if(it.state!=AllocationState.RELEASED)it.copy(state=AllocationState.REVOKED) else it}
        commit(snapshot.copy(policy=policy,leases=leases,recordedAtMs=maxOf(snapshot.recordedAtMs,now())))
    }
    @Synchronized fun reserve(authenticatedCoordinator:String,request:AllocationRequest):ResourceLease {
        request.validate();val time=now();require(time>=snapshot.recordedAtMs){"Clock moved backwards; reconcile before allocating"}
        require(authenticatedCoordinator in snapshot.policy.coordinators){"Coordinator not authorized"}
        snapshot.leases.firstOrNull{it.request.requestId==request.requestId}?.let{
            require(it.coordinator==authenticatedCoordinator && it.request==request){"Allocation ID conflict"}
            require(it.state==AllocationState.RESERVED && time<it.expiresAtMs){"Allocation already ended; use a new task attempt ID"}
            return it
        }
        require(request.policyRevision==snapshot.policy.revision){"Stale policy revision"}
        val observed=capacity();observed.budget.validate();require(time-observed.observedAtMs in 0..10_000){"Capacity evidence stale"}
        val cap=ResourceBudget(minOf(observed.budget.cpu,snapshot.policy.ceiling.cpu),minOf(observed.budget.ramBytes,snapshot.policy.ceiling.ramBytes),minOf(observed.budget.gpuSlots,snapshot.policy.ceiling.gpuSlots))
        val occupied=snapshot.leases.filter{it.state!=AllocationState.RELEASED}.fold(ResourceBudget()){a,l->a+l.request.budget}
        require((occupied+request.budget).fits(cap)){"Requested resources exceed unreserved node capacity"}
        require(snapshot.leases.count{it.state!=AllocationState.RELEASED}<128){"Lease capacity reached"}
        val lease=ResourceLease(java.util.UUID.randomUUID().toString(),authenticatedCoordinator,request,time,time+request.durationMs)
        val retained=snapshot.leases.filter{it.state!=AllocationState.RELEASED}+snapshot.leases.filter{it.state==AllocationState.RELEASED}.takeLast(128)+lease
        commit(snapshot.copy(leases=retained,recordedAtMs=time));return lease
    }
    @Synchronized fun expired():List<ResourceLease> {
        val time=now();val leases=snapshot.leases.map{if(it.state==AllocationState.RESERVED && time>=it.expiresAtMs)it.copy(state=AllocationState.EXPIRED) else it}
        if(leases!=snapshot.leases)commit(snapshot.copy(leases=leases,recordedAtMs=maxOf(time,snapshot.recordedAtMs)))
        return leases.filter{it.state in setOf(AllocationState.EXPIRED,AllocationState.REVOKED)}
    }
    /** Invoke only after the worker has stopped/released its actual allocation. Network request identity comes from authenticated transport, never the body. */
    @Synchronized fun acknowledgeRelease(authenticatedCoordinator:String,leaseId:String) {
        val lease=snapshot.leases.single{it.id==leaseId};require(lease.coordinator==authenticatedCoordinator)
        if(lease.state==AllocationState.RELEASED)return
        commit(snapshot.copy(leases=snapshot.leases.map{if(it.id==leaseId)it.copy(state=AllocationState.RELEASED) else it},recordedAtMs=maxOf(now(),snapshot.recordedAtMs)))
    }
}
