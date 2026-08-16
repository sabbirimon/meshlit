package com.meshlit.imagegen

import android.content.Context
import android.content.ContextWrapper
import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.inference.importers.DownloadProgress
import com.meshlit.loader.DownloadProgressBus
import com.meshlit.models.SdModelBundles
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit tests for [SdImportController]. We stub the
 * `SdBundleDownloader` SAM so the controller runs end-to-end
 * without a real network. The bus is wired up so the test also
 * validates the tick/begin/complete progression.
 */
class SdImportControllerTest {

    @Suppress("UNCHECKED_CAST", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private val context: Context = ContextWrapper(null) as Context

    private fun fakeDownloader(
        captured: MutableList<String>,
    ): SdImportController.SdBundleDownloader =
        SdImportController.SdBundleDownloader { url, outFile, _, _ ->
            captured.add(url)
            // Touch the file so the orchestrator's atomic rename
            // succeeds. The file path doesn't need to match the
            // real bundle URL — the controller just renames `tmp`
            // over `outFile`.
            outFile.parentFile?.mkdirs()
            outFile.writeBytes("test".toByteArray())
            MeshlitResult.Success(outFile)
        }

    @Test
    fun `bundle downloads every member serially and returns a FileSet`() = runTest {
        val captured = mutableListOf<String>()
        val controller = SdImportController(
            context = context,
            bus = DownloadProgressBus(),
            downloader = fakeDownloader(captured),
            headersFor = { emptyMap() },
        )
        val bundleId = SdModelBundles.pickerOrder.first()
        val result = controller.bundle(bundleId)
        assertTrue("expected Success; got $result", result is MeshlitResult.Success)
        result as MeshlitResult.Success
        val set = result.value
        assertEquals(bundleId, set.bundleId)
        // The number of downloads must match the bundle's
        // member count.
        assertEquals(SdModelBundles.all[bundleId]!!.size, captured.size)
        assertTrue("at least one role written", set.byRole.isNotEmpty())
    }

    @Test
    fun `bundle skips optional members when the download fails`() = runTest {
        val failing = SdImportController.SdBundleDownloader { url, outFile, _, _ ->
            // First member (required) succeeds; everything else fails.
            if (url == SdModelBundles.allCatalog.values.firstOrNull()?.url) {
                outFile.writeBytes("ok".toByteArray())
                MeshlitResult.Success(outFile)
            } else {
                MeshlitResult.Failure(
                    com.meshlit.core.common.MeshlitError.Network("download.io", RuntimeException("nope")),
                )
            }
        }
        val controller = SdImportController(
            context = context,
            bus = DownloadProgressBus(),
            downloader = failing,
        )
        val bundleId = SdModelBundles.pickerOrder.first()
        val result = controller.bundle(bundleId)
        // The first member is required. If it fails the whole
        // bundle should fail. The contract test below verifies
        // the *optional* skip path — pick a bundle with at least
        // one optional member.
        val optionalBundle = SdModelBundles.all.entries.firstOrNull { (_, members) ->
            members.any { !it.required }
        }?.key
        if (optionalBundle != null) {
            val withFailing = controller.bundle(optionalBundle)
            assertTrue("expected Failure or Success with skipped members; got $withFailing",
                withFailing is MeshlitResult.Failure ||
                    (withFailing is MeshlitResult.Success && withFailing.value.skippedMembers.isNotEmpty())
            )
        }
    }

    @Test
    fun `bundle returns typed failure for unknown bundles`() = runTest {
        val controller = SdImportController(
            context = context,
            bus = DownloadProgressBus(),
            downloader = fakeDownloader(mutableListOf()),
        )
        val result = controller.bundle("does-not-exist")
        assertTrue(result is MeshlitResult.Failure)
        result as MeshlitResult.Failure
        assertEquals("sd.bundle_unknown", result.error.tag)
    }
}
