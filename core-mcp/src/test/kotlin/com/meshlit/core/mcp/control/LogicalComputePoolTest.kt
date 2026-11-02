package com.meshlit.core.mcp.control
import org.junit.Assert.*
import org.junit.Test
class LogicalComputePoolTest {
    private class Store:PoolNodeStore {var value=PoolNodeSnapshot();var fail=false;override fun load()=value;override fun save(value:PoolNodeSnapshot){check(!fail);this.value=value}}
    @Test fun twoCoordinatorsCannotDoubleAllocateAndReplayCannotRestartAJob() {
        val store=Store();var time=1000L
        val node=LogicalComputePoolNode(store,{NodeCapacity(time,ResourceBudget(8,1000,0))},{time})
        node.configure(NodePoolPolicy(1,setOf("a","b"),ResourceBudget(6,1000),PoolControlMode.MULTI_COORDINATOR),0)
        val req=AllocationRequest("one","task",1,ResourceBudget(4,500),1000)
        val lease=node.reserve("a",req);assertEquals(lease,node.reserve("a",req))
        assertThrows(IllegalArgumentException::class.java){node.reserve("b",req.copy(requestId="two"))}
        time=3000;assertEquals(1,node.expired().size)
        assertThrows(IllegalArgumentException::class.java){node.reserve("b",req.copy(requestId="two"))}
        node.acknowledgeRelease("a",lease.id)
        assertThrows(IllegalArgumentException::class.java){node.reserve("a",req)}
        assertNotNull(node.reserve("b",req.copy(requestId="two")))
    }
    @Test fun durableFailureAndStaleCapacityFailClosed() {
        val store=Store();val node=LogicalComputePoolNode(store,{NodeCapacity(0,ResourceBudget(8,1000))},{20_000})
        node.configure(NodePoolPolicy(1,setOf("a"),ResourceBudget(4,1000)),0)
        assertThrows(IllegalArgumentException::class.java){node.reserve("a",AllocationRequest("x","t",1,ResourceBudget(1)))}
        store.fail=true;assertThrows(IllegalStateException::class.java){node.configure(NodePoolPolicy(2,setOf("a"),ResourceBudget(2,1000)),1)}
        assertEquals(1L,node.read().policy.revision)
    }
}
