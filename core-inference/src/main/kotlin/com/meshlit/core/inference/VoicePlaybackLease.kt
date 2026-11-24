package com.meshlit.core.inference

import kotlinx.coroutines.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** One playback; replacement waits for both track cleanup and in-flight SDK Stop calls. */
internal class VoicePlaybackLease {
    internal class Session {
        private val stopped=AtomicBoolean(false)
        private val stopAudio=AtomicReference<(() -> Unit)?>(null)
        var finished=false
        var pendingStops=0
        fun requireActive(){if(stopped.get())throw CancellationException("Voice playback stopped")}
        fun attachStop(action:()->Unit){stopAudio.set(action);if(stopped.get())action()}
        fun detachStop(){stopAudio.set(null)}
        fun stop(){stopped.set(true);stopAudio.get()?.invoke()}
    }
    private var current:Session?=null
    @Synchronized fun begin():Session? {
        if(current!=null)return null
        return Session().also{current=it}
    }
    @Synchronized fun finish(session:Session){session.detachStop();session.finished=true;release(session)}
    fun stop():Session? {
        val session=synchronized(this){current?.also{it.pendingStops++}} ?: return null
        session.stop();return session
    }
    @Synchronized fun completeStop(session:Session){check(session.pendingStops>0);session.pendingStops--;release(session)}
    private fun release(session:Session){if(current===session && session.finished && session.pendingStops==0)current=null}
}
