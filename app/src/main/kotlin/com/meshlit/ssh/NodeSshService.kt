package com.meshlit.ssh

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import org.koin.core.context.GlobalContext

@RequiresApi(26)
class NodeSshService:Service() {
    private var sessionId:String?=null
    private var sessionStartId:Int=0
    override fun onCreate() {
        super.onCreate()
        val manager=getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("node-ssh","SSH node session",NotificationManager.IMPORTANCE_LOW))
        val stop=PendingIntent.getService(this,9,Intent(this,NodeSshService::class.java).setAction("stop"),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        startForeground(2233,NotificationCompat.Builder(this,"node-ssh").setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Meshlit SSH node running").setContentText("Public-key access · maximum 30 minutes")
            .setOngoing(true).addAction(0,"Stop SSH",stop).build())
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        intent?.getStringExtra("session")?.let { sessionId=it;sessionStartId=startId }
        if(intent?.action=="stop" || !com.meshlit.legal.LegalAgreementStore(this).accepted()) {
            GlobalContext.get().get<NodeSshHost>().stop();stopSelf()
        }
        return START_NOT_STICKY
    }
    override fun onDestroy() { sessionId?.let { GlobalContext.get().get<NodeSshHost>().stop(it) };super.onDestroy() }
    override fun onBind(intent:Intent?):IBinder?=null
    override fun onTimeout(startId:Int) {
        if(startId==sessionStartId) sessionId?.let { GlobalContext.get().get<NodeSshHost>().stop(it) }
        stopSelf(startId)
    }
}
