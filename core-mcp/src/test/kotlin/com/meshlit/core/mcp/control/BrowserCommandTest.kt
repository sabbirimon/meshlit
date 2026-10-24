package com.meshlit.core.mcp.control
import org.junit.Assert.*
import org.junit.Test
class BrowserCommandTest {
    @Test fun boundedTaskAndSteps(){AgentCommand("browser",AgentOperation.BROWSER_AUTONOMOUS_RUN,prompt="Read the page",browserMaxSteps=10).validate()
        for(step in listOf(0,21))assertThrows(IllegalArgumentException::class.java){AgentCommand("browser",AgentOperation.BROWSER_AUTONOMOUS_RUN,prompt="Read",browserMaxSteps=step).validate()}
        for(task in listOf("", "x".repeat(4001)))assertThrows(IllegalArgumentException::class.java){AgentCommand("browser",AgentOperation.BROWSER_AUTONOMOUS_RUN,prompt=task).validate()}
    }
}
