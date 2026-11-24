package com.meshlit.chat

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.meshlit.R
import java.io.File

/** A user-approved, single-frame screen attachment. The chat composer owns delivery. */
class ChatScreenCapture : Service() {
    private val handler=Handler(Looper.getMainLooper())
    private var projection:MediaProjection?=null
    private var display:VirtualDisplay?=null
    private var reader:ImageReader?=null
    private var result:ResultReceiver?=null
    private var finished=false

    override fun onBind(intent:Intent?)=null
    @Suppress("DEPRECATION")
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action=="stop" || intent==null){finish(null);return START_NOT_STICKY}
        if(result!=null)return START_NOT_STICKY
        result=intent.getParcelableExtra("receiver")
        try {
            val channel="chat-screen-frame"
            if(Build.VERSION.SDK_INT>=26)getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(channel,"Screen attachments",NotificationManager.IMPORTANCE_LOW))
            val stop=PendingIntent.getService(this,0,Intent(this,ChatScreenCapture::class.java).setAction("stop"),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val notice=NotificationCompat.Builder(this,channel).setSmallIcon(R.drawable.ic_meshlit_notification)
                .setContentTitle("Meshlit screen attachment").setContentText("Capturing one frame in five seconds")
                .setOngoing(true).addAction(0,"Cancel",stop).build()
            if(Build.VERSION.SDK_INT>=29)startForeground(4103,notice,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            else startForeground(4103,notice)
            val consent=intent.getParcelableExtra<Intent>("consent") ?: error("Missing capture permission")
            val session=getSystemService(MediaProjectionManager::class.java).getMediaProjection(Activity.RESULT_OK,consent) ?: error("Screen permission expired")
            projection=session
            session.registerCallback(object:MediaProjection.Callback(){override fun onStop(){finish(null)}},handler)
            val metrics=resources.displayMetrics
            val scale=minOf(1.0,1280.0/maxOf(metrics.widthPixels,metrics.heightPixels))
            val width=(metrics.widthPixels*scale).toInt().coerceAtLeast(1)
            val height=(metrics.heightPixels*scale).toInt().coerceAtLeast(1)
            val images=ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,2)
            reader=images
            // Open the virtual display after the countdown so its first available frame is current.
            handler.postDelayed({
                if(!finished)try {
                    images.setOnImageAvailableListener({source->
                        if(!finished)try {
                            val frame=source.acquireLatestImage() ?: return@setOnImageAvailableListener
                            frame.use {
                                val plane=frame.planes.first()
                                val padded=Bitmap.createBitmap(plane.rowStride/plane.pixelStride,height,Bitmap.Config.ARGB_8888)
                                try {
                                    padded.copyPixelsFromBuffer(plane.buffer)
                                    val crop=Bitmap.createBitmap(padded,0,0,width,height)
                                    try {
                                        val dir=File(cacheDir,"exports/captures").apply{mkdirs()}
                                        val file=File.createTempFile("chat-screen-",".jpg",dir)
                                        try {
                                            file.outputStream().use{check(crop.compress(Bitmap.CompressFormat.JPEG,82,it))}
                                            finish(FileProvider.getUriForFile(this,"${packageName}.fileprovider",file))
                                        }catch(e:Exception){file.delete();throw e}
                                    }finally{if(crop!==padded)crop.recycle()}
                                }finally{padded.recycle()}
                            }
                        }catch(_:Exception){finish(null)}
                    },handler)
                    display=session.createVirtualDisplay("Chat screen attachment",width,height,metrics.densityDpi,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,images.surface,null,handler)
                }catch(_:Exception){finish(null)}
            },5000)
            handler.postDelayed({finish(null)},12000)
        }catch(_:Exception){finish(null)}
        return START_NOT_STICKY
    }
    private fun finish(uri:android.net.Uri?) {
        if(finished)return
        finished=true
        result?.send(if(uri==null) Activity.RESULT_CANCELED else Activity.RESULT_OK,Bundle().apply{putParcelable("frame",uri)})
        stopSelf()
    }
    override fun onDestroy(){
        finished=true
        handler.removeCallbacksAndMessages(null)
        display?.release()
        reader?.close()
        projection?.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
