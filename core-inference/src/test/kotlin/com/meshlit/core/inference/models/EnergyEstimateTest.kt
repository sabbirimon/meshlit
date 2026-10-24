package com.meshlit.core.inference.models
import org.junit.Assert.*
import org.junit.Test
class EnergyEstimateTest {
    @Test fun unsupportedAndInvalidSensorsDoNotBecomeZeroPower(){assertNull(EnergyEstimate.watts(Int.MIN_VALUE,4000));assertNull(EnergyEstimate.watts(null,4000));assertNull(EnergyEstimate.watts(100,0))}
    @Test fun sensorUnitsAndDischargeIntegrationPreservePhysicalMeaning(){assertEquals(-2.0,EnergyEstimate.watts(-500000,4000)!!,1e-10);assertEquals(2.0,EnergyEstimate.watts(500000,4000)!!,1e-10);assertEquals(2.0*5/3600,EnergyEstimate.dischargeWh(0,5000,-2.0,-2.0,false)!!,1e-10)}
    @Test fun chargingGapsAndClockRegressionAreExcluded(){assertNull(EnergyEstimate.dischargeWh(0,5000,-2.0,-2.0,true));assertNull(EnergyEstimate.dischargeWh(0,16000,-2.0,-2.0,false));assertNull(EnergyEstimate.dischargeWh(5000,0,-2.0,-2.0,false));assertNull(EnergyEstimate.dischargeWh(0,5000,2.0,-2.0,false))}
    @Test fun costRequiresExplicitTariffAndEfficiency(){assertEquals(0.4,EnergyEstimate.cost(1000.0,0.2,0.5),1e-10);assertThrows(IllegalArgumentException::class.java){EnergyEstimate.cost(1.0,0.1,Double.NaN)}}
    @Test fun absentProviderUsageOrRatesLeavesCostUnknown(){val profile=OnlineProfile("test","Test","https://example.org/v1",inputPricePerMillion=1.0,outputPricePerMillion=2.0);assertNull(OnlineReply("reply",null,100).estimatedCost(profile));assertNull(OnlineReply("reply",100,100).estimatedCost(profile.copy(inputPricePerMillion=null)));assertEquals(0.0003,OnlineReply("reply",100,100).estimatedCost(profile)!!,1e-10)}
}
