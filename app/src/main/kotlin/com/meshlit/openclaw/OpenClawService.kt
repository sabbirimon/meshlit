package com.meshlit.openclaw
import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import com.meshlit.MainActivity
import org.koin.core.context.GlobalContext
class OpenClawService:Service(){
    private var generation:String?=null
    override fun onCreate(){super.onCreate()
        if(Build.VERSION.SDK_INT>=26) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("openclaw-provider","Phone model sharing",NotificationManager.IMPORTANCE_LOW))
        val open=PendingIntent.getActivity(this,31,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        val stop=PendingIntent.getService(this,32,Intent(this,OpenClawService::class.java).setAction("stop"),PendingIntent.FLAG_IMMUTABLE)
        val notice=NotificationCompat.Builder(this,"openclaw-provider").setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Meshlit phone model endpoint active").setContentText("Authenticated loopback access for OpenClaw")
            .setContentIntent(open).setOngoing(true).addAction(0,"Stop sharing",stop).build()
        if(Build.VERSION.SDK_INT>=34) startForeground(713,notice,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(713,notice)
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
        intent?.getStringExtra("generation")?.let{generation=it}
        if(intent?.action=="stop"){GlobalContext.get().get<OpenClawHost>().stopSharing();stopSelf()};return START_NOT_STICKY
    }
    override fun onBind(intent:Intent?):IBinder?=null
    override fun onDestroy(){GlobalContext.get().get<OpenClawHost>().stopIfGeneration(generation);super.onDestroy()}
}
