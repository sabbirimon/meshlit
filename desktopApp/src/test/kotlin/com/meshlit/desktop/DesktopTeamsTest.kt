package com.meshlit.desktop

import com.meshlit.core.mcp.control.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import kotlin.test.*

class DesktopTeamsTest {
    @Test fun dialogueMessagesArePersistedWithAttributionAndWithoutReplay()=runBlocking {
        var disk:String?=null;var leadCalls=0
        val teams=DesktopTeams({disk},{disk=it}){member,_,_->
            if(member.id=="p" && leadCalls++==0) """{"action":"delegate","memberId":"w","task":"Check the result"}"""
            else """{"action":"reply","text":"Verified result"}"""
        }
        val id=teams.start(plan().copy(control=TeamControl.AGENT_DIALOGUE))
        val run=terminal(teams,id)
        assertEquals("SUCCEEDED",run.getValue("phase").jsonPrimitive.content)
        val messages=run.getValue("messages").jsonArray
        assertEquals(4,messages.size)
        assertEquals("p",messages[1].jsonObject.getValue("from").jsonPrimitive.content)
        assertEquals("w",messages[1].jsonObject.getValue("to").jsonPrimitive.content)
        teams.close()
        val reopened=DesktopTeams({disk},{disk=it}){_,_,_->error("Completed dialogues must not replay")}
        try {assertEquals(messages,reopened.status().getValue("runs").jsonArray.single().jsonObject.getValue("messages"))}
        finally {reopened.close()}
    }
    private fun plan(goal:String="Check arithmetic")=TeamPlan(goal,TeamMode.SINGLE_DEVICE,listOf(
        TeamMember("p","planner",modelId="local"),TeamMember("w","worker",modelId="local"),TeamMember("r","reviewer",modelId="local")))
    private suspend fun terminal(teams:DesktopTeams,id:String)=withTimeout(3000){
        var result:JsonObject
        do{delay(5);result=teams.status().getValue("runs").jsonArray.single{it.jsonObject.getValue("id").jsonPrimitive.content==id}.jsonObject}
        while(result.getValue("phase").jsonPrimitive.content in setOf("QUEUED","RUNNING","PAUSING","PAUSED"))
        result
    }
    @Test fun agentDelegationIsExplicitIdempotentAndDoesNotExportCredentials()=runBlocking {
        var disk:String?=null;var calls=0
        val teams=DesktopTeams({disk},{disk=it}){_,_,_->calls++;"findings"}
        try {
            assertFails{teams.startDelegated("task","request")}
            teams.delegate(plan())
            val id=teams.startDelegated("task","request")
            assertEquals(id,teams.startDelegated("task","request"))
            assertFails{teams.startDelegated("different task","request")}
            assertEquals("SUCCEEDED",terminal(teams,id).getValue("phase").jsonPrimitive.content)
            assertEquals(3,calls)
            assertFalse(teams.status().containsKey("delegatedRoutes"))
            teams.delegate(null);assertFails{teams.startDelegated("task","new")}
        }finally{teams.close()}
        Unit
    }
    @Test fun revokeStopsActiveDelegatedWorkAndRestartNeverReplays()=runBlocking {
        var disk:String?=null;val entered=CompletableDeferred<Unit>();var calls=0
        val teams=DesktopTeams({disk},{disk=it}){_,_,_->calls++;entered.complete(Unit);awaitCancellation()}
        teams.delegate(plan())
        val id=teams.startDelegated("task","request")
        withTimeout(3000){entered.await()};teams.delegate(null)
        assertEquals("CANCELLED",terminal(teams,id).getValue("phase").jsonPrimitive.content)
        val saved=disk
        teams.close()
        val reopened=DesktopTeams({saved},{disk=it}){_,_,_->calls++;"should not run"}
        try{reopened.status();delay(20);assertEquals(1,calls)}finally{reopened.close()}
    }
    @Test fun interruptedRunIsVisibleAndPersistenceFailureDoesNotLaunch()=runBlocking {
        var disk:String?=null;var fail=false;val entered=CompletableDeferred<Unit>();var calls=0
        val teams=DesktopTeams({disk},{if(fail)error("disk full") else disk=it}){_,_,_->calls++;entered.complete(Unit);awaitCancellation()}
        teams.status();fail=true;assertFails{teams.start(plan())};assertEquals(0,calls);fail=false
        val id=teams.start(plan());withTimeout(3000){entered.await()};val interrupted=disk
        teams.close()
        val reopened=DesktopTeams({interrupted},{disk=it}){_,_,_->error("must not replay")}
        try{assertEquals("INTERRUPTED",reopened.status().getValue("runs").jsonArray.single{it.jsonObject.getValue("id").jsonPrimitive.content==id}.jsonObject.getValue("phase").jsonPrimitive.content)}finally{reopened.close()}
    }
}
