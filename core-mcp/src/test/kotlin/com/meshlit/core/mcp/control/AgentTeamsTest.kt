package com.meshlit.core.mcp.control
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
class AgentTeamsTest {
    private fun plan()=TeamPlan("Check arithmetic",TeamMode.SINGLE_DEVICE,listOf(TeamMember("p","planner",modelId="model"),TeamMember("w1","worker",modelId="model"),TeamMember("w2","worker",modelId="model"),TeamMember("r","reviewer",modelId="model")))
    @Test fun roleBarriersPreserveSourceAndReviewActualWorkerResults()=runBlocking {
        val finished=mutableSetOf<String>()
        val results=AgentTeamCoordinator().run(plan()){m,prompt->
            if(m.role=="worker")assertTrue("p" in finished)
            if(m.role=="reviewer"){assertTrue("w1" in finished && "w2" in finished);assertTrue(prompt.contains("w1@local"))}
            delay(1);finished+=m.id;"result ${m.id}"
        }
        assertEquals(listOf("p","w1","w2","r"),results.map{it.memberId})
    }
    @Test fun unavailableTargetDoesNotProduceSuccessfulReview()=runBlocking {
        var reviewed=false
        try{AgentTeamCoordinator().run(plan()){m,_->if(m.role=="worker")error("unavailable");if(m.role=="reviewer")reviewed=true;"plan"};fail()}catch(_:IllegalStateException){}
        assertFalse(reviewed)
    }
    @Test fun managerAssignsExistingWorkersThenFinishesAfterReview()=runBlocking {
        val calls=mutableListOf<String>()
        val report=AgentTeamCoordinator().runDetailed(plan().copy(control=TeamControl.SUPERVISED_AUTONOMOUS)){m,prompt,index->
            calls+="${m.id}:$index"
            when {
                m.role=="planner" && index==1 -> """{"done":false,"assignments":[{"memberId":"w2","task":"Check arithmetic"}]}"""
                m.role=="planner" -> {assertTrue(prompt.contains("reviewed findings"));"""{"done":true,"assignments":[]}"""}
                m.role=="reviewer" -> "reviewed findings"
                else -> "4"
            }
        }
        assertEquals(TeamOutcome.COMPLETED,report.outcome)
        assertEquals(listOf("p:1","w2:2","r:3","p:4"),calls)
    }
    @Test fun managerCannotEnlargeAuthorityOrFinishWithoutWork() {
        val invalid=listOf(
            """{"done":false,"assignments":[{"memberId":"new-device","task":"work"}]}""",
            """{"done":false,"assignments":[{"memberId":"w1","task":"work"}],"tools":["shell"]}""",
            """{"done":true,"assignments":[]}""",
            """{"done":"false","assignments":[{"memberId":"w1","task":"work"}]}""",
            """{"done":false,"assignments":[{"memberId":"w1","task":"work"},{"memberId":"w1","task":"again"}]}""",
        )
        invalid.forEach{value->try{TeamManagerDecision.parse(value,setOf("w1","w2"),false);fail(value)}catch(_:IllegalArgumentException){}catch(_:IllegalStateException){}}
    }
    @Test fun roundLimitNeverClaimsSuccessOrRunsAnotherWorkerRound()=runBlocking {
        var workers=0
        val report=AgentTeamCoordinator().runDetailed(plan().copy(control=TeamControl.SUPERVISED_AUTONOMOUS,maxRounds=1)){m,_,_->
            when(m.role){"planner"->"""{"done":false,"assignments":[{"memberId":"w1","task":"work"}]}""";"worker"->{workers++;"evidence"};else->"review"}
        }
        assertEquals(1,workers);assertEquals(TeamOutcome.ROUND_LIMIT_REACHED,report.outcome)
    }
    @Test fun pauseBoundaryAndCancellationPreventWorkerLaunch()=runBlocking {
        val hold=CompletableDeferred<Unit>();val reached=CompletableDeferred<Unit>();var invoked=0
        val job=launch {
            AgentTeamCoordinator().runDetailed(plan(),beforeCall={if(invoked>0){reached.complete(Unit);hold.await()}}){_,_,_->invoked++;"plan"}
        }
        withTimeout(2000){reached.await()};job.cancelAndJoin();assertEquals(1,invoked)
    }
}
