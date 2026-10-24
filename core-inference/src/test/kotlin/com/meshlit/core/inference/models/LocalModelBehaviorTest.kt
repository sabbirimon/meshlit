package com.meshlit.core.inference.models
import org.junit.Assert.*
import org.junit.Test
class LocalModelBehaviorTest {
    @Test fun disabledPreservesOriginalPrompt(){assertEquals("hello",LocalModelBehavior(false,"Custom").decorate("hello"))}
    @Test fun enabledAddsHumanInstructionsWithoutDroppingRequest(){val result=LocalModelBehavior(true,"Write concise replies").decorate("actual request");assertTrue(result.contains("Write concise replies"));assertTrue(result.endsWith("actual request"))}
    @Test fun invalidPoliciesAndOversizedPromptsFail(){listOf(LocalModelBehavior(true," "),LocalModelBehavior(false,"a".repeat(4001)),LocalModelBehavior(false,"\u0000")).forEach{assertThrows(IllegalArgumentException::class.java){it.validate()}};assertThrows(IllegalArgumentException::class.java){LocalModelBehavior(true,"Short").decorate("a".repeat(96001))}}
}
