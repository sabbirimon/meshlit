package com.meshlit.core.training

/** No Android autograd backend is installed. Never manufacture gradients or training progress. */
@Suppress("UNUSED_PARAMETER")
class LocalLoraTrainer(private val paramCount:Int=1024) {
    val available:Boolean get()=false
    suspend fun computeLocalGradient(step:Long,loraRank:Int,seed:Long):FloatArray =
        throw UnsupportedOperationException("training_backend_unavailable: configure a real training host")
    suspend fun applyGradient(averaged:FloatArray):Unit =
        throw UnsupportedOperationException("training_backend_unavailable: no optimizer is installed")
    fun snapshot()=Snapshot(0,0f,paramCount)
    data class Snapshot(val stepCount:Long,val lastMagnitude:Float,val paramCount:Int,val available:Boolean=false)
}
