package com.meshlit.control
import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import com.meshlit.MainActivity
import org.koin.core.context.GlobalContext
class WebBridgeService:Service(){
    private var generation:String?=null
    override fun onCreate(){super.onCreate()
        if(Build.VERSION.SDK_INT>=26) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("control-bridge","Approved device API",NotificationManager.IMPORTANCE_LOW))
        val open=PendingIntent.getActivity(this,81,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        val stop=PendingIntent.getService(this,82,Intent(this,WebBridgeService::class.java).setAction("stop"),PendingIntent.FLAG_IMMUTABLE)
        val notification=NotificationCompat.Builder(this,"control-bridge").setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Meshlit approved device API active").setContentText("TLS control endpoint on your network")
            .setContentIntent(open).setOngoing(true).addAction(0,"Stop access",stop).build()
        if(Build.VERSION.SDK_INT>=34) startForeground(719,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(719,notification)
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{intent?.getStringExtra("generation")?.let{generation=it};if(intent?.action=="stop"){GlobalContext.get().get<WebBridgeHost>().stop();stopSelf()};return START_NOT_STICKY}
    override fun onBind(intent:Intent?):IBinder?=null
    override fun onDestroy(){GlobalContext.get().get<WebBridgeHost>().stopIfGeneration(generation);super.onDestroy()}
}
