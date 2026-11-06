package com.meshlit.chat

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/** A single, session-only decision. Timeout, Stop and stale approvals cannot submit a transcript. */
data class PendingVoiceTranscript(val id:String,val text:String)
internal class VoiceTranscriptReview(private val timeoutMs:Long=60_000) {
    private var pending:Pair<PendingVoiceTranscript,CompletableDeferred<String?>>?=null
    suspend fun await(text:String,publish:(PendingVoiceTranscript?)->Unit):String? {
        val item=PendingVoiceTranscript(UUID.randomUUID().toString(),text)
        val decision=CompletableDeferred<String?>()
        synchronized(this){check(pending==null);pending=item to decision}
        return try {publish(item);withTimeoutOrNull(timeoutMs){decision.await()}}
        finally{synchronized(this){if(pending?.first==item)pending=null};decision.cancel();publish(null)}
    }
    @Synchronized fun answer(id:String,text:String?):Boolean {
        val current=pending ?: return false
        val normalized=text?.trim()
        if(current.first.id!=id || (normalized!=null && (normalized.isBlank() || normalized.length>12000 || '\u0000' in normalized)))return false
        return current.second.complete(normalized)
    }
}
