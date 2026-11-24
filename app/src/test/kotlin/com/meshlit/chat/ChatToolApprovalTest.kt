package com.meshlit.chat

import com.meshlit.core.mcp.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ChatToolApprovalTest {
    @Test fun readClassificationCannotTrustAnImportedToolOrAClaimedReadOnlyMutation() {
        val call=McpToolRequest("android_control",buildJsonObject{put("action","click");put("readOnly",true)})
        assertTrue(needsChatToolApproval(ChatApprovalMode.AUTO_READS,call,McpToolSpec.Origin.BuiltIn))
        assertFalse(needsChatToolApproval(ChatApprovalMode.AUTO_READS,McpToolRequest("web_search"),McpToolSpec.Origin.BuiltIn))
        assertTrue(needsChatToolApproval(ChatApprovalMode.SELECTED_TOOLS,McpToolRequest("web_search"),McpToolSpec.Origin.UserAdded))
    }
    @Test fun exactPendingDecisionExpiresAndCannotAuthorizeANewerCall()=runBlocking {
        var now=0L;val gate=ChatToolApproval(1000){now}
        val first=async(start=CoroutineStart.UNDISPATCHED){gate.review(ChatApprovalMode.ASK,McpToolRequest("test"),McpToolSpec.Origin.BuiltIn)}
        val id=gate.pending.value!!.id
        assertFalse(gate.answer("foreign",true));assertFalse(first.isCompleted)
        now=1001;assertTrue(gate.answer(id,true));assertFalse(first.await());assertNull(gate.pending.value)
        val next=async(start=CoroutineStart.UNDISPATCHED){gate.review(ChatApprovalMode.ASK,McpToolRequest("next"),McpToolSpec.Origin.BuiltIn)}
        assertFalse(gate.answer(id,true));assertTrue(gate.answer(gate.pending.value!!.id,true));assertTrue(next.await())
    }
    @Test fun stopClearsReviewAndNoAnswerIsNeverApproval()=runBlocking {
        val gate=ChatToolApproval(1000)
        val waiting=launch(start=CoroutineStart.UNDISPATCHED){gate.review(ChatApprovalMode.ASK,McpToolRequest("test"),McpToolSpec.Origin.BuiltIn)}
        val id=gate.pending.value!!.id;waiting.cancelAndJoin()
        assertNull(gate.pending.value);assertFalse(gate.answer(id,true))
        assertFalse(ChatToolApproval(25).review(ChatApprovalMode.ASK,McpToolRequest("test"),McpToolSpec.Origin.BuiltIn))
    }
}
