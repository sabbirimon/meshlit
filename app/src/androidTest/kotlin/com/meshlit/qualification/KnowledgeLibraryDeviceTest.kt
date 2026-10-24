package com.meshlit.qualification

import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.meshlit.core.mcp.McpToolResult
import com.meshlit.knowledge.KnowledgeLibrary
import com.meshlit.workspace.KnowledgePolicy
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Real Android URI import and encrypted persistence; isolated from the owner's corpus and grants. */
@RunWith(AndroidJUnit4::class)
class KnowledgeLibraryDeviceTest {
    @Test fun uriImportSurvivesReopenAndAgentGrantsAreIndependent()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val id=UUID.randomUUID().toString();val prefs="qualification-knowledge-$id"
        val file=File(context.filesDir,"generated-media/knowledge-$id.txt").apply{parentFile!!.mkdirs();writeText("The cobalt telescope needs a seven volt battery.")}
        try {
            val library=KnowledgeLibrary(context,prefs)
            val imported=library.import(FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",file))
            val reopened=KnowledgeLibrary(context,prefs)
            assertEquals(imported.id,reopened.corpus.snapshot().documents.single().id)
            assertEquals("",reopened.corpus.context("cobalt telescope",false))
            reopened.corpus.setPolicy(KnowledgePolicy(localChat=true))
            assertTrue(reopened.corpus.context("cobalt telescope",false).contains("[K1]"))
            assertEquals("",reopened.corpus.context("cobalt telescope",true))
            val tool=reopened.specs().single();val query=buildJsonObject{put("query","cobalt telescope")}
            assertTrue(tool.handler(query) is McpToolResult.Error)
            reopened.corpus.setPolicy(KnowledgePolicy(localChat=true,remoteChat=true,agents=true))
            val result=tool.handler(query)
            assertTrue(result is McpToolResult.Json)
            assertTrue((result as McpToolResult.Json).value.toString().contains("seven volt"))
            reopened.corpus.setPolicy(KnowledgePolicy())
            assertTrue(tool.handler(query) is McpToolResult.Error)
            reopened.corpus.delete(imported.id)
            assertTrue(KnowledgeLibrary(context,prefs).corpus.snapshot().documents.isEmpty())
            println("MESHLIT_KNOWLEDGE_DEVICE uri_import=true encrypted_reopen=true local_remote_agent_grants=true removal=true model_answer_tested=false")
        } finally { file.delete();context.deleteSharedPreferences(prefs) }
    }
}
