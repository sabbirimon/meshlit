package com.meshlit.core.mcp.control
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
class CheckpointCommandTest {
    @Test fun restoreAndDeleteRequireBoundedCheckpointIdentity(){
        for(op in listOf(AgentOperation.CHECKPOINT_RESTORE,AgentOperation.CHECKPOINT_DELETE)){
            assertThrows(IllegalArgumentException::class.java){AgentCommand("test",op).validate()}
            assertThrows(IllegalArgumentException::class.java){AgentCommand("test",op,checkpointId="../../blob").validate()}
            AgentCommand("test",op,checkpointId="12345678-1234-1234-1234-123456789abc").validate()
        }
    }
    @Test fun schemaAdvertisesAllFourRecoveryOperations(){
        val props=AgentCommandSchema.describe()["properties"]!!.jsonObject
        assertTrue("checkpointId" in props)
        val operations=props["operation"]!!.jsonObject["enum"]!!.jsonArray.map{it.jsonPrimitive.content}
        assertTrue(listOf("CHECKPOINT_LIST","CHECKPOINT_SAVE","CHECKPOINT_RESTORE","CHECKPOINT_DELETE").all{it in operations})
    }
}
