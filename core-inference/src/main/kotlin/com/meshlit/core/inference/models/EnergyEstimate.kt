package com.meshlit.core.inference.models

import kotlin.math.abs

/** Battery-side device estimates, not wall-plug or app-only measurements. */
object EnergyEstimate {
    fun watts(currentMicroamps:Int?,voltageMillivolts:Int?):Double? {
        if(currentMicroamps==null || voltageMillivolts==null || currentMicroamps==Int.MIN_VALUE || voltageMillivolts !in 1000..20000) return null
        // Android reports positive charge / negative discharge current. Preserve the sign.
        return currentMicroamps.toDouble()*voltageMillivolts/1e9
    }
    fun dischargeWh(previousMs:Long,currentMs:Long,previousWatts:Double?,currentWatts:Double?,charging:Boolean):Double? {
        val delta=currentMs-previousMs
        if(charging || delta !in 1..15000 || previousWatts==null || currentWatts==null || previousWatts>=0 || currentWatts>=0) return null
        return abs((previousWatts+currentWatts)/2)*delta/3600000.0
    }
    fun cost(energyWh:Double,pricePerKwh:Double,chargingEfficiency:Double):Double {
        require(energyWh.isFinite() && energyWh>=0 && pricePerKwh.isFinite() && pricePerKwh>=0 && chargingEfficiency.isFinite() && chargingEfficiency in 0.1..1.0)
        return energyWh/1000/chargingEfficiency*pricePerKwh
    }
}
