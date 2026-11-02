package com.meshlit.core.mcp.control

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class DeviceConnectionCoordinatorTest {
    private class Store:DeviceConnectionStore {
        var value=DeviceConnectionSnapshot();var fail=false
        override fun load()=value
        override fun save(value:DeviceConnectionSnapshot){check(!fail);this.value=value}
    }
    private val source=DeviceConnectionSource("model:one","Approved desktop",ConnectionTransport.MODEL_API,"binding-1",true,identityLabel="Saved HTTPS identity")
    private fun approved(c:DeviceConnectionCoordinator){c.setEnabled(true);c.setSourceAllowed(source.id,true,source.binding)}
    @Test fun connectionRequiresFeatureSourceApprovalAndRealProbe()=runBlocking {
        var probes=0;val store=Store()
        val c=DeviceConnectionCoordinator(store,{listOf(source)},{},{_,check->check();probes++;DeviceConnectionProbe(true,"Model catalog verified",itemCount=2)})
        assertFalse(c.offer("chat:one").enabled);assertTrue(c.offer("chat:one").choices.isEmpty())
        c.setEnabled(true);val denied=c.offer("chat:one");assertFalse(denied.choices.single().canConnect)
        try{c.connect("chat:one",denied.id,source.id);fail()}catch(_:IllegalArgumentException){}
        assertEquals(0,probes)
        c.setSourceAllowed(source.id,true,source.binding);val offer=c.offer("chat:one")
        val result=c.connect("chat:one",offer.id,source.id)
        assertEquals(ConnectionPhase.VERIFIED,result.phase);assertEquals(2,result.itemCount)
        assertEquals(result,c.connect("chat:one",offer.id,source.id));assertEquals(1,probes)
        c.setSourceAllowed(source.id,false)
        try{c.connect("chat:one",offer.id,source.id);fail()}catch(_:IllegalArgumentException){}
        assertEquals(1,probes)
    }
    @Test fun changedIdentityExpiredChoicesAndOtherConversationNeverConnect()=runBlocking {
        var now=100L;var current=source;var probes=0
        val c=DeviceConnectionCoordinator(Store(),{listOf(current)},{},{_,_->probes++;DeviceConnectionProbe(true,"verified")},clock={now})
        approved(c);val offer=c.offer("chat:one")
        try{c.connect("chat:two",offer.id,source.id);fail()}catch(_:IllegalArgumentException){}
        current=source.copy(binding="changed-host-pin")
        try{c.connect("chat:one",offer.id,source.id);fail()}catch(_:IllegalStateException){}
        current=source;now+=120001
        try{c.connect("chat:one",offer.id,source.id);fail()}catch(_:IllegalArgumentException){}
        assertEquals(0,probes)
    }
    @Test fun setupOnlyAdvertisementsCannotBeApprovedAsConnections() {
        val unknown=source.copy(supported=false,setupReason="Review the first peer identity in Devices")
        val c=DeviceConnectionCoordinator(Store(),{listOf(unknown)},{},{_,_->error("Must not probe")})
        c.setEnabled(true);assertFalse(c.offer("chat:one").choices.single().canConnect)
        try{c.setSourceAllowed(unknown.id,true,unknown.binding);fail()}catch(_:IllegalArgumentException){}
    }
    @Test fun transientReadProbeRetriesOnceButAuthFailureNeverRetries()=runBlocking {
        for(retryable in listOf(true,false)) {
            var calls=0
            val c=DeviceConnectionCoordinator(Store(),{listOf(source)},{},{_,_->calls++;if(calls==1)DeviceConnectionProbe(false,"Contact failed",retryable) else DeviceConnectionProbe(true,"Actual catalog received")},retryDelayMs=0)
            approved(c);val result=c.connect("chat:one",c.offer("chat:one").id,source.id)
            assertEquals(if(retryable)2 else 1,calls)
            assertEquals(if(retryable)ConnectionPhase.VERIFIED else ConnectionPhase.FAILED,result.phase)
        }
    }
    @Test fun timeoutCancelsEachProbeAndDoesNotClaimAConnection()=runBlocking {
        var closed=0
        val c=DeviceConnectionCoordinator(Store(),{listOf(source)},{},{_,_->try{awaitCancellation()}finally{closed++}},attemptTimeoutMs=20,retryDelayMs=0)
        approved(c);val result=c.connect("chat:one",c.offer("chat:one").id,source.id)
        assertEquals(ConnectionPhase.TIMED_OUT,result.phase);assertEquals(2,closed)
    }
    @Test fun stopPropagatesAndRecordsCancellationWithoutReplay()=runBlocking {
        val started=CompletableDeferred<Unit>();var closed=false;var calls=0
        val c=DeviceConnectionCoordinator(Store(),{listOf(source)},{},{_,_->calls++;started.complete(Unit);try{awaitCancellation()}finally{closed=true}})
        approved(c);val offer=c.offer("chat:one")
        val task=launch { c.connect("chat:one",offer.id,source.id) };started.await();task.cancelAndJoin()
        assertTrue(closed);assertEquals(1,calls);assertEquals(ConnectionPhase.CANCELLED,c.receipts("chat:one").single().phase)
    }
    @Test fun revokedPermissionDuringProbeDiscardsItsResponse()=runBlocking {
        val started=CompletableDeferred<Unit>();var calls=0
        val c=DeviceConnectionCoordinator(Store(),{listOf(source)},{},{_,check->calls++;started.complete(Unit);delay(5000);check();DeviceConnectionProbe(true,"Must not publish")})
        approved(c);val offer=c.offer("chat:one")
        val task=launch { c.connect("chat:one",offer.id,source.id) };started.await();c.setEnabled(false);task.join()
        assertEquals(1,calls);assertEquals(ConnectionPhase.REVOKED,c.receipts("chat:one").single().phase)
    }
    @Test fun interruptedProcessesNeverAutomaticallyReconnect() {
        val store=Store();store.value=DeviceConnectionSnapshot(receipts=listOf(DeviceConnectionReceipt("old","chat:one",source.id,source.name,source.transport,ConnectionPhase.CONNECTING,1,2)))
        val c=DeviceConnectionCoordinator(store,{listOf(source)},{},{_,_->error("No automatic replay")})
        assertEquals(ConnectionPhase.INTERRUPTED,c.state.value.receipts.single().phase)
    }
    @Test fun failedPolicyStorageDoesNotEnableAutomation() {
        val store=Store();val c=DeviceConnectionCoordinator(store,{listOf(source)},{},{_,_->error("No probe")})
        store.fail=true
        try{c.setEnabled(true);fail()}catch(_:IllegalStateException){}
        assertFalse(c.state.value.policy.enabled)
        try{c.offer("chat:one");fail()}catch(_:IllegalStateException){}
    }
}
