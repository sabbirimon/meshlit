package com.meshlit.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import com.meshlit.R

/**
 * One row of the "Supported formats" card. The Models screen renders
 * this list as a multi-row table so the user can see — for each
 * open-source model format — which runtime would carry it, what
 * status the runtime is in, and what the cost trade-off looks like.
 *
 * Today the list is mostly aspirational: only `GGUF` is shipped. The
 * other rows describe the Phase 2 / Phase 3 roadmap so the user
 * understands *why* we only support one format right now. APK size
 * is no longer a blocker — multi-runtime is on the roadmap.
 *
 * The status text comes from `strings.xml` so it can be swapped
 * without rerunning the build.
 */
@Immutable
data class EngineFormatRow(
    val formatLabelRes: Int,
    val runtimeLabelRes: Int,
    val statusLabelRes: Int,
    val isShipped: Boolean,
)

val engineFormats: List<EngineFormatRow> = listOf(
    EngineFormatRow(
        formatLabelRes = R.string.models_format_gguf,
        runtimeLabelRes = R.string.engine_supported_formats_gguf_runtime,
        statusLabelRes = R.string.engine_supported_formats_gguf_status,
        isShipped = true,
    ),
    EngineFormatRow(
        formatLabelRes = R.string.models_format_onnx,
        runtimeLabelRes = R.string.models_format_onnx,
        statusLabelRes = R.string.engine_supported_formats_onnx_status,
        isShipped = false,
    ),
    EngineFormatRow(
        formatLabelRes = R.string.models_format_safetensors,
        runtimeLabelRes = R.string.models_format_safetensors,
        statusLabelRes = R.string.engine_supported_formats_safetensors_status,
        isShipped = false,
    ),
    EngineFormatRow(
        formatLabelRes = R.string.models_format_tflite,
        runtimeLabelRes = R.string.models_format_tflite,
        statusLabelRes = R.string.engine_supported_formats_tflite_status,
        isShipped = false,
    ),
    EngineFormatRow(
        formatLabelRes = R.string.models_format_mlx,
        runtimeLabelRes = R.string.models_format_mlx,
        statusLabelRes = R.string.engine_supported_formats_mlx_status,
        isShipped = false,
    ),
    EngineFormatRow(
        formatLabelRes = R.string.models_format_coreml,
        runtimeLabelRes = R.string.models_format_coreml,
        statusLabelRes = R.string.engine_supported_formats_coreml_status,
        isShipped = false,
    ),
)

/** Resolve the `statusLabelRes` to a localized string for the row. */
@Composable
fun EngineFormatRow.statusLabel(): String = stringResource(statusLabelRes)

/** Resolve the `runtimeLabelRes` to a localized string for the row. */
@Composable
fun EngineFormatRow.runtimeLabel(): String = stringResource(runtimeLabelRes)

/** Resolve the `formatLabelRes` to a localized string for the row. */
@Composable
fun EngineFormatRow.formatLabel(): String = stringResource(formatLabelRes)
