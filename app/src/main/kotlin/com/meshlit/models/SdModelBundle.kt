package com.meshlit.models

/**
 * Phase 4.x — Stable Diffusion bundle registry.
 *
 * An SD pipeline isn't a single file — sd.cpp needs a UNet, a
 * CLIP text encoder, optionally a VAE, optionally a TAESD
 * (tiny autoencoder for cheap previews). Users picking
 * "DreamShaper 8 Q4_0" really want *all four* files for that
 * pipeline downloaded as a unit.
 *
 * The bundle registry maps a user-facing bundle id to its
 * constituent members and resolves each member's URL via the
 * [ModelCatalog] entry it points at. The
 * [com.meshlit.imagegen.SdImportController] iterates members
 * in declared order, downloads each one, and writes the
 * resulting paths back into `imageGenSd*PathFlow` settings so
 * the bridge can load the pipeline.
 *
 * Roles (the four slots `sd.cpp` exposes):
 *  - `unet` — the diffusion model itself. Always required.
 *  - `text_encoder` — CLIP-L (SD 1.5) or CLIP-L + CLIP-G
 *    (SDXL dual-encoder). Always required.
 *  - `vae` — full Variational AutoEncoder for the final decode
 *    step. Optional when `taesd` is provided.
 *  - `taesd` — Tiny AutoEncoder for Stable Diffusion, used by
 *    sd.cpp for cheap intermediate previews. Optional.
 *
 * Naming convention: `<family>-<quantization>-bundle`, e.g.
 * `sd-1.5-q4_0-bundle`. The id is the user-visible string in
 * the picker and the directory name under
 * `filesDir/imported-models/<id>/`.
 */
data class BundleMember(
    /** [ModelCatalog.Entry.id] this member resolves to. */
    val entryId: String,
    /** Slot in the sd.cpp pipeline: "unet" / "text_encoder" /
     *  "vae" / "taesd". Used by the controller to write the
     *  correct settings key on success. */
    val role: String,
    /** If false and the download fails, the bundle import
     *  continues (logged + reported in
     *  [com.meshlit.imagegen.FileSet.skippedMembers]). If true
     *  and the download fails, the whole bundle fails. */
    val required: Boolean,
)

object SdModelBundles {

    /**
     * Bundle id → ordered member list. Order is the download
     * order. UNet comes first because it's the heaviest file;
     * text encoder second; TAESD last because it's tiny and
     * optional.
     */
    val all: Map<String, List<BundleMember>> = mapOf(
        // ── SD 1.5 / DreamShaper Q4_0 (default) ───────────────
        // Smallest working bundle — UNet + CLIP-L + TAESD.
        // ~2.0 GB total. Recommended entry point for users on
        // mid-range phones.
        "sd-1.5-q4_0-bundle" to listOf(
            BundleMember(
                entryId = "dreamshaper-8-q4_0-gguf",
                role = "unet",
                required = true,
            ),
            BundleMember(
                entryId = "clip-vit-l-14-safetensors",
                role = "text_encoder",
                required = true,
            ),
            BundleMember(
                entryId = "privatelm-taesd",
                role = "taesd",
                required = false,
            ),
        ),

        // ── SD 1.5 / DreamShaper Q8_0 (higher quality) ───────
        // ~3.6 GB. UNet is Q8_0 (closer to FP16 fidelity);
        // adds CLIP-L for prompt understanding.
        "sd-1.5-q8_0-bundle" to listOf(
            BundleMember(
                entryId = "dreamshaper-8-q8_0-gguf",
                role = "unet",
                required = true,
            ),
            BundleMember(
                entryId = "clip-vit-l-14-safetensors",
                role = "text_encoder",
                required = true,
            ),
            BundleMember(
                entryId = "privatelm-taesd",
                role = "taesd",
                required = false,
            ),
        ),

        // ── SDXL Q4_0 ─────────────────────────────────────────
        // ~2.7 GB. SDXL's base 1024×1024 generation. Single
        // CLIP-L is enough for sd.cpp Q4_0 (sd.cpp splits the
        // UNet to accept either encoder).
        "sdxl-q4_0-bundle" to listOf(
            BundleMember(
                entryId = "sdxl-base-1.0-q4_0-gguf",
                role = "unet",
                required = true,
            ),
            BundleMember(
                entryId = "clip-vit-l-14-safetensors",
                role = "text_encoder",
                required = true,
            ),
            BundleMember(
                entryId = "privatelm-taesd",
                role = "taesd",
                required = false,
            ),
        ),

        // ── SDXL Q8_0 (full SDXL fidelity) ────────────────────
        // ~6.4 GB. Adds CLIP-G as a second text encoder for the
        // full SDXL prompt-understanding stack. Heavy on RAM
        // — only recommended for 12 GB+ devices.
        "sdxl-q8_0-bundle" to listOf(
            BundleMember(
                entryId = "sdxl-base-1.0-q8_0-gguf",
                role = "unet",
                required = true,
            ),
            BundleMember(
                entryId = "clip-vit-l-14-safetensors",
                role = "text_encoder",
                required = true,
            ),
            BundleMember(
                entryId = "clip-vit-g-14-fp16-safetensors",
                role = "text_encoder",
                required = false,
            ),
            BundleMember(
                entryId = "privatelm-taesd",
                role = "taesd",
                required = false,
            ),
        ),

        // ── ONNX SD 1.5 (ONNX Runtime Mobile engine) ──────────
        // Single-file bundle. The `.onnx` already contains the
        // fused UNet + text encoder + VAE pipeline; no
        // additional downloads needed.
        "onnx-sd-1.5-bundle" to listOf(
            BundleMember(
                entryId = "stable-diffusion-v1-5-onnx",
                role = "unet",
                required = true,
            ),
        ),

        // ── ExecuTorch SDXL-Turbo (Phase 2 placeholder) ───────
        // Registered so the bundle picker shows the option;
        // the download will 404 until Meta publishes a stable
        // ExecuTorch SD export. Surfacing it now means the UI
        // shape doesn't have to change in Phase 2.
        "executorch-sdxl-turbo-bundle" to listOf(
            BundleMember(
                entryId = "sdxl-turbo-executorch-stub",
                role = "unet",
                required = true,
            ),
        ),
    )

    /**
     * Per-member id → lightweight view of the catalog entry.
     * The controller reads `id`, `displayName`, `url`, and
     * `approxSizeMb` (converted to bytes) without pulling in
     * the rest of the catalog surface.
     *
     * `null` for a member means the catalog doesn't have the
     * entry — controller surfaces this as `sd.entry_missing`.
     */
    val allCatalog: Map<String, BundleEntryView> = run {
        val byId = ModelCatalog.all.associateBy { it.id }
        all.values
            .flatten()
            .mapNotNull { m ->
                byId[m.entryId]?.let { entry ->
                    m.entryId to BundleEntryView(
                        id = entry.id,
                        displayName = entry.displayName,
                        url = entry.url,
                        approxSizeBytes = entry.approxSizeMb * 1_000_000L,
                    )
                }
            }
            .toMap()
    }

    /** Friendly labels for the bundle picker UI. */
    val displayNames: Map<String, String> = mapOf(
        "sd-1.5-q4_0-bundle" to "SD 1.5 (DreamShaper 8) · Q4_0 — 2.0 GB",
        "sd-1.5-q8_0-bundle" to "SD 1.5 (DreamShaper 8) · Q8_0 — 3.6 GB",
        "sdxl-q4_0-bundle" to "SDXL Base 1.0 · Q4_0 — 2.7 GB",
        "sdxl-q8_0-bundle" to "SDXL Base 1.0 · Q8_0 — 6.4 GB",
        "onnx-sd-1.5-bundle" to "SD 1.5 · ONNX — 5.2 GB",
        "executorch-sdxl-turbo-bundle" to "SDXL-Turbo · ExecuTorch (Phase 2)",
    )

    /** Ordering for the picker UI (cheapest first). */
    val pickerOrder: List<String> = listOf(
        "sd-1.5-q4_0-bundle",
        "sd-1.5-q8_0-bundle",
        "sdxl-q4_0-bundle",
        "sdxl-q8_0-bundle",
        "onnx-sd-1.5-bundle",
        "executorch-sdxl-turbo-bundle",
    )
}

/** Compact view of a catalog entry for the controller. */
data class BundleEntryView(
    val id: String,
    val displayName: String,
    val url: String,
    val approxSizeBytes: Long,
)