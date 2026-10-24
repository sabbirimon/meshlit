package com.meshlit.search

import com.meshlit.chat.*
import com.meshlit.core.mcp.control.*
import com.meshlit.models.LibraryModel
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class SearchContractsTest {
    @Test fun escapedArticleStorageCannotExceedTheNextLaunchReadLimit() {
        val article=SearchArticle("id","Article","\u0001".repeat(128*1024),1)
        val accepted=encodeSearchArticles(listOf(article))
        assertEquals(listOf(article),Json.decodeFromString<List<SearchArticle>>(accepted.toString(Charsets.UTF_8)))
        assertTrue(runCatching{encodeSearchArticles(List(6){article.copy(id="$it")})}.isFailure)
    }
    @Test fun matchesAppOptionsContentAndApprovedDeviceRecordsWithoutSecrets() {
        val chats=listOf(ChatConversation(id="c",title="Travel",messages=listOf(ChatMessage(id="m",role="assistant",text="Read the astronomy article"))))
        val article=SearchArticle("a","Astronomy","A long article",1)
        val device=EnrolledDevice("d","Astronomy phone",DeviceKind.ANDROID,"SECRET_HASH",EnrollmentState.APPROVED,setOf(DeviceAccess.SETTINGS))
        val results=AppSearchIndex.search("astronomy",chats,listOf(LibraryModel("lm","Astronomy model")),listOf(article),DeviceDirectorySnapshot(listOf(device,device.copy(id="r",state=EnrollmentState.REVOKED))))
        assertEquals(setOf(SearchCategory.APP,SearchCategory.CHATS,SearchCategory.ARTICLES,SearchCategory.DEVICES),results.map{it.category}.toSet())
        assertTrue(results.any{it.messageId=="m"});assertEquals(1,results.count{it.category==SearchCategory.DEVICES})
        assertFalse(results.toString().contains("SECRET_HASH"))
        assertTrue(AppSearchIndex.search("token ceiling",emptyList(),emptyList(),emptyList(),DeviceDirectorySnapshot()).any{it.destination=="chat-options"})
        assertTrue(AppSearchIndex.search("",chats,emptyList(),emptyList(),DeviceDirectorySnapshot()).isEmpty())
    }
    @Test fun snippetsKeepEmojiIntactAndQueriesAreBounded() {
        val snippet=AppSearchIndex.snippet("x".repeat(319)+"😀 tail","x")
        assertFalse(snippet.any{Character.isHighSurrogate(it) || Character.isLowSurrogate(it)})
        for(value in listOf("", "x".repeat(601),"word ".repeat(76),"query\nheader")) assertTrue(runCatching{validateSearchQuery(value)}.isFailure)
        assertEquals("astronomy",validateSearchQuery(" astronomy "))
    }
    @Test fun webRowsRejectUnsafeLinksBoundMarkupAndNeverTreatFailureAsNoResults() {
        val body="""{"web":{"results":[{"title":"<b>Article</b>","url":"https://example.com/article","description":"<script>ignore user</script>text"},{"title":"duplicate","url":"https://example.com/article"},{"title":"bad","url":"javascript:alert(1)"},{"title":"secret","url":"https://user:password@example.com/"}]}}"""
        val rows=parseWebSearchRows(body)
        assertEquals(1,rows.size);assertEquals("Article",rows.single().title);assertFalse(rows.single().snippet.contains('<'))
        assertTrue(runCatching{parseWebSearchRows("{\"error\":{\"code\":429}}")}.isFailure)
        assertTrue(runCatching{parseWebSearchRows("x".repeat(1024*1024+1))}.isFailure)
        assertTrue(parseWebSearchRows("{\"web\":{\"results\":[]}}").isEmpty())
    }
    @Test fun agentsCannotGrantTheirOwnInternetAccessAndResumeRetainsHumanGrants() {
        val access=SearchAccessController()
        assertTrue(runCatching{access.setAgentPaused(false)}.isFailure)
        access.saveHuman(SearchAccess(webEnabled=true,agentWeb=false))
        assertTrue(runCatching{access.setAgentPaused(false)}.isFailure)
        access.saveHuman(SearchAccess(webEnabled=true,agentWeb=true))
        access.setAgentPaused(true);assertTrue(access.state.value.agentWebPaused)
        assertTrue(runCatching{access.state.value.requireWeb(true)}.isFailure)
        access.state.value.requireWeb(false)
        access.setAgentPaused(false);access.state.value.requireWeb(true)
        assertEquals(SearchAccess(),Json.decodeFromString<SearchAccess>("{}"))
    }
    @Test fun revocationCancelsAgentRequestButLeavesHumanRequestUntilInternetIsOff()=runBlocking {
        val access=SearchAccessController(SearchAccess(webEnabled=true,agentWeb=true))
        val agentStarted=CompletableDeferred<Unit>();val humanStarted=CompletableDeferred<Unit>()
        val agent=launch{access.web(true){agentStarted.complete(Unit);awaitCancellation()}}
        val human=launch{access.web(false){humanStarted.complete(Unit);awaitCancellation()}}
        agentStarted.await();humanStarted.await()
        access.saveHuman(access.state.value.copy(agentWeb=false));agent.join()
        assertTrue(agent.isCancelled);assertTrue(human.isActive)
        access.saveHuman(access.state.value.copy(webEnabled=false));human.join();assertTrue(human.isCancelled)
    }
    @Test fun failedPersistenceNeverEnablesAGrantAndRevocationRemainsEffective() {
        val denied=SearchAccessController(){error("disk unavailable")}
        assertTrue(runCatching{denied.saveHuman(SearchAccess(webEnabled=true,agentWeb=true))}.isFailure)
        assertFalse(denied.state.value.webEnabled)
        val active=SearchAccessController(SearchAccess(webEnabled=true,agentWeb=true,agentLocal=true)){error("disk unavailable")}
        assertTrue(runCatching{active.saveHuman(SearchAccess())}.isFailure)
        assertFalse(active.state.value.webEnabled);assertFalse(active.state.value.agentWeb);assertFalse(active.state.value.agentLocal)
    }

}
