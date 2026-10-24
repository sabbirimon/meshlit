package com.meshlit.chat

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.meshlit.MainActivity
import org.koin.core.context.GlobalContext

/** Chat generation lifetime only; does not open cluster/control listeners. */
class ChatInferenceService:Service() {
    private var generationId:String?=null
    private val controller get()=GlobalContext.get().get<ChatController>()
    override fun onCreate() {
        super.onCreate()
        if(Build.VERSION.SDK_INT>=26) getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("meshlit-chat","Chat generation",NotificationManager.IMPORTANCE_LOW))
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop=PendingIntent.getService(this,1,Intent(this,ChatInferenceService::class.java).setAction("stop"),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification=NotificationCompat.Builder(this,"meshlit-chat").setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Meshlit is generating").setContentText("Tap Stop to end this response")
            .setContentIntent(open).setOngoing(true).addAction(0,"Stop",stop).build()
        if(Build.VERSION.SDK_INT>=34) startForeground(706,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(706,notification)
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        intent?.getStringExtra("generation")?.let { generationId=it }
        if(intent?.action=="stop") { controller.stop();stopSelf() };return START_NOT_STICKY
    }
    override fun onDestroy() { controller.stopIfMatching(generationId);super.onDestroy() }
    override fun onBind(intent:Intent?):IBinder?=null
}
