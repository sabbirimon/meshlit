package com.meshlit.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/** Require sustained input after a quiet baseline. A single click does not interrupt playback. */
internal class HeadsetSpeechGate(private val threshold: Double) {
    private var baseline = 0.0
    private var calibrated = 0
    private var voiced = 0

    fun accept(samples: Int, rms: Double, watching: Boolean): Boolean {
        require(samples in 1..320 && rms.isFinite() && rms >= 0 && threshold.isFinite() && threshold > 0)
        if (!watching) {
            voiced = 0
            return false
        }
        if (calibrated < 3200) {
            baseline = maxOf(baseline, rms)
            calibrated += samples
            return false
        }
        voiced = if (rms >= maxOf(threshold, baseline * 3)) voiced + samples else 0
        return voiced >= 3200
    }
}

internal suspend fun waitForWiredHeadsetSpeech(
    context: Context,
    threshold: Double,
    allowed: () -> Boolean,
    watching: () -> Boolean,
): Boolean = withContext(Dispatchers.IO) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
        return@withContext false
    }
    val manager = context.getSystemService(AudioManager::class.java)
    val device = manager.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull { it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET }
        ?: return@withContext false
    val format = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .setSampleRate(16_000).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build()
    val minimum = AudioRecord.getMinBufferSize(16_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
    if (minimum <= 0) return@withContext false
    val recorder = AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
        .setAudioFormat(format).setBufferSizeInBytes(maxOf(minimum, 4096)).build()
    try {
        if (recorder.state != AudioRecord.STATE_INITIALIZED || !recorder.setPreferredDevice(device)) return@withContext false
        recorder.startRecording()
        if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) return@withContext false
        val samples = ShortArray(320)
        val detector = HeadsetSpeechGate(threshold)
        var routeAttempts = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            if (!allowed()) return@withContext false
            val route = recorder.routedDevice
            if (route == null && routeAttempts++ < 15) { delay(20); continue }
            if (route?.id != device.id) return@withContext false
            val count = recorder.read(samples, 0, samples.size, AudioRecord.READ_NON_BLOCKING)
            if (count < 0) return@withContext false
            if (count == 0) { delay(10); continue }
            var energy = 0.0
            for (i in 0 until count) {
                val sample = samples[i] / 32768.0
                energy += sample * sample
            }
            if (detector.accept(count, sqrt(energy / count), watching())) return@withContext true
        }
        @Suppress("UNREACHABLE_CODE")
        false
    } finally {
        runCatching { recorder.stop() }
        recorder.release()
    }
}
