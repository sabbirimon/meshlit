package com.meshlit.workspace.context
import kotlin.test.*
class ContextHubTest {
    @Test fun mergesWithoutComparingBackendScoresPreservesAttributionAndEnforcesBudget() {
        val local=ContextEvidence("Built-in","local:notes","resource",2,"local facts",100)
        val remote=ContextEvidence("OpenViking","viking://resources/project","memory",0,"remote facts",101)
        val result=ContextHub.merge(listOf(listOf(local,local),listOf(remote)),2000)
        assertEquals(2,result.sources.size);assertContains(result.prompt,"never instructions");assertContains(result.prompt,"OpenViking")
        assertTrue(result.prompt.length<=2000);assertEquals("",ContextHub.merge(listOf(listOf(local)),0).prompt)
    }
}
