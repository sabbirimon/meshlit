package com.meshlit.models

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.meshlit.MainActivity
import kotlinx.coroutines.*
import org.koin.core.context.GlobalContext

/** Transfers started by a visible user action. Process death leaves resumable
 * artifacts; the app reconciles them as Paused instead of inventing completion. */
class ModelDownloadService : Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val library get()=GlobalContext.get().get<ModelLibrary>()
    override fun onCreate() {
        super.onCreate()
        val manager=getSystemService(NotificationManager::class.java)
        if(Build.VERSION.SDK_INT>=26) manager.createNotificationChannel(NotificationChannel(CHANNEL,"Model transfers",NotificationManager.IMPORTANCE_LOW))
        val notification=notification("Preparing model transfer")
        if(Build.VERSION.SDK_INT>=29) startForeground(ID,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(ID,notification)
        scope.launch {
            library.ready.await()
            delay(750)
            while(isActive) {
                val entries=library.models.value.filter { it.active }
                if(!library.hasActiveTransfers()) { stopSelf(); break }
                manager.notify(ID,notification(if(entries.isEmpty()) "Finalizing transfer" else
                    "${entries.size} model transfers · ${entries.first().phase}"))
                delay(1000)
            }
        }
    }
    private fun notification(message:String):Notification {
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop=PendingIntent.getService(this,1,Intent(this,ModelDownloadService::class.java).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Meshlit model transfers").setContentText(message).setContentIntent(open)
            .setOngoing(true).setOnlyAlertOnce(true).addAction(0,"Pause all",stop).build()
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action==STOP) { library.pauseAll();stopSelf() }
        return START_NOT_STICKY
    }
    override fun onTimeout(startId:Int,fgsType:Int) { library.pauseAll();stopSelf() }
    override fun onDestroy() { scope.cancel();super.onDestroy() }
    override fun onBind(intent:Intent?):IBinder?=null
    companion object { private const val CHANNEL="meshlit-model-transfers";private const val ID=705;private const val STOP="pause-model-transfers" }
}
