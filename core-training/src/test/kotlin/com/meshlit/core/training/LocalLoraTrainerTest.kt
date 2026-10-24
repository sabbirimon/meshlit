package com.meshlit.core.training
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
class LocalLoraTrainerTest {
    @Test fun dispatcherRejectsUnavailableTrainingBeforeGradientExchange()=runBlocking {
        val trainer=LocalLoraTrainer()
        val dispatcher=com.meshlit.core.training.averaging.StrategyDispatcher(trainer,ThermalGuard(),com.meshlit.core.training.averaging.NaNGuard(),com.meshlit.core.training.averaging.P2pRingAverager())
        val result=dispatcher.step(1,com.meshlit.core.training.config.DistributedConfigLoader.defaultConfig(),emptyList(),"self","127.0.0.1",9999)
        assertTrue(result is com.meshlit.core.common.MeshlitResult.Failure)
        assertFalse(trainer.snapshot().available)
        assertEquals(0L,trainer.snapshot().stepCount)
    }
    @Test fun missingBackendNeverManufacturesOrAppliesGradients()=runBlocking {
        val trainer=LocalLoraTrainer();assertFalse(trainer.available)
        try {trainer.computeLocalGradient(1,16,1);fail("Synthetic gradient returned")}catch(e:UnsupportedOperationException){assertTrue(e.message!!.contains("training_backend_unavailable"))}
        try {trainer.applyGradient(floatArrayOf(1f));fail("Applied without optimizer")}catch(e:UnsupportedOperationException){assertTrue(e.message!!.contains("training_backend_unavailable"))}
        assertEquals(0L,trainer.snapshot().stepCount)
    }
}
