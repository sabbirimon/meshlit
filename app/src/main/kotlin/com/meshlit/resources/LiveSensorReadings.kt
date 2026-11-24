package com.meshlit.resources
import kotlinx.serialization.Serializable
@Serializable data class LiveSensorReading(val type:Int,val name:String,val vendor:String,val values:List<Float>,val observedAtMs:Long,val sourceDevice:String,val dynamicExternal:Boolean,val units:List<String> = emptyList())
/** Only owner-shared, recent samples enter the existing resource_status tool. No recording or new listener port. */
object LiveSensorReadings {
    @Volatile private var current:LiveSensorReading?=null
    fun publish(reading:LiveSensorReading){require(reading.values.size in 1..16 && reading.values.all{it.isFinite()} && (reading.units.isEmpty() || reading.units.size==reading.values.size));current=reading}
    fun clear(){current=null}
    fun snapshot(now:Long=System.currentTimeMillis()):List<LiveSensorReading> = current?.takeIf{now-it.observedAtMs in 0..10_000}?.let{listOf(it)} ?: emptyList()
}

/** Android SensorEvent units; unknown components remain explicitly unknown. */
fun liveSensorUnits(type:Int,count:Int):List<String> {
    val unit=when(type){
        android.hardware.Sensor.TYPE_ACCELEROMETER,android.hardware.Sensor.TYPE_GRAVITY,android.hardware.Sensor.TYPE_LINEAR_ACCELERATION->"m/s²"
        android.hardware.Sensor.TYPE_GYROSCOPE->"rad/s"
        android.hardware.Sensor.TYPE_MAGNETIC_FIELD->"µT"
        android.hardware.Sensor.TYPE_LIGHT->"lx"
        android.hardware.Sensor.TYPE_PRESSURE->"hPa"
        android.hardware.Sensor.TYPE_PROXIMITY->"cm"
        android.hardware.Sensor.TYPE_RELATIVE_HUMIDITY->"%"
        android.hardware.Sensor.TYPE_AMBIENT_TEMPERATURE->"°C"
        else->"unknown"
    }
    return List(count){index->if(type==android.hardware.Sensor.TYPE_ROTATION_VECTOR)when(index){in 0..3->"unitless";4->"rad";else->"unknown"} else unit}
}
