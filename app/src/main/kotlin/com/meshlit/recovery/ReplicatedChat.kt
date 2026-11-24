package com.meshlit.recovery

import com.meshlit.chat.ChatConversation
import com.meshlit.chat.ChatMessage
import com.meshlit.core.federation.recovery.JournalCommit
import com.meshlit.core.federation.recovery.JournalContent
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

internal const val RECOVERED_REPLY_SOURCE = "Recovered conversation text"
private const val MAX_TRANSCRIPT_BYTES = 8192
private const val MAX_TURNS = 4
private val SHA = Regex("[a-f0-9]{64}")
private val CHAT_TASK = Regex("chat-[a-f0-9-]{36}")

@Serializable internal data class ReplicaChatLine(val role:String,val text:String)
@Serializable internal data class ReplicaChatTranscript(val messages:List<ReplicaChatLine>,val version:Int=1) {
    fun encode():String {
        require(version==1 && messages.size in 2..MAX_TURNS*2 && messages.size%2==0)
        require(messages.withIndex().all{(i,line)->line.role==(if(i%2==0)"user" else "assistant") && line.text.isNotBlank()})
        return Json.encodeToString(this).also{require(it.toByteArray(Charsets.UTF_8).size<=MAX_TRANSCRIPT_BYTES){"The transcript exceeds the 8 KiB replica limit"}}
    }
}
@Serializable data class ReplicaChatBinding(val taskId:String,val epoch:Long,val checkpointId:String)
internal data class ReplicaChatDraft(val taskId:String,val epoch:Long,val modelSha256:String,val transcript:ReplicaChatTranscript)
internal data class ReplicaChatCheckpoint(val taskId:String,val requestId:String,val owner:String,val sequence:Long,val modelSha256:String,val transcript:ReplicaChatTranscript)

internal fun recoveryDigest(value:String):String=MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
internal fun requireChatRecoveryTask(taskId:String) { require(taskId.matches(CHAT_TASK)){"Invalid conversation recovery reference"} }

/** Only actual completed local text pairs, never a streamed partial, tool result, draft, or attachment. */
internal fun replicaChatTranscript(conversation:ChatConversation):ReplicaChatTranscript {
    fun eligible(reply:ChatMessage)=reply.role=="assistant" && !reply.isolatedPhoto && reply.text.isNotBlank() && reply.status==null &&
        ((reply.usage?.source=="Runtime usage" && reply.usage.finishReason in setOf("natural","max_tokens","stop_sequence")) || reply.source==RECOVERED_REPLY_SOURCE)
    val messages=conversation.messages
    // Omit trailing incomplete/failed turns. Keep only adjacent eligible pairs.
    var end=messages.indexOfLast(::eligible)
    var lines=emptyList<ReplicaChatLine>()
    while(lines.size<MAX_TURNS*2){
        val answer=messages.getOrNull(end)
        val question=messages.getOrNull(end-1)
        if(answer==null || !eligible(answer) || question?.role!="user" || question.attachmentNote!=null || question.isolatedPhoto || question.text.isBlank())break
        val next=listOf(ReplicaChatLine("user",question.text),ReplicaChatLine("assistant",answer.text))+lines
        val candidate=ReplicaChatTranscript(next)
        if(runCatching{candidate.encode()}.isFailure)break
        lines=next;end-=2
    }
    require(lines.isNotEmpty()){ "No completed local text turn fits the 8 KiB checkpoint limit" }
    return ReplicaChatTranscript(lines)
}

internal fun chatRecoveryCheckpoints(commits:List<JournalCommit>):List<ReplicaChatCheckpoint> = commits.asReversed().asSequence()
    .map{it.value.entry}.filter{it.content==JournalContent.CHAT_TRANSCRIPT}.distinctBy{it.taskId}
    .map { entry ->
        requireChatRecoveryTask(entry.taskId)
        require(entry.modelSha256.matches(SHA) && entry.checkpointSha256==recoveryDigest(entry.replayText)) { "Checkpoint digest does not match" }
        val transcript=Json.decodeFromString<ReplicaChatTranscript>(entry.replayText)
        transcript.encode()
        val head=commits.last{it.value.entry.taskId==entry.taskId}.value.entry
        ReplicaChatCheckpoint(entry.taskId,entry.requestId,head.owner,entry.offset,entry.modelSha256,transcript)
    }.take(24).toList()
