package com.meshlit.core.inference

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One heavy SDK/local generation or offline speech operation at a time. Audio capture/playback remain independent. */
object CpuWorkGate {
    private val mutex=Mutex()
    private val waiting=java.util.concurrent.atomic.AtomicInteger()
    val queued:Int get()=waiting.get()
    @Volatile var active:String?=null
        private set
    suspend fun <T> run(label:String="Compute",action:suspend()->T):T {
        waiting.incrementAndGet();var acquired=false
        try {return mutex.withLock {acquired=true;waiting.decrementAndGet();active=label
            try {action()} finally {active=null}
        }} finally {if(!acquired) waiting.decrementAndGet()}
    }
}
