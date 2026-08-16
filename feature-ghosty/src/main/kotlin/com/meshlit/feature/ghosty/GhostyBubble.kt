package com.meshlit.feature.ghosty

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures

/**
 * The floating Ghosty bubble. Renders as a circular FAB-like
 * surface with a chat icon. Tapping opens the expanded screen;
 * the parent composable decides what to do with drag gestures.
 *
 * Note: this is just the visual. Adding it to a real window over
 * other apps is done by `GhostyOverlayService` which inflates
 * this composable into a `Window.addContentView` surface.
 */
@Composable
fun GhostyBubble(
    accent: Color,
    opacity: Float = 0.85f,
    bubbleSizeDp: Int = 56,
    onTap: () -> Unit = {},
    onDrag: (Float, Float) -> Unit = { _, _ -> },
) {
    Surface(
        shape = CircleShape,
        color = accent,
        shadowElevation = 8.dp,
        modifier = Modifier
            .size(bubbleSizeDp.dp)
            .alpha(opacity),
    ) {
        Box(
            modifier = Modifier
                .background(Color.Transparent)
                .pointerInput(Unit) {
                    detectTapGestures { onTap() }
                }
                .pointerInput(Unit) {
                    detectDragGestures { _, delta -> onDrag(delta.x, delta.y) }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.ChatBubble,
                contentDescription = "Ghosty",
                tint = Color.White,
                modifier = Modifier.size((bubbleSizeDp * 0.5f).dp),
            )
        }
    }
}
