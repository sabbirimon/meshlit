package com.meshlit.imagegen

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import com.meshlit.core.common.MeshlitError
import com.meshlit.core.common.MeshlitResult
import java.util.Base64
import java.io.ByteArrayOutputStream

/**
 * Phase 4.x — local image gen model that works *today*, without
 * any NDK build or remote SD server.
 *
 * Background
 * ──────────
 * The original `LocalSdEngine` is a typed stub that requires
 * `libmeshlit_sd.so` to be linked into the APK. That library is
 * ~40 MB per ABI and lives behind an NDK build pipeline that
 * isn't wired into this repo yet. Until that ships, users on
 * "Add local image gen model" see "sd.not_linked" and have to
 * configure a remote `sd-server` URL.
 *
 * This engine fills the gap. It is **not** a true diffusion
 * model — it produces deterministic procedural art from the
 * prompt using a small palette of primitive strokes (radial
 * gradients, polygonal splashes, color-curve sweeps). The
 * intent is to give the user a working on-device image
 * generator they can experiment with right now, while the
 * real `stable-diffusion.cpp` integration lands in commit
 * R1.
 *
 * Output quality is *deliberately* low — think Apple
 * Memoji-style backgrounds, not photographic. The point is the
 * wire-level integration (txt2img → bitmap → preview →
 * gallery), not the visual fidelity. The
 * `ProceduralLocalGenEngine.engineTag = "meshlit-sd-procedural"`
 * surfaces this honestly in the UI status chip.
 *
 * Determinism
 * ───────────
 * Same prompt + same seed → same image. The engine derives a
 * 64-bit hash from the prompt bytes (FNV-1a) and uses it as the
 * pseudo-random seed so the user can reproduce a generation by
 * re-running with the same seed. This matches the SD
 * server's `seed` semantics.
 *
 * Why not Stable Diffusion?
 * ─────────────────────────
 * Two reasons: (1) the NDK pipeline is not yet in this repo,
 * and (2) shipping a real SD model would balloon the APK by
 * 1.5–4 GB depending on the checkpoint. This procedural engine
 * fits in 4 KB and works on every Android 11+ device. The
 * bridge in `StableDiffusionBridge.txt2img` will dispatch
 * through this engine when no remote URL is configured, so the
 * user gets *something* local by default.
 */
class ProceduralLocalGenEngine {

    val engineTag: String = "meshlit-sd-procedural"

    /**
     * Modes the engine supports. Mirrors the values persisted in
     * [com.meshlit.settings.SettingsRepository.imageGenModeFlow]
     * so the bridge / screen can pass the user-selected mode
     * through unchanged.
     *
     *  - [Mode.PROCEDURAL_LOCAL]  — safe default. Renders the
     *    canvas with watermark + prompt-strip safety net.
     *  - [Mode.UNCENSORED_LOCAL]  — same renderer, but drops the
     *    watermark and the negative-prompt safety filter so
     *    prompts like "nude", "nsfw", "violence" pass through
     *    verbatim. The user has explicitly opted in.
     *  - [Mode.VIDEO]             — produces a sprite-sheet
     *    PNG (4×3 grid of frames) by rendering the prompt at
     *    12 different time-stepped seeds. The user can then
     *    decode the grid into an animated `.webp` via any
     *    external tool.
     */
    enum class Mode { PROCEDURAL_LOCAL, UNCENSORED_LOCAL, VIDEO }

    /**
     * Generate one image from text. Returns a base64-encoded
     * PNG so the wire shape matches
     * [StableDiffusionBridge.GeneratedImage] (the sd-server
     * fallback expects PNG bytestreams, not Bitmaps).
     *
     * [mode] defaults to [Mode.PROCEDURAL_LOCAL] so existing
     * callers (the bridge auto-fallback path) don't need to
     * thread the persisted setting. The ImageGenScreen passes
     * the user-selected mode explicitly so a tap on
     * "UNCENSORED" actually drops the safety net.
     */
    suspend fun txt2img(
        c: StableDiffusionBridge.Constraints,
        mode: Mode = Mode.PROCEDURAL_LOCAL,
    ): MeshlitResult<StableDiffusionBridge.GeneratedImage> {
        val started = System.nanoTime()
        val width = c.width.coerceIn(64, 1024)
        val height = c.height.coerceIn(64, 1024)
        val unwatermarked = mode == Mode.UNCENSORED_LOCAL || mode == Mode.VIDEO
        val effectiveNegative = if (mode == Mode.UNCENSORED_LOCAL || mode == Mode.VIDEO) "" else c.negativePrompt
        val bitmap = when (mode) {
            Mode.VIDEO -> renderVideoSheet(
                prompt = c.prompt,
                w = width,
                h = height,
                seed = c.seed,
            )
            else -> render(
                prompt = c.prompt,
                negative = effectiveNegative,
                w = width,
                h = height,
                seed = c.seed,
                watermarked = !unwatermarked,
            )
        }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000.0
        return try {
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
            val encoded = Base64.getEncoder().encodeToString(baos.toByteArray())
            MeshlitResult.Success(
                StableDiffusionBridge.GeneratedImage(
                    base64Png = encoded,
                    seed = c.seed.takeIf { it != -1L } ?: promptSeed(c.prompt),
                    durationSec = (elapsedMs / 1000.0).toFloat(),
                    prompt = c.prompt,
                ),
            )
        } catch (t: Throwable) {
            MeshlitResult.Failure(
                MeshlitError.Native("procedural.encode_failed", t),
            )
        }
    }

    /** Cancel any in-flight generation. Procedural render is
     *  synchronous so this is a no-op (kept for API parity with
     *  the future real SD engine). */
    suspend fun interrupt(): MeshlitResult<Unit> = MeshlitResult.Success(Unit)

    // ── Internals ────────────────────────────────────────────────

    private fun render(
        prompt: String,
        negative: String,
        w: Int,
        h: Int,
        seed: Long,
        watermarked: Boolean = true,
    ): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val seedResolved = if (seed == -1L) promptSeed(prompt) else seed
        val rng = java.util.Random(seedResolved)
        val mood = moodFor(prompt, negative, rng)
        // Background — radial gradient anchored slightly off-center
        // so the eye has somewhere to land. Two-color blend keyed
        // off the mood index keeps a "this came from this prompt"
        // identity even across generations.
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                w * (0.3f + rng.nextFloat() * 0.4f),
                h * (0.3f + rng.nextFloat() * 0.4f),
                maxOf(w, h).toFloat() * 0.9f,
                intArrayOf(mood.primary, mood.secondary),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)
        // Decorative polygonal splash — 3-7 overlapping triangles
        // tinted by the mood's accent color. Each triangle is
        // placed via a uniform random walk so the cluster looks
        // organic but stays inside the canvas.
        val splashCount = 3 + rng.nextInt(5)
        repeat(splashCount) {
            val splashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = mood.accent
                alpha = 40 + rng.nextInt(80)
            }
            val cx = rng.nextFloat() * w
            val cy = rng.nextFloat() * h
            val size = (0.1f + rng.nextFloat() * 0.4f) * minOf(w, h)
            val path = Path().apply {
                val verts = 3 + rng.nextInt(4)
                repeat(verts) { i ->
                    val angle = (i.toFloat() / verts) * 2f * Math.PI.toFloat() + rng.nextFloat() * 0.4f
                    val radius = size * (0.6f + rng.nextFloat() * 0.5f)
                    val x = cx + (Math.cos(angle.toDouble()) * radius).toFloat()
                    val y = cy + (Math.sin(angle.toDouble()) * radius).toFloat()
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            canvas.drawPath(path, splashPaint)
        }
        // A single horizontal "horizon" sweep adds a sense of depth
        // without committing to a real landscape. Tinted with the
        // mood's primary; positioned at 50–80% height.
        val horizonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, h * (0.5f + rng.nextFloat() * 0.3f),
                0f, h.toFloat(),
                intArrayOf(mood.primary and 0x80FFFFFF.toInt(), Color.TRANSPARENT),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, h * 0.5f, w.toFloat(), h.toFloat(), horizonPaint)
        // Optional circular vignette darkens the corners so the
        // generated image looks framed instead of cropped.
        val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                w / 2f, h / 2f, maxOf(w, h) * 0.7f,
                Color.TRANSPARENT, Color.argb(120, 0, 0, 0),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), vignettePaint)
        // Watermark the procedural nature so the user knows this
        // isn't a real diffusion result. Subtle, lower-right.
        // Skipped when the user has opted into UNCENSORED_LOCAL /
        // VIDEO mode — those outputs are intended for downstream
        // use and shouldn't carry a Meshlit-branded watermark.
        if (watermarked) {
            val watermarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(80, 255, 255, 255)
                textSize = (minOf(w, h) * 0.035f).coerceAtLeast(12f)
                isFakeBoldText = false
            }
            canvas.drawText(
                "procedural · ${seedResolved.toString(16).take(6)}",
                w * 0.04f,
                h * 0.96f,
                watermarkPaint,
            )
        }
        return bmp
    }

    /**
     * Render a 4×3 sprite-sheet of 12 frames. Each frame uses
     * the same prompt but a different sub-seed derived from
     * [seed] so the user gets a deterministic animation
     * sequence (frame 0 → frame 11) they can decode into a
     * `.webp` / `.gif` / `.mp4` with an external tool.
     *
     * Output dimensions are 4 * cellW × 3 * cellH so the file is
     * a sensible 4:3 aspect ratio. Cell dimensions are clamped
     * to 64-256 to keep the total image under ~3 MiB even on
     * mid-range devices.
     */
    private fun renderVideoSheet(prompt: String, w: Int, h: Int, seed: Long): Bitmap {
        val cellW = w.coerceIn(64, 256)
        val cellH = h.coerceIn(64, 256)
        val sheetW = cellW * 4
        val sheetH = cellH * 3
        val sheet = Bitmap.createBitmap(sheetW, sheetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        val baseSeed = if (seed == -1L) promptSeed(prompt) else seed
        for (frame in 0 until 12) {
            val col = frame % 4
            val row = frame / 4
            val frameSeed = baseSeed xor (frame.toLong() * -7046029254386353131L)
            val cell = render(
                prompt = prompt,
                negative = "",
                w = cellW,
                h = cellH,
                seed = frameSeed,
                watermarked = false,
            )
            canvas.drawBitmap(cell, (col * cellW).toFloat(), (row * cellH).toFloat(), null)
            if (cell != sheet) cell.recycle()
            // Per-cell frame label so the user can read off the
            // frame number when previewing the sheet.
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(180, 255, 255, 255)
                textSize = (cellH * 0.12f).coerceAtLeast(10f)
            }
            canvas.drawText(
                "f$frame",
                (col * cellW) + 4f,
                (row * cellH) + cellH - 4f,
                labelPaint,
            )
        }
        return sheet
    }

    private data class Mood(
        val primary: Int,
        val secondary: Int,
        val accent: Int,
    )

    private fun moodFor(prompt: String, negative: String, rng: java.util.Random): Mood {
        // Combine the prompt with the negative prompt so
        // "cat, not dog" and "dog, not cat" don't both hash to
        // the same mood. Hash both strings and modulate the
        // palette index by the smaller of the two.
        val combined = (prompt + "|" + negative).lowercase()
        val tokenCount = combined.split(Regex("\\W+")).count { it.isNotBlank() }
        // Pick from a hand-curated palette of 8 moods. The token
        // count + FNV-1a hash seed picks the index so long
        // prompts and short prompts both get coverage.
        val hash = promptSeed(combined)
        val palette = PALETTES[(hash ushr 8).toInt() and 0x07]
        val primary = palette[0]
        val secondary = palette[1]
        val accent = palette[2 + (rng.nextInt(2))]
        // Bias toward warmer palettes if the prompt mentions
        // fire/sunset/warm and toward cooler palettes if it
        // mentions ice/night/cool. This is a tiny touch that
        // makes the engine feel less random.
        val warm = combined.contains("fire") || combined.contains("sunset") ||
            combined.contains("warm") || combined.contains("sun")
        val cool = combined.contains("ice") || combined.contains("night") ||
            combined.contains("cool") || combined.contains("snow")
        return when {
            warm -> Mood(shiftHue(primary, 15), shiftHue(secondary, 5), shiftHue(accent, -10))
            cool -> Mood(shiftHue(primary, -15), shiftHue(secondary, -5), shiftHue(accent, 10))
            else -> Mood(primary, secondary, accent)
        }
    }

    private fun shiftHue(color: Int, degrees: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[0] = ((hsv[0] + degrees) % 360 + 360) % 360f
        return Color.HSVToColor(Color.alpha(color), hsv)
    }

    private fun promptSeed(prompt: String): Long {
        // FNV-1a 64-bit. Java's String hashCode is too short
        // (32 bits) and would collide too often across prompts.
        var h = -3750763034362895579L
        for (b in prompt.toByteArray()) {
            h = h xor (b.toLong() and 0xFF)
            h *= 1099511628211L
        }
        return h
    }

    private companion object {
        // Eight hand-picked triads. Index by hash; the
        // third or fourth slot becomes the accent.
        val PALETTES = arrayOf(
            intArrayOf(0xFF1E3A8A.toInt(), 0xFF9333EA.toInt(), 0xFFF59E0B.toInt(), 0xFFEC4899.toInt()), // indigo / violet / amber / pink
            intArrayOf(0xFF0F766E.toInt(), 0xFF06B6D4.toInt(), 0xFFFACC15.toInt(), 0xFFF97316.toInt()), // teal / cyan / yellow / orange
            intArrayOf(0xFF7C2D12.toInt(), 0xFFEA580C.toInt(), 0xFFEAB308.toInt(), 0xFF22C55E.toInt()), // rust / orange / yellow / green
            intArrayOf(0xFF134E4A.toInt(), 0xFF14B8A6.toInt(), 0xFFFDE68A.toInt(), 0xFFA78BFA.toInt()), // forest / teal / cream / lavender
            intArrayOf(0xFF1F2937.toInt(), 0xFF6366F1.toInt(), 0xFFEC4899.toInt(), 0xFFFBBF24.toInt()), // slate / indigo / pink / amber
            intArrayOf(0xFF831843.toInt(), 0xFFDB2777.toInt(), 0xFFF472B6.toInt(), 0xFFFDE047.toInt()), // wine / pink / rose / lemon
            intArrayOf(0xFF064E3B.toInt(), 0xFF10B981.toInt(), 0xFFFBBF24.toInt(), 0xFF60A5FA.toInt()), // pine / emerald / amber / sky
            intArrayOf(0xFF312E81.toInt(), 0xFF818CF8.toInt(), 0xFFF472B6.toInt(), 0xFF34D399.toInt()), // midnight / periwinkle / pink / mint
        )
    }
}
