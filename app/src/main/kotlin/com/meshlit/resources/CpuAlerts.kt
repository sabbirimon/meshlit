package com.meshlit.resources
import android.app.*
import android.content.*
import android.os.*
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.meshlit.core.inference.*
import com.meshlit.models.AccelerationPreferences
import com.meshlit.core.inference.models.CpuMode
import kotlinx.coroutines.*
/** Opt-in, local status transitions while Meshlit is alive. Android owns delivery and sound policy. */
object CpuAlerts {
    fun start(context:Context,scope:CoroutineScope) {scope.launch(Dispatchers.Default){
        val prefs=context.getSharedPreferences("cpu-alerts",0)
        var last="";var lastSound:Boolean?=null;var notifiedAt=0L;var lastPermission=false
        while(isActive){
            try {
                if(!prefs.getBoolean("enabled",false)){NotificationManagerCompat.from(context).cancel(708);last=""}
                else {
                    val power=context.getSystemService(PowerManager::class.java)
                    val memory=ActivityManager.MemoryInfo().also{context.getSystemService(ActivityManager::class.java).getMemoryInfo(it)}
                    val battery=context.registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    val level=battery?.getIntExtra(BatteryManager.EXTRA_LEVEL,-1) ?: -1
                    val scale=battery?.getIntExtra(BatteryManager.EXTRA_SCALE,-1) ?: -1
                    val plugged=battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED,0) ?: 0
                    val alerts=buildList {
                        if(AccelerationPreferences(context).cpuMode()==CpuMode.OVERDRIVE_PLUS)add("Overdrive+ CPU overrides active")
                        if(Build.VERSION.SDK_INT>=29 && power.currentThermalStatus>=2)add("Thermal pressure")
                        if(memory.lowMemory)add("Low available memory")
                        if(scale>0 && level>=0 && level*100/scale<=15 && plugged==0)add("Low battery")
                        if(power.isPowerSaveMode && CpuWorkGate.active!=null)add("Battery saver active")
                    }
                    val state=alerts.joinToString(" · ")
                    val sound=prefs.getBoolean("sound",false)
                    val permission=NotificationManagerCompat.from(context).areNotificationsEnabled()
                    if(state.isEmpty()){NotificationManagerCompat.from(context).cancel(708);last=""}
                    else if(state!=last || sound!=lastSound || permission!=lastPermission) {
                        val canSound=sound && (notifiedAt==0L || SystemClock.elapsedRealtime()-notifiedAt>=60_000)
                        if(sound!=lastSound)NotificationManagerCompat.from(context).cancel(708)
                        val channel=if(sound) "cpu-alerts-sound-1" else "cpu-alerts-silent-1"
                        if(Build.VERSION.SDK_INT>=26)context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                            NotificationChannel(channel,if(sound) "Resource warnings with sound" else "Silent resource warnings",NotificationManager.IMPORTANCE_DEFAULT).apply {
                                setSound(if(sound)RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) else null,AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).build())
                            })
                        if(permission) {
                            val open=PendingIntent.getActivity(context,708,Intent(context,com.meshlit.MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                            val notification=NotificationCompat.Builder(context,channel).setSmallIcon(android.R.drawable.ic_dialog_alert)
                                .setContentTitle("Meshlit resource warning").setContentText(state).setStyle(NotificationCompat.BigTextStyle().bigText(state))
                                .setContentIntent(open).setOnlyAlertOnce(false).setSilent(!canSound).build()
                            try{NotificationManagerCompat.from(context).notify(708,notification)}catch(_:SecurityException){}
                        }
                        last=state;lastSound=sound;lastPermission=permission
                        if(canSound && permission)notifiedAt=SystemClock.elapsedRealtime()
                    }
                }
            }catch(e:CancellationException){throw e}catch(_:Exception){}
            delay(2000)
        }
    }}
}
