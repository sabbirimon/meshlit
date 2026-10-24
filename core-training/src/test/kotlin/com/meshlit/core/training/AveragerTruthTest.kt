package com.meshlit.core.training
import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.training.averaging.*
import com.meshlit.core.training.config.DistributedConfigLoader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
class AveragerTruthTest {
    private suspend fun execute(status:DesktopPeerStatus):MeshlitResult<AveragedGradient> = AccelerateDelegateAverager({status}).average(1,floatArrayOf(1f),emptyList(),DistributedConfigLoader.defaultConfig(),"self","127.0.0.1",9999)
    @Test fun dilocoDoesNotInventOtherPeerDeltas()=runBlocking {
        val peers=listOf(com.meshlit.core.training.ring.RingParticipant("self","127.0.0.1",9999),com.meshlit.core.training.ring.RingParticipant("other","127.0.0.1",9998))
        val result=DiLoCoAverager().average(1,floatArrayOf(1f),peers,DistributedConfigLoader.defaultConfig(),"self","127.0.0.1",9999)
        assertTrue(result is MeshlitResult.Failure)
    }
    @Test fun missingDesktopOrGradientCannotLookLikeSuccessfulTraining()=runBlocking {
        listOf(DesktopPeerStatus.Offline(),DesktopPeerStatus.Syncing("host"),DesktopPeerStatus.Online("host"),DesktopPeerStatus.Online("host",floatArrayOf(Float.NaN))).forEach{assertTrue(execute(it) is MeshlitResult.Failure)}
    }
    @Test fun actualSuppliedGradientKeepsUnmeasuredLossUnknown()=runBlocking {
        val result=execute(DesktopPeerStatus.Online("host",floatArrayOf(2f))) as MeshlitResult.Success
        assertArrayEquals(floatArrayOf(2f),result.value.values,0f);assertNull(result.value.loss)
    }
}
