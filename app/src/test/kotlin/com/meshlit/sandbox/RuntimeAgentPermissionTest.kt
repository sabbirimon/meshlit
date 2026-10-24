package com.meshlit.sandbox
import android.app.Application
import com.meshlit.core.common.control.ManagedFeature
import com.meshlit.core.sandbox.VmState
import com.meshlit.operations.OperationsControl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class, sdk=[34], manifest=Config.NONE)
class RuntimeAgentPermissionTest {
    @Test fun optInAndGlobalAgentGateAreIndependentAndNeverStartMissingRuntime()=runBlocking {
        val context=RuntimeEnvironment.getApplication()
        val gate=OperationsControl.get(context).gate
        gate.resumeHuman();gate.setFeature(ManagedFeature.VM,true);gate.setFeature(ManagedFeature.VM,true,true)
        val host=RuntimeHost(context);host.setAllowAgentVm(false)
        assertFalse(host.allowAgentVm())
        assertTrue(runCatching { host.startVm(true) }.isFailure)
        assertTrue(runCatching { host.waitForGuest(true) }.isFailure)
        assertTrue(runCatching { host.executeGuest(listOf("/bin/id"),agentRequested=true) }.isFailure)
        assertEquals(VmState.STOPPED,host.vm.state)
        host.setAllowAgentVm(true);assertTrue(RuntimeHost(context).allowAgentVm())
        try {
            gate.setFeature(ManagedFeature.VM,false,true)
            assertTrue(runCatching { host.startVm(true) }.isFailure)
            assertTrue(runCatching { host.executeGuest(listOf("/bin/id"),agentRequested=true) }.isFailure)
            assertEquals(VmState.STOPPED,host.vm.state)
        } finally { host.setAllowAgentVm(false);gate.setFeature(ManagedFeature.VM,true,true) }
        assertFalse(RuntimeHost(context).allowAgentVm())
    }
}
