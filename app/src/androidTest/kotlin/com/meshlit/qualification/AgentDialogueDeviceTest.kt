package com.meshlit.qualification

import androidx.test.platform.app.InstrumentationRegistry
import com.meshlit.core.inference.InferenceCoordinator
import com.meshlit.core.mcp.control.*
import com.meshlit.legal.LegalAgreementStore
import com.meshlit.models.ModelLibrary
import com.meshlit.teams.AgentTeams
import com.meshlit.teams.TeamSetup
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.koin.core.context.GlobalContext
import java.io.File

/** Explicit live qualification; uses real model calls and existing grants/approved targets.
 * Run before final Accessibility enable: Samsung force-stop/instrumentation can revoke it. */
class AgentDialogueDeviceTest {
    @Test fun realModelsDelegateAndReturnCorrelatedReplies()=runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val args=InstrumentationRegistry.getArguments()
        assumeTrue("Explicit operator-run model test",args.getString("qualifyAgentDialogue")=="true")
        val context=instrumentation.targetContext
        assertTrue("Legal notices must already be accepted by the owner",LegalAgreementStore(context).accepted())
        val library=GlobalContext.get().get<ModelLibrary>()
        val inference=GlobalContext.get().get<InferenceCoordinator>()
        library.ready.await()
        val modelId=args.getString("modelId") ?: "url-824902c942c3cc73"
        assertTrue("A real installed model is required",library.models.value.any{it.id==modelId && it.installed})
        library.load(modelId)
        assertNotNull(inference.loadedModel())
        val teams=AgentTeams.get(context)
        val target=args.getString("approvedTargetId")?.takeIf{it.isNotBlank()}
        if(target!=null)assertTrue("The target must already be owner-approved",target in teams.approvedTargetIds())
        val goal="Qualification: calculate 2+2 using a handoff. Lead agent-1 must first delegate the arithmetic to agent-2. Agent-2 must reply with the answer. Lead must then reply with the received answer. Return only the reply/delegate JSON protocol."
        val setup=TeamSetup(control=TeamControl.AGENT_DIALOGUE,maxRounds=1,
            mode=if(target==null)TeamMode.SINGLE_DEVICE else TeamMode.MULTI_DEVICE,targetIds=target?.let{setOf(it)} ?: emptySet())
        val id=teams.start(goal,setup)
        try {
            val run=withTimeout(360000){teams.runs.first{runs->runs.any{it.id==id && it.phase !in setOf("QUEUED","RUNNING","PAUSED","PAUSING")}}.single{it.id==id}}
            File(context.filesDir,"qualification/agent-dialogue-device.json").apply{parentFile!!.mkdirs();writeText(Json.encodeToString(run))}
            assertEquals("Real-model dialogue did not complete: ${run.error}","SUCCEEDED",run.phase)
            val task=run.messages.firstOrNull{it.kind=="TASK" && it.from=="agent-1" && it.to=="agent-2"}
            assertNotNull("Valid JSON alone is not delegation: an actual task must be routed",task)
            assertTrue("The receiver must return the correlated reply",run.messages.any{it.kind=="REPLY" && it.from=="agent-2" && it.to=="agent-1" && it.replyTo==task!!.id})
            val final=run.messages.lastOrNull{it.kind=="REPLY" && it.from=="agent-1" && it.to=="human"}
            assertNotNull("The lead must return an actual final reply",final)
            assertTrue("Real arithmetic answer missing",Regex("\\b(4|four)\\b",RegexOption.IGNORE_CASE).containsMatchIn(final!!.body))
            if(target!=null)assertTrue("Cross-device proof needs an executed remote member",run.outputs.any{it.sourceId==target})
        } finally {teams.stop(id)}
    }
}
