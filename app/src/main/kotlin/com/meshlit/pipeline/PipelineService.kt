package com.meshlit.pipeline
import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import com.meshlit.MainActivity
import kotlinx.coroutines.*
import org.koin.core.context.GlobalContext
class PipelineService:Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
    private var intentionalStop=false
    private val host get()=GlobalContext.get().get<PipelineHost>()
    override fun onCreate(){super.onCreate()
        if(Build.VERSION.SDK_INT>=26) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("meshlit-pipeline","Layer pipeline",NotificationManager.IMPORTANCE_LOW))
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        val stop=PendingIntent.getService(this,1,Intent(this,PipelineService::class.java).setAction("stop"),PendingIntent.FLAG_IMMUTABLE)
        val notice=NotificationCompat.Builder(this,"meshlit-pipeline").setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Meshlit layer runtime active").setContentText("Approved devices can compute model layers")
            .setContentIntent(open).setOngoing(true).addAction(0,"Stop",stop).build()
        if(Build.VERSION.SDK_INT>=34) startForeground(708,notice,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(708,notice)
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action=="stop-if-idle" && !host.active()) intentionalStop=stopSelfResult(startId)
        if(intent?.action=="stop") scope.launch{host.stopAll();if(!host.active()) intentionalStop=stopSelfResult(startId)}
        return START_NOT_STICKY
    }
    override fun onDestroy(){
        scope.cancel()
        if(!intentionalStop){host.serviceDestroyed();if(host.active()) CoroutineScope(SupervisorJob()+Dispatchers.IO).launch{host.stopAll()}}
        super.onDestroy()
    }
    override fun onBind(intent:Intent?):IBinder?=null
}
