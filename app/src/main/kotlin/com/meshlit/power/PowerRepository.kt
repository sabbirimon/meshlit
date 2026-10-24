package com.meshlit.power

import android.content.*
import android.os.*
import android.net.*
import com.meshlit.core.inference.models.EnergyEstimate
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable data class PowerPolicy(val minimumBattery:Int=15,val pauseLowBatteryDownloads:Boolean=true,
    val unmeteredDownloadsOnly:Boolean=false,val chargingForNewLoads:Boolean=false) {
    fun validate(){require(minimumBattery in 5..80)}
}
@Serializable data class PowerReading(val observedAtMs:Long,val batteryPercent:Int?,val charging:Boolean?,val plugged:Int?,val voltageMv:Int?,
    val temperatureC:Double?,val currentUa:Int?,val chargeCounterUah:Int?,val energyCounterNwh:Long?,val signedBatteryWatts:Double?,
    val powerSave:Boolean,val thermalStatus:Int?,val metered:Boolean,val internetValidated:Boolean)
class PowerRepository(private val context:Context) {
    private val prefs=context.getSharedPreferences("power-cost-policy",0)
    private val json=Json{ignoreUnknownKeys=true}
    fun policy():PowerPolicy=runCatching{json.decodeFromString<PowerPolicy>(prefs.getString("policy",null) ?: "{}")}.getOrDefault(PowerPolicy())
    fun save(policy:PowerPolicy){policy.validate();check(prefs.edit().putString("policy",json.encodeToString(policy)).commit())}
    fun reading():PowerReading {
        val intent=context.registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val manager=context.getSystemService(BatteryManager::class.java)
        val power=context.getSystemService(PowerManager::class.java)
        val connectivity=context.getSystemService(ConnectivityManager::class.java)
        fun extra(key:String)=intent?.takeIf{it.hasExtra(key)}?.getIntExtra(key,-1)?.takeIf{it>=0}
        fun property(id:Int)=runCatching{manager.getIntProperty(id)}.getOrNull()?.takeUnless{it==Int.MIN_VALUE}
        val level=extra(BatteryManager.EXTRA_LEVEL);val scale=extra(BatteryManager.EXTRA_SCALE)
        val voltage=extra(BatteryManager.EXTRA_VOLTAGE)?.takeIf{it>0}
        val current=property(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val status=extra(BatteryManager.EXTRA_STATUS)
        val capabilities=connectivity.activeNetwork?.let{connectivity.getNetworkCapabilities(it)}
        return PowerReading(SystemClock.elapsedRealtime(),if(level!=null && scale!=null && scale>0) (100*level/scale).coerceIn(0,100) else null,
            status?.let{it==BatteryManager.BATTERY_STATUS_CHARGING || it==BatteryManager.BATTERY_STATUS_FULL},extra(BatteryManager.EXTRA_PLUGGED),voltage,
            extra(BatteryManager.EXTRA_TEMPERATURE)?.div(10.0),current,property(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)?.takeIf{it>0},
            runCatching{manager.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)}.getOrNull()?.takeIf{it>0 && it!=Long.MIN_VALUE},
            EnergyEstimate.watts(current,voltage),power.isPowerSaveMode,if(Build.VERSION.SDK_INT>=29) power.currentThermalStatus else null,
            connectivity.isActiveNetworkMetered,capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)==true)
    }
    fun downloadBlockReason():String? {
        val policy=policy();val reading=reading()
        return when {
            policy.unmeteredDownloadsOnly && (!reading.internetValidated || reading.metered)->"Waiting for a validated unmetered network"
            policy.pauseLowBatteryDownloads && reading.charging!=true && (reading.batteryPercent ?: 100)<policy.minimumBattery->"Paused below ${policy.minimumBattery}% battery"
            else->null
        }
    }
    fun loadBlockReason():String?=if(policy().chargingForNewLoads && reading().charging!=true) "Connect power before starting a new model load" else null
    fun pricePerKwh():Double?=prefs.getString("price",null)?.toDoubleOrNull()
    fun efficiency():Double=prefs.getString("efficiency",null)?.toDoubleOrNull() ?: 0.85
    fun currency():String=prefs.getString("currency","USD") ?: "USD"
    fun saveCost(price:Double?,efficiency:Double,currency:String) {
        require(price==null || price.isFinite() && price in 0.0..100000.0);require(efficiency.isFinite() && efficiency in 0.1..1.0);require(currency.matches(Regex("[A-Z]{3}")))
        check(prefs.edit().putString("price",price?.toString()).putString("efficiency",efficiency.toString()).putString("currency",currency).commit())
    }
}
