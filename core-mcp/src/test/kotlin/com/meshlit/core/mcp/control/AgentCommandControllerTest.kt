package com.meshlit.core.mcp.control
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
@OptIn(ExperimentalCoroutinesApi::class)
class AgentCommandControllerTest {
    private class Memory(var rows:List<AgentJob> = emptyList()):AgentJobStore{
        override suspend fun load()=rows
        override suspend fun save(jobs:List<AgentJob>){rows=jobs.toList()}
    }
    @Test fun cloudCommandsAreBoundedReferencesAndCannotCarrySecrets(){
        AgentCommand("cloud",AgentOperation.CLOUD_EXECUTE,cloudProfileId="prod",cloudAction="instances",cloudPage=1).validate()
        assertThrows(IllegalArgumentException::class.java){AgentCommand("bad",AgentOperation.CLOUD_EXECUTE,cloudProfileId="prod",cloudAction="instances",cloudPage=101).validate()}
        assertThrows(IllegalArgumentException::class.java){AgentCommand("bad",AgentOperation.CLOUD_EXECUTE,cloudProfileId="prod",cloudAction="https://other.example").validate()}
        assertTrue(runCatching{Json.decodeFromString<AgentCommand>("""{"requestId":"leak","operation":"CLOUD_EXECUTE","cloudProfileId":"prod","cloudAction":"instances","token":"secret"}""")}.isFailure)
        val schema=AgentCommandSchema.describe()["properties"]!!.jsonObject
        assertTrue("cloudAction" in schema && "cloudProfileId" in schema);assertFalse("token" in schema)
    }
    @Test fun durableAdmissionAndDuplicateIdExecuteOnce()=runTest{
        val store=Memory();var calls=0
        val controller=AgentCommandController(store,backgroundScope){calls++;buildJsonObject{put("ok",true)}}
        controller.ready.await()
        val command=AgentCommand("one",AgentOperation.MODELS_LIST)
        assertEquals(AgentJobPhase.QUEUED,controller.submit(command).phase)
        assertEquals(1,store.rows.size)
        controller.submit(command)
        runCurrent()
        assertEquals(1,calls);assertEquals(AgentJobPhase.SUCCEEDED,controller.jobs.value.single().phase)
        assertThrows(IllegalArgumentException::class.java){runBlocking{controller.submit(command.copy(operation=AgentOperation.SETTINGS_READ))}}
    }
    @Test fun processRestartMarksUncertainAndRequiresNewId()=runTest{
        val interrupted=AgentJob(AgentCommand("old",AgentOperation.MODEL_LOAD,modelId="installed"),AgentJobPhase.RUNNING)
        val store=Memory(listOf(interrupted));var calls=0
        val controller=AgentCommandController(store,backgroundScope){calls++;JsonObject(emptyMap())}
        controller.ready.await()
        assertEquals(AgentJobPhase.INTERRUPTED,controller.jobs.value.single().phase);assertEquals(0,calls)
        assertThrows(IllegalArgumentException::class.java){runBlocking{controller.retry("old","old")}}
        controller.retry("old","new");runCurrent();assertEquals(1,calls)
    }
    @Test fun cancellationIsObservableAndFailureHasTypedCode()=runTest{
        val started=CompletableDeferred<Unit>()
        val controller=AgentCommandController(Memory(),backgroundScope){command ->
            if(command.requestId=="deny") throw AgentCommandFailure("permission_denied","No delegation")
            started.complete(Unit);awaitCancellation()
        }
        controller.submit(AgentCommand("waiting",AgentOperation.MODELS_LIST));runCurrent();started.await()
        assertEquals(AgentJobPhase.CANCELLED,controller.cancel("waiting")!!.phase)
        controller.submit(AgentCommand("deny",AgentOperation.MODELS_LIST));runCurrent()
        assertEquals("permission_denied",controller.jobs.value.last().errorCode)
    }
    @Test fun admissionStoreFailurePreventsExecution()=runTest{
        var calls=0;var saves=0
        val store=object:AgentJobStore{
            override suspend fun load()=emptyList<AgentJob>()
            override suspend fun save(jobs:List<AgentJob>){if(++saves>1) error("disk unavailable")}
        }
        val controller=AgentCommandController(store,backgroundScope){calls++;JsonNull}
        controller.ready.await()
        try{controller.submit(AgentCommand("not-admitted",AgentOperation.MODELS_LIST));fail("Expected persistence failure")}catch(_:IllegalStateException){}
        runCurrent();assertEquals(0,calls);assertTrue(controller.jobs.value.isEmpty())
    }
}
