package com.meshlit.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.android.awaitFrame
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Polyhedral icosahedron wireframe — pixel-for-pixel port of
 * `PolyhedralMesh.tsx` from the Stitch source.
 *
 * Algorithm (verbatim):
 * 1. Compute 12 icosahedron vertices from the golden ratio.
 * 2. Build edges between vertices whose distance < 1.1.
 * 3. Each frame: rotate on X (0.008), Y (0.012), Z (0.005) radians.
 * 4. Project to 2D using `(size*0.38)/(1.8 - z*0.4)` depth scaling.
 * 5. Edges: linear gradient between purple/cyan/emerald (12-color palette).
 * 6. Vertices: filled circles with per-vertex shadowBlur=10 in their own color.
 */
@Composable
fun MeshlitPolyhedralMesh(
    palette: StitchPalette,
    modifier: Modifier = Modifier,
    size: Dp = 115.dp,
) {
    val rotationState = remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            awaitFrame()
            rotationState.value += 0.012f // accumulate on Y axis (~83ms/frame)
        }
    }

    val edgeGradient = when (palette) {
        StitchPalette.DARK -> listOf(
            MeshlitDesignPalette.iridescentMid.copy(alpha = 0.9f),   // purple
            MeshlitDesignPalette.iridescentStart.copy(alpha = 0.9f),  // cyan
            MeshlitDesignPalette.iridescentEnd.copy(alpha = 0.9f),    // emerald
        )
        StitchPalette.LIGHT -> listOf(
            Color(0xCC9333EA),
            Color(0xCC0891B2),
            Color(0xCC059669),
        )
    }

    val vertexColors = arrayOf(
        Color(0xFF38BDF8), Color(0xFFC084FC), Color(0xFF34D399),
        Color(0xFFF472B6), Color(0xFF60A5FA), Color(0xFFA78BFA),
        Color(0xFF2DD4BF), Color(0xFFFB923C), Color(0xFF38BDF8),
        Color(0xFFC084FC), Color(0xFF34D399), Color(0xFF818CF8),
    )

    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = 0.dp,
                ambientColor = MeshlitDesignPalette.Dark.haloCyanMedium,
                spotColor = MeshlitDesignPalette.Dark.haloCyanMedium,
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val rotY = rotationState.value
            val rotX = rotationState.value * 0.67f
            val rotZ = rotationState.value * 0.42f

            // Compute icosahedron vertices
            val phi = (1.0 + sqrt(5.0)).toFloat()
            val a = 1.0f
            val b = 1.0f / phi

            // 12 base vertices (normalized later)
            val rawVerts = arrayOf(
                floatArrayOf(-a,  b,  0f), floatArrayOf( a,  b,  0f),
                floatArrayOf(-a, -b,  0f), floatArrayOf( a, -b,  0f),
                floatArrayOf( 0f, -a,  b), floatArrayOf( 0f,  a,  b),
                floatArrayOf( 0f, -a, -b), floatArrayOf( 0f,  a, -b),
                floatArrayOf( b,  0f, -a), floatArrayOf( b,  0f,  a),
                floatArrayOf(-b,  0f, -a), floatArrayOf(-b,  0f,  a),
            )

            // Normalize to unit sphere
            val normalized = rawVerts.map { v ->
                val len = sqrt((v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).toDouble()).toFloat()
                floatArrayOf(v[0] / len, v[1] / len, v[2] / len)
            }

            // Rotate X
            val rotatedX = normalized.map { v ->
                val cosX = cos(rotX.toDouble()).toFloat()
                val sinX = sin(rotX.toDouble()).toFloat()
                floatArrayOf(
                    v[0],
                    v[1] * cosX - v[2] * sinX,
                    v[1] * sinX + v[2] * cosX,
                )
            }

            // Rotate Y
            val rotatedY = rotatedX.map { v ->
                val cosY = cos(rotY.toDouble()).toFloat()
                val sinY = sin(rotY.toDouble()).toFloat()
                floatArrayOf(
                    v[0] * cosY + v[2] * sinY,
                    v[1],
                    -v[0] * sinY + v[2] * cosY,
                )
            }

            // Rotate Z
            val rotatedZ = rotatedY.map { v ->
                val cosZ = cos(rotZ.toDouble()).toFloat()
                val sinZ = sin(rotZ.toDouble()).toFloat()
                floatArrayOf(
                    v[0] * cosZ - v[1] * sinZ,
                    v[0] * sinZ + v[1] * cosZ,
                    v[2],
                )
            }

            // Project to 2D
            val canvasSize = this.size.minDimension
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val projected = rotatedZ.map { v ->
                val depth = (canvasSize * 0.38f) / (1.8f - v[2] * 0.4f)
                Offset(
                    x = center.x + v[0] * depth,
                    y = center.y - v[1] * depth,
                )
            }

            // Draw edges (linear gradient stroke)
            val edgeBrush = Brush.linearGradient(
                colors = edgeGradient,
                start = Offset(0f, 0f),
                end = Offset(canvasSize, canvasSize),
            )

            for (i in projected.indices) {
                for (j in i + 1 until projected.size) {
                    val dx = projected[i].x - projected[j].x
                    val dy = projected[i].y - projected[j].y
                    val dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                    val depthAvg = (rotatedZ[i][2] + rotatedZ[j][2]) / 2f
                    if (dist < canvasSize * 1.1f) {
                        val alpha = ((depthAvg + 1f) / 2f).coerceIn(0.3f, 1.0f)
                        drawLine(
                            brush = edgeBrush,
                            start = projected[i],
                            end = projected[j],
                            strokeWidth = 1.6f * alpha,
                            cap = StrokeCap.Round,
                            alpha = alpha,
                        )
                    }
                }
            }

            // Draw vertices
            projected.forEachIndexed { i, p ->
                val color = vertexColors[i % vertexColors.size]
                val depthAvg = rotatedZ[i][2]
                val alpha = ((depthAvg + 1f) / 2f).coerceIn(0.4f, 1.0f)
                drawCircle(
                    color = color.copy(alpha = alpha),
                    radius = 4f * alpha,
                    center = p,
                )
            }
        }
    }
}