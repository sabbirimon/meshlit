package com.meshlit.core.inference.net

import com.meshlit.core.inference.RuntimeRegistry
import com.meshlit.core.inference.RuntimeStatus
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 2.x — wire round-trip for the new `/v1/runtimes` response.
 * If any field name, type, or default changes here, peer devices
 * parsing the JSON will break. This test pins the shape.
 */
class RuntimesResponseRoundTripTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `RuntimesResponse round-trips with empty catalog`() {
        val original = RuntimesResponse()
        val encoded = json.encodeToString(RuntimesResponse.serializer(), original)
        val decoded = json.decodeFromString(RuntimesResponse.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `RuntimesResponse round-trips with full catalog`() {
        val descriptors = RuntimeRegistry.all.map { rt ->
            RuntimeDescriptor(
                runtimeId = rt.runtimeId,
                displayName = rt.displayName,
                status = rt.status.tag,
                supportedFormats = rt.supportedFormats.map { it.extension },
                approxApkFootprintBytes = rt.approxApkFootprintBytes,
            )
        }
        val original = RuntimesResponse(
            deviceRuntimeId = "gguf-llama.cpp",
            deviceRuntimeDisplayName = "GGUF · llama.cpp",
            runtimes = descriptors,
            summary = RuntimeCatalogSummary(
                shippedCount = 2,  // Phase 2.x: gguf + onnx
                candidateCount = 2,  // safetensors + tflite
                appleOnlyCount = 2,
            ),
        )
        val encoded = json.encodeToString(RuntimesResponse.serializer(), original)
        val decoded = json.decodeFromString(RuntimesResponse.serializer(), encoded)
        assertEquals(original, decoded)
        assertEquals(6, decoded.runtimes.size)
        assertEquals("shipped", decoded.runtimes.first { it.runtimeId == "gguf-llama.cpp" }.status)
        // Phase 2.x — ONNX Runtime is now also shipped.
        assertEquals("shipped", decoded.runtimes.first { it.runtimeId == "onnx-ort" }.status)
        assertEquals("candidate", decoded.runtimes.first { it.runtimeId == "safetensors-candle" }.status)
        assertEquals("apple_only", decoded.runtimes.first { it.runtimeId == "mlx-apple" }.status)
    }

    @Test
    fun `RuntimeDescriptor uses camelCase keys on the wire`() {
        val descriptor = RuntimeDescriptor(
            runtimeId = "test-rt",
            displayName = "Test Runtime",
            status = "shipped",
            supportedFormats = listOf("gguf", "onnx"),
            approxApkFootprintBytes = 12L * 1024L * 1024L,
        )
        val encoded = json.encodeToString(RuntimeDescriptor.serializer(), descriptor)
        assertTrue(
            "expected approxApkFootprintBytes in JSON, got: $encoded",
            encoded.contains("approxApkFootprintBytes"),
        )
        assertTrue(
            "expected supportedFormats in JSON, got: $encoded",
            encoded.contains("supportedFormats"),
        )
    }

    @Test
    fun `every registry runtime carries a wire-safe status tag`() {
        // Phase 2.x peers parse the `status` field by string equality.
        // Adding a new RuntimeStatus enum value without updating the
        // wire contract would silently fail — this test catches it.
        val wireTags = setOf("shipped", "candidate", "apple_only", "unavailable")
        RuntimeRegistry.all.forEach { rt ->
            assertTrue(
                "runtime ${rt.runtimeId} has wire-unsafe status ${rt.status.tag}",
                rt.status.tag in wireTags,
            )
            assertTrue(
                "runtime ${rt.runtimeId} status must be one of RuntimeStatus enum values",
                rt.status in RuntimeStatus.entries,
            )
        }
    }

    @Test
    fun `RuntimesResponse JSON is parseable by an external Json instance`() {
        // Sanity: peers will use a different Json instance with
        // different config (e.g. isLenient=true). Make sure our
        // output works under the default settings too.
        val resp = RuntimesResponse(
            deviceRuntimeId = null,
            runtimes = RuntimeRegistry.all.map { rt ->
                RuntimeDescriptor(
                    runtimeId = rt.runtimeId,
                    displayName = rt.displayName,
                    status = rt.status.tag,
                    supportedFormats = rt.supportedFormats.map { it.extension },
                    approxApkFootprintBytes = rt.approxApkFootprintBytes,
                )
            },
        )
        val encoded = json.encodeToString(RuntimesResponse.serializer(), resp)
        val parsed = Json.decodeFromString(RuntimesResponse.serializer(), encoded)
        assertNotNull(parsed)
        assertEquals(6, parsed.runtimes.size)
    }
}
