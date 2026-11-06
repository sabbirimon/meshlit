package com.meshlit.recovery

import com.meshlit.chat.ChatConversation
import com.meshlit.chat.ChatMessage
import com.meshlit.chat.chatTokenUsage
import com.meshlit.core.federation.recovery.*
import org.junit.Assert.*
import org.junit.Test

class ReplicatedChatTest {
    private fun user(text:String)=ChatMessage(role="user",text=text)
    private fun assistant(text:String,finish:String="natural",source:String="Runtime usage")=ChatMessage(role="assistant",text=text,
        usage=chatTokenUsage(source,32,null,null,null,10,null,256,finish))

    @Test fun checkpointContainsOnlyCompletedPlainTurnsAndNoDraftOrInterruptedText() {
        val chat=ChatConversation(draft="private unsent draft",messages=listOf(user("first"),assistant("answer"),
            user("cancelled question"),assistant("partial",finish="cancelled"),user("unfinished"),ChatMessage(role="assistant",text="streamed")))
        val transcript=replicaChatTranscript(chat)
        assertEquals(listOf("first","answer"),transcript.messages.map{it.text})
        assertFalse(transcript.encode().contains("private unsent"))
        assertThrows(IllegalArgumentException::class.java){replicaChatTranscript(chat.copy(messages=listOf(user("a"),assistant("tool result",source="Tool loop · counts not aggregated"))))}
        assertThrows(IllegalArgumentException::class.java){replicaChatTranscript(chat.copy(messages=listOf(user("photo").copy(attachmentNote="attached"),assistant("reply"))))}
    }

    @Test fun checkpointKeepsWholeRecentPairsWithinUtf8Budget() {
        val chat=ChatConversation(messages=(1..6).flatMap{n->listOf(user("question $n"),assistant("answer $n"))})
        assertEquals((3..6).map{"question $it"},replicaChatTranscript(chat).messages.filter{it.role=="user"}.map{it.text})
        val oversized=chat.copy(messages=listOf(user("বাংলা".repeat(1000)),assistant("reply")))
        assertThrows(IllegalArgumentException::class.java){replicaChatTranscript(oversized)}
    }

    @Test fun recoveryReadsLatestTranscriptButReportsCurrentOwnerAndRejectsWrongDigest() {
        val task="chat-00000000-0000-0000-0000-000000000001"
        val old=replicaChatTranscript(ChatConversation(messages=listOf(user("one"),assistant("first"))))
        val latest=replicaChatTranscript(ChatConversation(messages=listOf(user("two"),assistant("second"))))
        fun commit(slot:Int,entry:JournalEntry)=JournalCommit(JournalValue(slot,"0".repeat(64),entry),Ballot(1,"test"),emptyList())
        fun snapshot(id:String,sequence:Long,transcript:ReplicaChatTranscript)=JournalEntry(id,task,"old",1,JournalAction.SNAPSHOT,
            sequence,"a".repeat(64),recoveryDigest(transcript.encode()),transcript.encode(),JournalContent.CHAT_TRANSCRIPT)
        val history=listOf(commit(0,snapshot("s1",1,old)),commit(1,snapshot("s2",2,latest)),
            commit(2,JournalEntry("takeover",task,"new",2,JournalAction.ACQUIRE)))
        val recovered=chatRecoveryCheckpoints(history).single()
        assertEquals("s2",recovered.requestId);assertEquals("new",recovered.owner);assertEquals(latest,recovered.transcript)
        assertThrows(IllegalArgumentException::class.java){chatRecoveryCheckpoints(listOf(commit(1,snapshot("s2",2,latest).copy(checkpointSha256="b".repeat(64)))))}
    }
}
