package com.meshlit.openclaw
import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import com.meshlit.MainActivity
import org.koin.core.context.GlobalContext
class OpenClawNodeService:Service(){
    private var epoch:Long?=null
    override fun onCreate(){super.onCreate()
        if(Build.VERSION.SDK_INT>=26) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("openclaw-node","OpenClaw Android node",NotificationManager.IMPORTANCE_LOW))
        val open=PendingIntent.getActivity(this,41,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        val stop=PendingIntent.getService(this,42,Intent(this,OpenClawNodeService::class.java).setAction("stop"),PendingIntent.FLAG_IMMUTABLE)
        val notice=NotificationCompat.Builder(this,"openclaw-node").setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Meshlit OpenClaw node active").setContentText("Device commands follow your saved delegation")
            .setContentIntent(open).setOngoing(true).addAction(0,"Disconnect",stop).build()
        if(Build.VERSION.SDK_INT>=34) startForeground(714,notice,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(714,notice)
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
        if(intent?.hasExtra("epoch")==true) epoch=intent.getLongExtra("epoch",-1)
        if(intent?.action=="stop"){GlobalContext.get().get<OpenClawNode>().disconnect();stopSelf()};return START_NOT_STICKY
    }
    override fun onBind(intent:Intent?):IBinder?=null
    override fun onDestroy(){epoch?.let{GlobalContext.get().get<OpenClawNode>().disconnectIfEpoch(it)};super.onDestroy()}
}
