package com.meshlit.ui.modern
import android.hardware.*
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.meshlit.resources.*

@Composable internal fun LiveSensorPanel() {
    val context=LocalContext.current;val owner=LocalLifecycleOwner.current
    val manager=remember{context.getSystemService(SensorManager::class.java)}
    val supported=setOf(Sensor.TYPE_ACCELEROMETER,Sensor.TYPE_GYROSCOPE,Sensor.TYPE_MAGNETIC_FIELD,Sensor.TYPE_LIGHT,Sensor.TYPE_PRESSURE,Sensor.TYPE_PROXIMITY,Sensor.TYPE_RELATIVE_HUMIDITY,Sensor.TYPE_AMBIENT_TEMPERATURE,Sensor.TYPE_GRAVITY,Sensor.TYPE_LINEAR_ACCELERATION,Sensor.TYPE_ROTATION_VECTOR)
    var refresh by remember{mutableIntStateOf(0)}
    val sensors=remember(refresh){(manager.getSensorList(Sensor.TYPE_ALL)+manager.getDynamicSensorList(Sensor.TYPE_ALL)).distinctBy{listOf(it.id,it.type,it.name,it.vendor,it.isDynamicSensor)}}
    var selected by remember{mutableStateOf<Sensor?>(null)};var running by remember{mutableStateOf(false)}
    var share by remember{mutableStateOf(false)};var expanded by remember{mutableStateOf(false)}
    var reading by remember{mutableStateOf<LiveSensorReading?>(null)};var status by remember{mutableStateOf("Stopped")}
    DisposableEffect(selected,running,share,owner){
        val listener=object:SensorEventListener {
            override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int){}
            override fun onSensorChanged(event:SensorEvent){
                val values=event.values.take(16);if(values.isEmpty() || values.any{!it.isFinite()})return
                val next=LiveSensorReading(event.sensor.type,event.sensor.name,event.sensor.vendor,values,System.currentTimeMillis(),Build.MODEL,event.sensor.isDynamicSensor,liveSensorUnits(event.sensor.type,values.size))
                reading=next;status="Receiving real samples";if(share)LiveSensorReadings.publish(next)
            }
        }
        fun update(){
            manager.unregisterListener(listener);LiveSensorReadings.clear()
            if(running && selected!=null && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                status=try{if(manager.registerListener(listener,selected,200_000)) "Waiting for sensor samples" else "Sensor unavailable"}catch(_:SecurityException){"Android permission required; use its dedicated setup screen"}
            }
        }
        val observer=LifecycleEventObserver{_,_->update()};owner.lifecycle.addObserver(observer);update()
        onDispose{owner.lifecycle.removeObserver(observer);manager.unregisterListener(listener);LiveSensorReadings.clear()}
    }
    Text("Sensors and external sources",style=MaterialTheme.typography.titleMedium)
    Text("${sensors.size} sensors reported by Android. Dynamic USB/Bluetooth sensors appear when their Android driver exposes them.")
    Button(onClick={refresh++}){Text("Refresh sensor inventory")}
    Box{OutlinedButton(onClick={expanded=true}){Text(selected?.name ?: "Choose a live sensor")}
        DropdownMenu(expanded,{expanded=false}){sensors.filter{it.type in supported}.forEach{s->DropdownMenuItem(text={Text("${s.name} · ${s.vendor}${if(s.isDynamicSensor) " · external" else ""}")},onClick={selected=s;reading=null;running=false;expanded=false})}}
    }
    Row{Switch(running,{running=it},enabled=selected!=null);Text("Read selected sensor while dashboard is visible")}
    Row{Switch(share,{share=it});Text("Share live readings with approved tools/devices")}
    Text(status)
    reading?.let{Text("${it.name}: ${it.values.mapIndexed{index,v->"%.3f %s".format(v,it.units.getOrElse(index){"unknown"})}.joinToString()}\nObserved ${it.observedAtMs} · ${it.sourceDevice}")}
    Text("Each component includes its Android SensorEvent unit; unknown units stay unknown. Samples expire after 10 seconds and are cleared when stopped or hidden. Sharing uses the existing authenticated resource_status tool and its grants. Paired remote devices must expose that tool and explicitly share their own readings. Camera, microphone, location and permission-protected sensors use their separate controls.")
}
