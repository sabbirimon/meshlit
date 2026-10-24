package com.meshlit.desktop

import kotlin.test.*
import org.junit.Test

class ModelRecommendationsTest {
    @Test fun currentPressureDoesNotInventHardwareSupport() {
        val gib=1024L*1024*1024
        assertTrue(desktopModelAdvice(32*gib,16*gib,20*gib,false,false).none{it.fits})
        assertTrue(desktopModelAdvice(32*gib,256*1024*1024,20*gib,true,false).none{it.ready})
        assertTrue(desktopModelAdvice(32*gib,16*gib,0,true,false).none{it.ready})
        val roomy=desktopModelAdvice(32*gib,16*gib,20*gib,true,false)
        assertEquals("qwen3-capable",roomy.first().model.id)
        assertEquals("qwen3-small",desktopModelAdvice(32*gib,16*gib,20*gib,true,true).first().model.id)
    }
}
