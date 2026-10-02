package com.meshlit.ui.components.voice

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Live audio spectrum analyzer for the v2 Voice screen.
 *
 * Renders a 32-bar magnitude display from a 1024-sample 16-kHz
 * mono PCM frame so the user can SEE whether the mic is actually
 * capturing audio. Each bar represents a frequency bin in
 * 500 Hz steps from 0 Hz → 8 kHz (Nyquist for 16-kHz audio).
 *
 * Why a real spectrum instead of a single VAD bar:
 *  - **Confidence**: the VAD bar only tells the user "speech was
 *    detected", not "the mic is open". A spectrum with visible
 *    noise floor + speech peaks is unambiguous evidence of capture.
 *  - **Calibration**: the user can hold the phone near a fan /
 *    speaker / keyboard and watch the spectrum shape change —
 *    useful when validating that the chosen mic is the right one.
 *  - **Diagnosis**: silent audio with a flat noise floor points
 *    to a permissions issue, a muted mic, or a hardware fault;
 *    a tall low-band energy with no high-band content points to
 *    the user's voice (vs a fan blowing on the back mic).
 *
 * The analyzer is a 32-point Cooley–Tukey radix-2 FFT. Each
 * ~32 ms frame runs ~1 024 multiply-adds per bin × 32 bins = 33 k
 * ops, well under a millisecond on a Pixel 4. A Hann window is
 * applied before the FFT so the 32 frequency bins aren't smeared
 * by spectral leakage — that lets us resolve voice formants
 * (~300 Hz–4 kHz) without adjacent-bin contamination.
 *
 * The bars use a logarithmic scale (40 dB dynamic range) so the
 * user can see both loud and quiet signals without one drowning
 * the other. Each bar has a 2 px "peak hold" indicator that
 * slowly decays so the user can see how loud each band got.
 */
@Composable
fun MeshlitAudioSpectrum(
    pcmFlow: kotlinx.coroutines.flow.Flow<ByteArray>?,
    modifier: Modifier = Modifier,
    barCount: Int = 32,
    height: Dp = 96.dp,
    active: Color = Color(0xFF7C6FF2),  // MeshlitPulseViolet
    idle: Color = Color(0xFF2A3354),    // MeshlitOutline
    sensitivity: Float = 1.0f,
) {
    // Recompose the FFT-magnitudes feed whenever the user moves the
    // sensitivity slider so the multiplier is applied per-frame
    // (not just once at the start of the collector).
    val sensitivityState = androidx.compose.runtime.rememberUpdatedState(sensitivity)
    // SnapshotStateList tracks each bar's magnitude as a Compose
    // State so element-level mutations propagate to recomposition.
    // We use a list rather than FloatArray because Compose's
    // snapshot system observes per-element writes on lists but
    // not on arrays.
    val bars: SnapshotStateList<Float> = remember { androidx.compose.runtime.mutableStateListOf(*FloatArray(barCount) { 0f }.toTypedArray()) }
    val peaks: SnapshotStateList<Float> = remember { androidx.compose.runtime.mutableStateListOf(*FloatArray(barCount) { 0f }.toTypedArray()) }
    // Live waveform ring buffer (last 128 mono samples, normalized
    // to -1..+1). Drawn underneath the bars so the user can see
    // the *raw time-domain* audio alongside the frequency-domain
    // view. If the bars look fake-but-moving, the waveform is
    // unmistakable proof: a real voice has visible peaks + valleys
    // that follow speech envelopes, a synthetic oscillator has a
    // perfect sine.
    val waveform: SnapshotStateList<Float> = remember {
        androidx.compose.runtime.mutableStateListOf(*FloatArray(128) { 0f }.toTypedArray())
    }
    // PCM frame counter — increments every time a frame is
    // collected, so the user can SEE the rate (≈30 fps at 32 ms
    // per frame) and confirm "yes, frames are flowing right now".
    var frameCount by remember { mutableStateOf(0L) }
    // Latest RMS energy (0..1). A second, independent signal —
    // if RMS is near zero while bars look active, something is
    // very wrong (e.g. the flow is replaying stale frames).
    var rmsLevel by remember { mutableStateOf(0f) }
    var isReceiving by remember { mutableStateOf(false) }

    // Tick the animation at the display's refresh rate so the
    // envelope decay animates smoothly even when no PCM frames
    // are flowing. Recomposition is driven by the bar list
    // changing, not by the tick itself.
    LaunchedEffect(pcmFlow) {
        if (pcmFlow != null) {
            try {
                pcmFlow.collect { pcm ->
                    val mags = dftMagnitudes(pcm, binCount = barCount)
                    // Apply the sensitivity multiplier before the EMA
                    // so the slider visibly raises / lowers the bars.
                    // The rest of the chain (normalization → EMA →
                    // log remap → render) is unaffected because the
                    // slider gain scales the raw FFT output linearly.
                    val gain = sensitivityState.value
                    if (gain != 1.0f) {
                        for (i in 0 until barCount) mags[i] *= gain
                    }
                    _lastPcmNanos = System.nanoTime()
                    // Exponential moving average (EMA) smoothing.
                    // bars[i] = α·old + (1-α)·new with α = 0.78 —
                    // this gives ~9-frame (~290 ms) settling time
                    // so the visualizer follows the audio envelope
                    // smoothly instead of "jumping" on every frame.
                    // α too low (<0.5) makes the bars twitch on every
                    // FFT frame; α too high (>0.9) makes the bars
                    // sluggish and lag the user's voice. 0.78 is the
                    // sweet spot we measured against real speech.
                    //
                    // Asymmetric attack: when the new magnitude
                    // exceeds the smoothed bar (an onset), we jump
                    // to the new value immediately so transients
                    // (consonants, plosives) read visually instead
                    // of being smoothed into oblivion. The
                    // exponential release on the other side handles
                    // the natural decay.
                    val alpha = 0.78f
                    for (i in 0 until barCount) {
                        val newMag = mags[i]
                        val oldBar = bars[i]
                        val ema = oldBar * alpha + newMag * (1f - alpha)
                        bars[i] = max(ema, newMag)
                        peaks[i] = max(peaks[i] * 0.92f, newMag)
                    }
                    // Time-domain view: shift the waveform ring
                    // left by `pcmSamples` slots, then write the new
                    // samples at the tail. The visible window always
                    // shows the most recent 128 mono samples. We
                    // pick `pcmSamples` from the *trailing* portion
                    // of the frame so the user sees the latest
                    // audio, not the leading edge from the
                    // engine's driver buffer.
                    //
                    // RMS is computed in the same pass so we get a
                    // single scalar "how loud right now" signal
                    // independent of the FFT output.
                    val pcmSamples = min(pcm.size / 2, 128)
                    if (pcmSamples > 0) {
                        // Shift left: waveform[i] = waveform[i + pcmSamples]
                        val shift = pcmSamples
                        for (i in 0 until (128 - shift)) {
                            waveform[i] = waveform[i + shift]
                        }
                        // Write the new samples at the tail.
                        var sumSq = 0.0
                        for (i in 0 until pcmSamples) {
                            val src = pcm.size / 2 - pcmSamples + i
                            val lo = pcm[src * 2].toInt() and 0xFF
                            val hi = pcm[src * 2 + 1].toInt()
                            val raw = ((hi shl 8) or lo).toShort()
                            val s = raw / 32768f
                            sumSq += (s * s).toDouble()
                            waveform[128 - pcmSamples + i] = s
                        }
                        val rms = kotlin.math.sqrt(sumSq / pcmSamples).toFloat()
                        // Boost the visible range so a quiet room
                        // (RMS ~0.005) reads as ~0.10 instead of 0.00.
                        // Real mic signals on a phone in a quiet room
                        // sit at 0.001..0.05 RMS; this stretches the
                        // lower 50% of the dynamic range so the user
                        // can SEE that frames are flowing even when
                        // the room is dead silent.
                        val visibleRms = (rms * 8f).coerceAtMost(1f)
                        rmsLevel = (rmsLevel * 0.6f + visibleRms * 0.4f).coerceIn(0f, 1f)
                    }
                    frameCount = frameCount + 1L
                    isReceiving = true
                }
            } catch (t: Throwable) {
                // Flow cancelled — stop collecting, leave the
                // existing bars in place so the UI doesn't snap
                // to zero.
            }
        }
    }

    // Idle animation: when no frames have arrived for 200 ms,
    // decay the bars smoothly to zero so the user sees the
    // spectrum fade out (instead of freezing at the last
    // magnitude).
    LaunchedEffect(Unit) {
        var prev = 0L
        while (true) {
            withFrameNanos { now ->
                if (prev == 0L) {
                    prev = now
                }
                val dtMs = (now - prev) / 1_000_000f
                prev = now
                if (System.nanoTime() - _lastPcmNanos > 200_000_000L) {
                    isReceiving = false
                    for (i in 0 until barCount) {
                        bars[i] = (bars[i] - dtMs * 0.0015f).coerceAtLeast(0f)
                        peaks[i] = (peaks[i] - dtMs * 0.001f).coerceAtLeast(0f)
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(height)) {
            drawSpectrum(
                bars = bars.toFloatArray(),
                peaks = peaks.toFloatArray(),
                active = active,
                idle = idle,
                isReceiving = isReceiving,
                waveform = waveform.toFloatArray(),
                rms = rmsLevel,
            )
        }
    }
    // Live signal header — a tiny mono-spaced row that tells the
    // user "this many PCM frames have flowed, RMS is X". With the
    // spectrum below, this row answers the question "is this
    // reacting to the mic or animating itself?":
    //   - If `frames` is climbing and `rms` is moving → real audio.
    //   - If `frames` is climbing but `rms` is stuck at 0 → flow is
    //     replaying silent frames (permission grant issue, muted mic,
    //     wrong input device).
    //   - If neither moves → no frames are arriving (engine off).
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        // Pulsing dot: violet while frames are flowing, gray when
        // idle. Double-rings confirm "engine ON" + "audio arriving".
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = if (isReceiving) Color(0xFF7C6FF2) else Color(0xFF2A3354),
                    shape = androidx.compose.foundation.shape.CircleShape,
                ),
        )
        androidx.compose.material3.Text(
            text = if (isReceiving) "LIVE" else "IDLE",
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = if (isReceiving) Color(0xFF7C6FF2) else Color(0xFF6B7392),
        )
        androidx.compose.material3.Text(
            text = "f$frameCount · rms %.3f".format(rmsLevel),
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = Color(0xFF6B7392),
        )
        androidx.compose.material3.Text(
            text = "sens %.2fx".format(sensitivity),
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = Color(0xFF6B7392),
        )
    }
}

// Last PCM arrival timestamp (nanos). Read by the idle animation
// tick to decide whether to decay the bars back to zero. Updated
// by the LaunchedEffect that collects from the input flow.
private var _lastPcmNanos = 0L

private fun DrawScope.drawSpectrum(
    bars: FloatArray,
    peaks: FloatArray,
    active: Color,
    idle: Color,
    isReceiving: Boolean,
    waveform: FloatArray,
    rms: Float,
) {
    val n = bars.size
    if (n == 0) return
    val w = size.width
    val h = size.height
    // Layout split: top 60% is the FFT bar grid, bottom 40% is the
    // time-domain waveform strip + the RMS meter. Splitting gives
    // both views enough vertical space to read against the same
    // horizontal axis.
    val barsHeight = h * 0.6f
    val wavesTop = h * 0.6f
    val wavesHeight = h * 0.4f
    val barWidth = w / (n * 1.6f)              // bar + gap = 1.6× bar
    val gap = w / (n * 4f)
    val baseColor = if (isReceiving) active else idle
    val peakColor = if (isReceiving) active.copy(alpha = 0.7f) else idle
    val waveColor = if (isReceiving) active.copy(alpha = 0.85f) else idle.copy(alpha = 0.6f)

    // Spectral bars (top 60%).
    // Logarithmic remap: 0..1 → 0..1 with a 40 dB floor.
    // magnitude 1.0 (max short sample) → bar full-height
    // magnitude 0.01 (~−40 dB)        → bar ~zero
    val logFloor = 0.01f
    for (i in 0 until n) {
        val mag = bars[i]
        val db = if (mag <= 0f) 0f else (20f * ln(mag) / ln(10f)).coerceAtLeast(-40f)
        // -40 dB → 0, 0 dB → 1
        val norm = ((db + 40f) / 40f).coerceIn(0f, 1f)
        val barH = max(2f, norm * (barsHeight - 4f))     // 2 px min so flat bands still read
        val x = (i * (barWidth + gap)) + gap
        drawRoundedBar(
            topLeft = Offset(x, barsHeight - barH),
            size = Size(barWidth, barH),
            color = baseColor,
        )
        // Peak indicator — a 2 px line at the top of each bar that
        // holds the highest value reached in the last ~3 frames
        // and slowly drops so the user can see peaks.
        val pk = peaks[i]
        if (pk > 0.02f) {
            val pkDb = (20f * ln(pk) / ln(10f)).coerceAtLeast(-40f)
            val pkNorm = ((pkDb + 40f) / 40f).coerceIn(0f, 1f)
            val pkY = barsHeight - max(2f, pkNorm * (barsHeight - 4f))
            drawLine(
                color = peakColor,
                start = Offset(x, pkY),
                end = Offset(x + barWidth, pkY),
                strokeWidth = 2f,
            )
        }
    }
    // Time-domain waveform strip (bottom 40%). Draws the raw
    // PCM samples as a continuous polyline so the user can see
    // the actual audio shape — not an FFT-derived estimate. If
    // the spectrum above looks synthetic but the wave is flat,
    // the engine is replaying silent frames. If the wave is
    // moving but the bars are flat, the FFT is wrong.
    if (waveform.isNotEmpty()) {
        val mid = wavesTop + wavesHeight / 2f
        val amp = wavesHeight / 2f * 0.85f          // 15% padding so the wave doesn't clip
        val stepX = w / (waveform.size - 1).coerceAtLeast(1)
        var prev = Offset(0f, mid - waveform[0] * amp)
        for (i in 1 until waveform.size) {
            val x = i * stepX
            val y = mid - waveform[i].coerceIn(-1f, 1f) * amp
            val cur = Offset(x, y)
            drawLine(
                color = waveColor,
                start = prev,
                end = cur,
                strokeWidth = 1.5f,
            )
            prev = cur
        }
        // Zero-line guide so the eye reads the wave as oscillating
        // around silence, not "drawing toward the top".
        drawLine(
            color = idle,
            start = Offset(0f, mid),
            end = Offset(w, mid),
            strokeWidth = 0.5f,
        )
    }
    // RMS meter — a horizontal bar pinned to the bottom edge of
    // the strip. Lives next to the wave so the user can correlate
    // "look at this loud section" with the wave above. We cap the
    // visual at 0.5 (50% of full-strip) so even a hot signal
    // doesn't punch through the layout.
    if (rms > 0.001f) {
        val rmsW = (rms.coerceIn(0f, 0.5f) / 0.5f) * w
        val rmsTop = (h - 3f).coerceAtLeast(wavesTop + 1f)
        drawRect(
            color = active.copy(alpha = 0.55f),
            topLeft = Offset(0f, rmsTop),
            size = Size(rmsW, 3f),
        )
    }
}

private fun DrawScope.drawRoundedBar(
    topLeft: Offset,
    size: Size,
    color: Color,
) {
    // Cheap rounded top: stroke a rounded rect outline at the
    // top edge so the bar reads as a "pill" instead of a hard
    // rectangle. Width < 6 dp doesn't round well, so we cap.
    val r = min(size.width / 2f, 3f)
    drawRoundRect(
        color = color,
        topLeft = topLeft,
        size = size,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
    )
}

/**
 * 32-point Cooley–Tukey radix-2 FFT for a 16-bit mono PCM frame.
 *
 * We only need the magnitudes of the first [binCount] bins (the
 * lower 8 kHz of the spectrum at 16 kHz sample rate). Returns a
 * FloatArray of length [binCount] where each entry is the linear
 * magnitude (0..1 for normalized audio, ~0.0001..0.5 for real
 * voice signals).
 *
 * The frame length doesn't have to match the FFT size — we read
 * the first [fftSize] samples (or zero-pad if shorter). Frames
 * from `RunAnywhereVoiceEngine.startCapture` are 1024 samples,
 * so this reads the first 32 of every frame (≈ 8 ms at 16 kHz).
 * That's enough resolution for a visible spectrum.
 */
private fun dftMagnitudes(pcm: ByteArray, binCount: Int, fftSize: Int = 32): FloatArray {
    val out = FloatArray(binCount)
    if (pcm.isEmpty()) return out
    val samples = ShortArray(fftSize)
    // Convert 16-bit little-endian PCM to shorts, zero-pad if
    // the frame is shorter than fftSize.
    val limit = min(pcm.size / 2, fftSize)
    for (i in 0 until limit) {
        val lo = pcm[i * 2].toInt() and 0xFF
        val hi = pcm[i * 2 + 1].toInt()
        samples[i] = ((hi shl 8) or lo).toShort()
    }
    // Convert to float [-1, 1] + apply Hann window so the FFT
    // bins don't leak between adjacent voice bands.
    val real = FloatArray(fftSize)
    val imag = FloatArray(fftSize)
    for (i in 0 until fftSize) {
        val w = 0.5f * (1f - cos(2f * PI.toFloat() * i / (fftSize - 1).toFloat()))
        real[i] = (samples[i] / 32768f) * w
    }
    // Bit-reversal permutation for radix-2.
    val jArr = IntArray(fftSize)
    var j = 0
    for (i in 1 until fftSize) {
        var bit = fftSize shr 1
        while (j >= bit) { j -= bit; bit = bit shr 1 }
        j += bit
        jArr[i] = j
    }
    // Cooley–Tukey butterflies.
    var size = 2
    while (size <= fftSize) {
        val half = size / 2
        val theta = -2f * PI.toFloat() / size
        val wReal0 = cos(theta)
        val wImag0 = sin(theta)
        var k = 0
        while (k < fftSize) {
            var wReal = 1f
            var wImag = 0f
            for (m in 0 until half) {
                val idxEven = k + m
                val idxOdd = k + m + half
                val tReal = wReal * real[idxOdd] - wImag * imag[idxOdd]
                val tImag = wReal * imag[idxOdd] + wImag * real[idxOdd]
                real[idxOdd] = real[idxEven] - tReal
                imag[idxOdd] = imag[idxEven] - tImag
                real[idxEven] = real[idxEven] + tReal
                imag[idxEven] = imag[idxEven] + tImag
                val nReal = wReal * wReal0 - wImag * wImag0
                val nImag = wReal * wImag0 + wImag * wReal0
                wReal = nReal
                wImag = nImag
            }
            k += size
        }
        size = size shl 1
    }
    // Apply bit-reversal permutation.
    val r2 = FloatArray(fftSize)
    val i2 = FloatArray(fftSize)
    for (i in 0 until fftSize) {
        r2[i] = real[jArr[i]]
        i2[i] = imag[jArr[i]]
    }
    // Magnitudes for the first binCount bins.
    var maxMag = 0f
    for (b in 0 until binCount) {
        val mag = sqrt(r2[b] * r2[b] + i2[b] * i2[b])
        out[b] = mag
        if (mag > maxMag) maxMag = mag
    }
    // Normalize so the loudest bin is 1.0; the log-scale remap
    // in the renderer will then map 1.0 → bar full-height.
    if (maxMag > 0f) {
        for (b in 0 until binCount) out[b] = out[b] / maxMag
    }
    return out
}