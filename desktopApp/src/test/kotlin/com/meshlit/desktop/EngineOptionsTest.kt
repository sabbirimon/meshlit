package com.meshlit.desktop

import com.meshlit.core.inference.models.GgufModelMetadata
import org.junit.Test
import kotlin.test.*

class EngineOptionsTest {
    private val metadata=GgufModelMetadata("qwen2","Q4_K_M",32768,28,2,128,128)
    @Test fun modelContextAndKvNeedMemoryNotJustAvailableWeightFile() {
        val options=EngineOptions(threads=1,batchThreads=1)
        val plan=options.admission(DesktopStarter.size,metadata,8L*1024*1024*1024)
        assertTrue(plan.estimatedBytes>DesktopStarter.size);assertTrue(plan.kvBytes>0)
        assertFailsWith<IllegalArgumentException> {options.admission(DesktopStarter.size,metadata,1024L*1024*1024)}
        assertFailsWith<IllegalArgumentException> {options.admission(DesktopStarter.size,metadata.copy(maxContext=1024),8L*1024*1024*1024)}
        assertFailsWith<IllegalArgumentException> {options.admission(DesktopStarter.size,metadata.copy(architecture="unknown"),8L*1024*1024*1024)}
    }
    @Test fun invalidThreadsBatchesAndCacheCannotReachNativeProcess() {
        assertFailsWith<IllegalArgumentException> {EngineOptions(threads=0).validate(8)}
        assertFailsWith<IllegalArgumentException> {EngineOptions(threads=16).validate(8)}
        assertFailsWith<IllegalArgumentException> {EngineOptions(batch=128,microBatch=512).validate(8)}
        assertFailsWith<IllegalArgumentException> {EngineOptions(keyCache="bad").validate(8)}
    }
    @Test fun coalescingPreservesAllTextAndBoundsMemory() {
        var now=0L;val buffer=ReplyBuffer {now}
        assertEquals("hello",buffer.append("hello"));assertNull(buffer.append(" 🌍"))
        now+=34_000_000;assertEquals("hello 🌍 again",buffer.append(" again"))
        repeat(1000) {buffer.append("x")}
        assertEquals("hello 🌍 again"+"x".repeat(1000),buffer.snapshot())
        assertFailsWith<IllegalArgumentException> {buffer.append("x".repeat(131072))}
    }
    @Test fun longRepliesLimitExpensiveRedrawsWithoutLosingText() {
        var now = 0L
        val buffer = ReplyBuffer { now }
        assertEquals("a", buffer.append("a"))
        now += 40_000_000L
        assertNull(buffer.append("b".repeat(8_192)))
        now += 61_000_000L
        assertEquals("a" + "b".repeat(8_192) + "c", buffer.append("c"))
        now += 120_000_000L
        assertNull(buffer.append("d".repeat(24_576)))
        now += 81_000_000L
        val published = buffer.append("e")
        assertEquals(buffer.snapshot(), published)
    }
}
