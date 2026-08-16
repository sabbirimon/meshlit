package com.meshlit.devices

import android.app.Activity
import android.content.Context
import com.google.android.gms.common.api.ApiException
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Google Play Services Code Scanner — QR pairing glue.
 *
 * Why Play Services Code Scanner and not CameraX + ZXing + manual
 * decode:
 *  - Google ships a **dedicated, full-screen scanner UI** that runs
 *    on a Play-Services-managed camera surface. We don't have to
 *    write a `PreviewView`, a `CameraSelector`, or any
 *    `ImageAnalysis` plumbing.
 *  - The scanner downloads its module on first launch, so the APK
 *    only carries a small stub. No `CAMERA` permission is needed in
 *    our manifest — the scanner UI is opaque from our POV.
 *  - It returns the decoded raw value as a `Task<String>`. We wrap
 *    the `Task` in a `suspendCancellableCoroutine` so callers can
 *    `await` it like any other suspending operation.
 *  - `GmsBarcodeScannerOptions` is configured with `QR_CODE` format
 *    only and `allowManualInput = true` so a user whose camera is
 *    broken can type the value into the scanner UI itself.
 *
 * Artifact: `com.google.android.gms:play-services-code-scanner:16.1.0`.
 *
 * Failure modes (surfaced as the [ScanResult] sealed class):
 *  - `PlayServicesMissing` — Play Services missing or out of date.
 *  - `Cancelled` — user backed out of the scanner UI.
 *  - `MissingActivity` — called from a non-Activity context (rare).
 *  - `Failed(code, message)` — generic error.
 */
object QrScanner {

    /**
     * Launch the system scanner UI for the current foreground activity.
     * Returns the raw decoded QR string, or a typed error.
     */
    suspend fun scan(context: Context): ScanResult {
        val activity = context.findActivity() ?: return ScanResult.MissingActivity
        return try {
            val options = GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .allowManualInput()
                .build()
            val client = GmsBarcodeScanning.getClient(activity, options)
            val raw = suspendCancellableCoroutine<String?> { cont ->
                val task = client.startScan()
                    .addOnSuccessListener { value ->
                        if (cont.isActive) cont.resume(value.rawValue)
                    }
                    .addOnFailureListener { err ->
                        if (cont.isActive) cont.resume(null)
                    }
                cont.invokeOnCancellation { /* nothing — task has no cancel */ }
            }
            if (raw.isNullOrBlank()) ScanResult.Cancelled
            else ScanResult.Success(raw.trim())
        } catch (api: ApiException) {
            when (api.statusCode) {
                18 -> ScanResult.Cancelled
                17 -> ScanResult.PlayServicesMissing
                else -> ScanResult.Failed(api.statusCode, api.message ?: "")
            }
        } catch (t: Throwable) {
            ScanResult.Failed(-1, t.message ?: t.javaClass.simpleName)
        }
    }

    sealed class ScanResult {
        data class Success(val rawValue: String) : ScanResult()
        data object Cancelled : ScanResult()
        data object PlayServicesMissing : ScanResult()
        data object MissingActivity : ScanResult()
        data class Failed(val code: Int, val message: String) : ScanResult()
    }
}

/**
 * Resolve the current foreground Activity from any [Context]. Mirrors
 * `ContextUtils.findActivity` from the now-removed
 * `androidx.activity:context` artifact so we don't add a dep.
 */
private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}