package com.meshlit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min

/**
 * Phase 12.2 — lightweight HSV color picker.
 *
 * Layout (top → bottom):
 *  1. **Saturation / Value square** — pick (s, v) at the active hue.
 *  2. **Hue strip** — pick the hue (0–360°).
 *  3. **Preview swatch + ARGB hex** — confirms the current pick.
 *
 * No external deps — uses Compose `pointerInput` for taps and
 * drags. Cheap on the recomposition hot path because the gradient
 * shaders don't rebuild while the user drags; only the cursor
 * position animates.
 *
 * Returns the picked color through [onColorChange]. The picker
 * treats saturation / value as relative to the current hue (so
 * picking a different hue keeps brightness sensible).
 */
@Composable
fun HsvColorPicker(
    initialColor: Color,
    onColorChange: (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    val initialHsv = remember(initialColor) { Hsv.fromColor(initialColor) }
    var hue by remember(initialColor) { mutableStateOf(initialHsv.h) }
    var saturation by remember(initialColor) { mutableStateOf(initialHsv.s) }
    var value by remember(initialColor) { mutableStateOf(initialHsv.v) }

    val pickedColor = Color.hsv(
        hue = hue,
        saturation = saturation.coerceIn(0f, 1f),
        value = value.coerceIn(0f, 1f),
    )

    Column(modifier = modifier) {
        Text(
            text = "Tap or drag to pick a color",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        SaturationValueSquare(
            hue = hue,
            saturation = saturation,
            value = value,
            onPick = { s, v ->
                saturation = s
                value = v
                onColorChange(Color.hsv(hue, s, v))
            },
        )

        Spacer(Modifier.height(12.dp))

        HueStrip(
            hue = hue,
            onPick = { newHue ->
                hue = newHue
                onColorChange(Color.hsv(newHue, saturation, value))
            },
        )

        Spacer(Modifier.height(12.dp))

        // Live preview — picked swatch + ARGB hex string.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(pickedColor)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        CircleShape,
                    ),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "#${
                    pickedColor.toArgb().toUInt().toString(16).padStart(8, '0').uppercase()
                }",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
            )
        }
    }
}

/**
 * The 2D saturation × value pad. The bottom-right corner is fully
 * saturated + bright; the bottom-left is fully desaturated; the
 * top edge is fully bright.
 *
 * Drawn as a 3-stop linear gradient layered on top of the hue:
 *  - Horizontal gradient: white → hue (saturation ramp)
 *  - Vertical gradient: transparent → black (value ramp)
 */
@Composable
private fun SaturationValueSquare(
    hue: Float,
    saturation: Float,
    value: Float,
    onPick: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fullColor = Color.hsv(hue, 1f, 1f)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(Color.White, fullColor),
                ),
            )
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                ),
            )
            .pointerInput(hue) {
                detectDragGestures(
                    onDragStart = { offset ->
                        pickFromOffset(offset, size.width.toFloat(), size.height.toFloat(), onPick)
                    },
                    onDrag = { change, _ ->
                        pickFromOffset(change.position, size.width.toFloat(), size.height.toFloat(), onPick)
                    },
                )
            }
            .pointerInput(hue) {
                detectTapGestures { offset ->
                    pickFromOffset(offset, size.width.toFloat(), size.height.toFloat(), onPick)
                }
            },
    ) {
        val totalW = maxWidth
        val totalH = 180.dp
        val xFraction = saturation.coerceIn(0f, 1f)
        val yFraction = (1f - value).coerceIn(0f, 1f)
        // Cursor: 16dp circle, white outline + black inner ring.
        Box(
            modifier = Modifier
                .offset(
                    x = totalW * xFraction - 8.dp,
                    y = totalH * yFraction - 8.dp,
                )
                .size(16.dp)
                .clip(CircleShape)
                .border(2.dp, Color.White, CircleShape)
                .border(1.dp, Color.Black.copy(alpha = 0.5f), CircleShape),
        )
    }
}

/** Convert a touch offset → saturation + value fractions. */
private fun pickFromOffset(
    offset: Offset,
    width: Float,
    height: Float,
    onPick: (Float, Float) -> Unit,
) {
    if (width <= 0f || height <= 0f) return
    val s = (offset.x / width).coerceIn(0f, 1f)
    val v = (1f - offset.y / height).coerceIn(0f, 1f)
    onPick(s, v)
}

/**
 * Horizontal hue strip. Drawn as a `Brush.horizontalGradient`
 * with stops at each rainbow point. The cursor is a 16 dp white
 * circle outlined in black so it remains visible against any
 * background.
 */
@Composable
private fun HueStrip(
    hue: Float,
    onPick: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color.hsv(0f, 1f, 1f),
                        Color.hsv(60f, 1f, 1f),
                        Color.hsv(120f, 1f, 1f),
                        Color.hsv(180f, 1f, 1f),
                        Color.hsv(240f, 1f, 1f),
                        Color.hsv(300f, 1f, 1f),
                        Color.hsv(360f, 1f, 1f),
                    ),
                ),
            )
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        onPick((offset.x / size.width * 360f).coerceIn(0f, 360f))
                    },
                    onDrag = { change, _ ->
                        onPick((change.position.x / size.width * 360f).coerceIn(0f, 360f))
                    },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onPick((offset.x / size.width * 360f).coerceIn(0f, 360f))
                }
            },
    ) {
        val xFraction = (hue.coerceIn(0f, 360f) / 360f).coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .offset(x = maxWidth * xFraction - 8.dp, y = (-2).dp)
                .size(16.dp)
                .clip(CircleShape)
                .border(2.dp, Color.White, CircleShape)
                .border(1.dp, Color.Black.copy(alpha = 0.5f), CircleShape),
        )
    }
}

/**
 * Plain HSV carrier. Compose's [Color] doesn't expose HSV
 * directly — we have to derive it from RGB on the way in and
 * recompose it via [Color.hsv] on the way out.
 */
private data class Hsv(val h: Float, val s: Float, val v: Float) {
    companion object {
        fun fromColor(color: Color): Hsv {
            val r = color.red
            val g = color.green
            val b = color.blue
            val max = max(max(r, g), b)
            val min = min(min(r, g), b)
            val delta = max - min
            val v = max
            val s = if (max == 0f) 0f else delta / max
            val h = when {
                delta == 0f -> 0f
                max == r -> 60f * (((g - b) / delta) % 6f)
                max == g -> 60f * (((b - r) / delta) + 2f)
                else -> 60f * (((r - g) / delta) + 4f)
            }.let { if (it < 0f) it + 360f else it }
            return Hsv(h, s, v)
        }
    }
}