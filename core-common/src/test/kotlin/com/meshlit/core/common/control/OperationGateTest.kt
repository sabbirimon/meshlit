package com.meshlit.core.common.control
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
class OperationGateTest {
    @Test fun masterPauseCancelsOnlyAgentsAndSurvivesRestart() = runBlocking {
        var saved=OperationPolicy();val gate=OperationGate(persist={saved=it})
        val agentStarted=CompletableDeferred<Unit>();val humanStarted=CompletableDeferred<Unit>()
        val agent=launch{gate.run(ManagedFeature.INFERENCE,true){agentStarted.complete(Unit);awaitCancellation()}}
        val human=launch{gate.run(ManagedFeature.INFERENCE,false){humanStarted.complete(Unit);awaitCancellation()}}
        agentStarted.await();humanStarted.await();gate.setAgentsEnabled(false);agent.join()
        assertTrue(agent.isCancelled);assertTrue(human.isActive)
        val reopened=OperationGate(saved)
        assertThrows(IllegalStateException::class.java){reopened.requireAllowed(ManagedFeature.AUTOMATION,true)}
        reopened.requireAllowed(ManagedFeature.INFERENCE,false)
        human.cancelAndJoin()
    }
    @Test fun masterResumeDoesNotGrantRestrictedFeaturesOrClearEmergencyStop() {
        val gate=OperationGate(OperationPolicy(emergencyStopped=true,agentDisabled=setOf(ManagedFeature.CLOUD),agentsPaused=true))
        gate.setAgentsEnabled(true)
        assertTrue(gate.policy.value.emergencyStopped)
        gate.resumeHuman()
        assertThrows(IllegalStateException::class.java){gate.requireAllowed(ManagedFeature.CLOUD,true)}
        gate.requireAllowed(ManagedFeature.INFERENCE,true)
    }
    @Test fun masterPersistenceFailureKeepsRevocationButCannotResume() {
        val gate=OperationGate(persist={error("disk full")})
        assertThrows(IllegalStateException::class.java){gate.setAgentsEnabled(false)}
        assertTrue(gate.policy.value.agentsPaused)
        assertThrows(IllegalStateException::class.java){gate.setAgentsEnabled(true)}
        assertTrue(gate.policy.value.agentsPaused)
    }
    @Test fun stopCancelsAndSurvivesRestartWithoutResume() = runBlocking {
        var saved = OperationPolicy(); val gate = OperationGate(persist = { saved = it }); val started = CompletableDeferred<Unit>()
        val child = launch { gate.run(ManagedFeature.CLUSTER) { started.complete(Unit); awaitCancellation() } }
        started.await(); gate.emergencyStop(); child.join(); assertTrue(child.isCancelled)
        assertTrue(runCatching { OperationGate(saved).requireAllowed(ManagedFeature.INFERENCE) }.isFailure)
        gate.resumeHuman(); gate.requireAllowed(ManagedFeature.CLUSTER)
    }
    @Test fun separateAgentRevocationAndDisabledFeatureSurviveResume() = runBlocking {
        val gate = OperationGate(); val started = CompletableDeferred<Unit>()
        val child = launch { gate.run(ManagedFeature.SSH, true) { started.complete(Unit); awaitCancellation() } }
        started.await(); gate.setFeature(ManagedFeature.SSH, false, true); child.join(); assertTrue(child.isCancelled)
        gate.requireAllowed(ManagedFeature.SSH); assertTrue(runCatching { gate.requireAllowed(ManagedFeature.SSH, true) }.isFailure)
        gate.setFeature(ManagedFeature.CYBER, false); gate.emergencyStop(); gate.resumeHuman()
        assertTrue(runCatching { gate.requireAllowed(ManagedFeature.CYBER) }.isFailure)
    }
    @Test fun persistenceFailureNeverAllowsResumeOrRevocationBypass() {
        val gate = OperationGate(OperationPolicy(emergencyStopped = true), persist = { error("disk failure") })
        assertTrue(runCatching { gate.resumeHuman() }.isFailure); assertTrue(gate.policy.value.emergencyStopped)
        val revocation = OperationGate(persist = { error("disk failure") }); runCatching { revocation.setFeature(ManagedFeature.CLOUD, false) }
        assertTrue(runCatching { revocation.requireAllowed(ManagedFeature.CLOUD) }.isFailure)
    }
    @Test fun fixedBuildBoundaryCannotBeEnlargedByPolicyResumeOrMutableSets() = runBlocking {
        val supplied=mutableSetOf(ManagedFeature.INFERENCE)
        val gate=OperationGate(OperationPolicy(),allowedFeatures=supplied)
        supplied.add(ManagedFeature.VM)
        runCatching { (gate.supportedFeatures as? MutableSet<ManagedFeature>)?.add(ManagedFeature.SSH) }
        gate.emergencyStop();gate.resumeHuman()
        for(feature in ManagedFeature.entries.filter{it!=ManagedFeature.INFERENCE}) {
            assertTrue(runCatching { gate.setFeature(feature,true) }.isFailure)
            assertTrue(runCatching { gate.setFeature(feature,true,true) }.isFailure)
            var invoked=false
            assertTrue(runCatching { gate.run(feature) { invoked=true } }.isFailure)
            assertTrue(runCatching { gate.run(feature,true) { invoked=true } }.isFailure)
            assertFalse(invoked)
        }
        assertEquals("local",gate.run(ManagedFeature.INFERENCE) { "local" })
    }
}
