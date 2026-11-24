package com.meshlit.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class LiveCameraFrame(val jpeg: ByteArray, val thumbnail: Bitmap)

internal suspend fun <T> withLiveCamera(
    context: Context,
    owner: LifecycleOwner,
    use: suspend (capture: suspend () -> LiveCameraFrame) -> T,
): T = withContext(Dispatchers.Main.immediate) {
    check(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
        "Grant camera access to start live vision"
    }
    check(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) { "Return to Meshlit to start live vision" }
    val main = ContextCompat.getMainExecutor(context)
    val future = ProcessCameraProvider.getInstance(context.applicationContext)
    val provider = withTimeout(10_000) {
        suspendCancellableCoroutine { result ->
            future.addListener({
                if (result.isActive) runCatching { future.get() }.fold(result::resume, result::resumeWithException)
            }, main)
        }
    }
    @Suppress("DEPRECATION")
    val camera = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .setTargetResolution(Size(640, 480))
        .setFlashMode(ImageCapture.FLASH_MODE_OFF)
        .build()
    try {
        provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, camera)
        use {
            check(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) { "Camera paused" }
            val (bytes, rotation) = withTimeout(8_000) {
                suspendCancellableCoroutine { result ->
                    camera.takePicture(main, object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            try {
                                if (!result.isActive) return
                                check(image.format == ImageFormat.JPEG) { "Camera did not return a JPEG frame" }
                                val buffer = image.planes.single().buffer
                                require(buffer.remaining() in 4..4 * 1024 * 1024) { "Camera frame is too large" }
                                val encoded = ByteArray(buffer.remaining()).also { buffer.get(it) }
                                result.resume(encoded to image.imageInfo.rotationDegrees)
                            } catch (error: Exception) {
                                if (result.isActive) result.resumeWithException(error)
                            } finally { image.close() }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            if (result.isActive) result.resumeWithException(exception)
                        }
                    })
                }
            }
            withContext(Dispatchers.IO) {
                val info = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, info)
                require(info.outWidth in 1..8192 && info.outHeight in 1..8192) { "Unsupported camera frame dimensions" }
                var sample = 1
                while (maxOf(info.outWidth, info.outHeight) / sample > 1024) sample *= 2
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                    ?: error("Camera frame could not be decoded")
                val corrected = if (rotation == 0) bitmap else Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotation.toFloat()) }, true,
                )
                if (corrected !== bitmap) bitmap.recycle()
                val output = ByteArrayOutputStream()
                check(corrected.compress(Bitmap.CompressFormat.JPEG, 80, output))
                LiveCameraFrame(output.toByteArray(), corrected)
            }
        }
    } finally {
        withContext(NonCancellable + Dispatchers.Main.immediate) { provider.unbind(camera) }
    }
}
