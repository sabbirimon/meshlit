package com.meshlit.feature.advanced

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.ui.graphics.vector.ImageVector
import com.meshlit.core.advanced.engines.EngineCategory

/**
 * Routes reachable from the Advanced hub. Each entry maps a user-
 * visible label + icon to its category (used to filter which engines
 * show in the "Select a model" picker) and the `route` string the
 * internal NavHost will dispatch to.
 *
 * Adding a new section: add an entry here, a stub composable in
 * [AdvancedNavHost], and a card in [SpeechLabSection] /
 * [DeveloperDiagnosticsSection] / [AssistantAddonsSection].
 */
enum class AdvancedDestination(
    val route: String,
    val label: String,
    val description: String,
    val icon: ImageVector,
    val primaryCategory: EngineCategory,
) {
    Diarization(
        route = "advanced/diarization",
        label = "Diarization",
        description = "Speaker labels via NVIDIA Sortformer.",
        icon = Icons.Filled.RecordVoiceOver,
        primaryCategory = EngineCategory.DIARIZATION,
    ),
    ReadAloud(
        route = "advanced/read_aloud",
        label = "Read aloud",
        description = "Text to speech on device.",
        icon = Icons.Filled.LibraryMusic,
        primaryCategory = EngineCategory.TTS,
    ),
    Transcription(
        route = "advanced/transcription",
        label = "Transcription",
        description = "Batch, live and hybrid speech to text.",
        icon = Icons.Filled.GraphicEq,
        primaryCategory = EngineCategory.STT,
    ),
    VoiceActivity(
        route = "advanced/voice_activity",
        label = "Voice activity",
        description = "Silero VAD — when the user is speaking.",
        icon = Icons.Filled.Mic,
        primaryCategory = EngineCategory.VAD,
    ),
    WebTools(
        route = "advanced/web_tools",
        label = "Web & tools",
        description = "HTTP fetch, browsing tools, custom registries.",
        icon = Icons.Filled.Public,
        primaryCategory = EngineCategory.LLM,
    ),
    Solutions(
        route = "advanced/solutions",
        label = "Solutions",
        description = "YAML pipelines that chain engines.",
        icon = Icons.Filled.AccountTree,
        primaryCategory = EngineCategory.LLM,
    ),
    CloudProviders(
        route = "advanced/cloud_providers",
        label = "Cloud providers",
        description = "STT cloud fallback providers.",
        icon = Icons.Filled.NetworkCheck,
        primaryCategory = EngineCategory.STT,
    ),
    Benchmarks(
        route = "advanced/benchmarks",
        label = "Benchmarks",
        description = "CPU / RAM / disk / model throughput.",
        icon = Icons.Filled.Speed,
        primaryCategory = EngineCategory.LLM,
    ),
    GpuPanel(
        route = "advanced/gpu_panel",
        label = "GPU panel",
        description = "Vulkan compute + eGPU status.",
        icon = Icons.Filled.Memory,
        primaryCategory = EngineCategory.LLM,
    ),
    Settings(
        route = "advanced/settings",
        label = "Advanced settings",
        description = "Toggles, opacity, mount points.",
        icon = Icons.Filled.Tune,
        primaryCategory = EngineCategory.LLM,
    ),
    DocumentWorkbench(
        route = "advanced/document_workbench",
        label = "Document workbench",
        description = "RAG over your local files.",
        icon = Icons.Filled.Article,
        primaryCategory = EngineCategory.EMBED,
    ),
    VisionWorkbench(
        route = "advanced/vision_workbench",
        label = "Vision workbench",
        description = "VLM prompt playground.",
        icon = Icons.Filled.WorkOutline,
        primaryCategory = EngineCategory.VISION,
    ),
    DocumentOcr(
        route = "advanced/document_ocr",
        label = "Document OCR",
        description = "Nemotron OCR for scanned PDFs.",
        icon = Icons.Filled.Receipt,
        primaryCategory = EngineCategory.OCR,
    ),
    Segmentation(
        route = "advanced/segmentation",
        label = "Segmentation",
        description = "SegFormer pixel masks.",
        icon = Icons.Filled.Image,
        primaryCategory = EngineCategory.VISION,
    ),
    ImageGeneration(
        route = "advanced/image_generation",
        label = "Image generation",
        description = "Cosmos3 diffusion (stub).",
        icon = Icons.Filled.Image,
        primaryCategory = EngineCategory.IMAGE_GEN,
    ),
    GhostySettings(
        route = "advanced/ghosty",
        label = "Ghosty settings",
        description = "Floating chat overlay.",
        icon = Icons.Filled.Settings,
        primaryCategory = EngineCategory.LLM,
    ),
    McpSettings(
        route = "advanced/mcp",
        label = "MCP settings",
        description = "Embedded MCP HTTP server + user tools.",
        icon = Icons.Filled.SettingsApplications,
        primaryCategory = EngineCategory.LLM,
    ),
    Storage(
        route = "advanced/storage",
        label = "Storage",
        description = "Free space, cache, temp files.",
        icon = Icons.Filled.Storage,
        primaryCategory = EngineCategory.LLM,
    ),
}
