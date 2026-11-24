package com.meshlit.core.inference
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
class CpuWorkGateTest {
    @Test fun speechAndTextShareOneLaneAndCancelledWaitersLeaveNoPhantomQueue()=runBlocking {
        val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>();var secondRan=false
        val first=launch{CpuWorkGate.run("Speech"){entered.complete(Unit);release.await()}}
        entered.await();val second=launch{CpuWorkGate.run("Text"){secondRan=true}}
        withTimeout(2000){while(CpuWorkGate.queued!=1)yield()}
        assertEquals("Speech",CpuWorkGate.active);assertFalse(secondRan)
        second.cancelAndJoin();assertEquals(0,CpuWorkGate.queued)
        release.complete(Unit);first.join();assertNull(CpuWorkGate.active)
        try{CpuWorkGate.run("Failure"){error("expected")};fail()}catch(_:IllegalStateException){}
        assertNull(CpuWorkGate.active);assertEquals(0,CpuWorkGate.queued)
    }
}
