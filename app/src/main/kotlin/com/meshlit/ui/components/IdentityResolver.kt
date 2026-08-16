package com.meshlit.ui.components

import com.meshlit.BuildConfig
import com.meshlit.MeshlitApplication
import com.meshlit.core.inference.InferenceCoordinator
import com.meshlit.core.inference.ModelInfo
import com.meshlit.inference.InferenceDispatchMode

/**
 * Identity snapshot that the chat surface renders as a small
 * pill and seeds into the system prompt so the model can
 * identify itself with the right tags.
 *
 *   "Meshlit · Llama-3-8B-Instruct · Local · runanywhere · v0.2.0"
 *
 * Pieces:
 *  - [system]     — always "Meshlit" (the host application name).
 *  - [modelName]  — friendly name from the loaded [ModelInfo]
 *                   (e.g. "Llama-3-8B-Instruct"). Falls back
 *                   to the file name when no descriptor is set.
 *  - [engineTag]  — which inference engine is hosting the model
 *                   ("runanywhere" / "llama.cpp" / "onnx-ort" /
 *                   "gpu" / "none"). Sourced from
 *                   [InferenceCoordinator.engineTag].
 *  - [origin]     — Local / Remote / Cluster — the dispatch mode
 *                   the user picked on the Jobs screen.
 *  - [originPeer] — peer IP:port for REMOTE, peer nodeId for
 *                   CLUSTER, empty for LOCAL.
 *  - [appVersion] — `BuildConfig.VERSION_NAME`. The application
 *                   version tag (e.g. "0.2.0") the user sees in
 *                   the badge.
 */
data class Identity(
    val system: String,
    val modelName: String,
    val engineTag: String,
    val origin: InferenceDispatchMode,
    val originPeer: String,
    val appVersion: String,
) {
    /** Compact label used by [IdentityBadge] — keeps the
     *  toolbar line tight. Format:
     *  `Meshlit · <model> · <origin>` with the engine tag
     *  and version dropped (they live in the system prompt
     *  the model itself sees). */
    fun badgeText(): String {
        val pieces = mutableListOf(system, modelName, originTag())
        return pieces.filter { it.isNotBlank() }.joinToString(" · ")
    }

    /** Long-form label, used by the chat bubble's reply header
     *  and by the system-prompt seeding. */
    fun fullText(): String =
        listOfNotNull(
            system.takeIf { it.isNotBlank() },
            modelName.takeIf { it.isNotBlank() },
            originTag().takeIf { it.isNotBlank() },
            engineTag.takeIf { it.isNotBlank() && it != "none" },
            appVersion.takeIf { it.isNotBlank() }.let { "v$it" },
        ).joinToString(" · ")

    /** Human-friendly origin label. Cluster prepends the peer
     *  nodeId so the user can tell which phone is answering;
     *  REMOTE shows the IP:port; LOCAL stays bare. */
    fun originTag(): String = when (origin) {
        InferenceDispatchMode.LOCAL -> "Local"
        InferenceDispatchMode.REMOTE -> if (originPeer.isBlank()) "Remote" else "Remote · $originPeer"
        InferenceDispatchMode.CLUSTER -> if (originPeer.isBlank()) "Cluster" else "Cluster · $originPeer"
    }
}

/**
 * Compose-side helper that snapshots the current identity from
 * the live coordinator + dispatch mode. Re-evaluates whenever
 * the coordinator's [InferenceCoordinator.state] emits a new
 * value (so loading a new model updates the badge automatically).
 *
 * The resolver is cheap — it only reads string fields off the
 * coordinator and the dispatch state. There is no I/O.
 */
class IdentityResolver(
    private val app: MeshlitApplication,
) {
    /**
     * Build an [Identity] from the current state. [dispatchMode]
     * comes from the screen so the resolver stays decoupled from
     * any specific UI binding; [peerLabel] is the optional
     * resolved peer (REMOTE IP:port, CLUSTER nodeId) — pass an
     * empty string for LOCAL.
     */
    fun resolve(
        dispatchMode: InferenceDispatchMode,
        peerLabel: String = "",
        state: InferenceCoordinator? = app.inferenceCoordinator,
    ): Identity {
        val model = state?.loadedModel()
        val engineTag = state?.engineTag ?: "none"
        return Identity(
            system = "Meshlit",
            modelName = friendlyModelName(model, engineTag),
            engineTag = engineTag,
            origin = dispatchMode,
            originPeer = peerLabel,
            appVersion = BuildConfig.VERSION_NAME ?: "",
        )
    }

    private fun friendlyModelName(model: ModelInfo?, engineTag: String): String {
        if (model != null && model.modelName.isNotBlank()) return model.modelName
        if (model != null) {
            val path = model.modelPath.substringAfterLast('/')
            if (path.isNotBlank()) return path
        }
        // No model loaded. The badge still needs *something* —
        // surface the engine tag so the user sees "no-model /
        // runanywhere" rather than a blank pill.
        return when (engineTag) {
            "none" -> "no model"
            else -> engineTag
        }
    }
}