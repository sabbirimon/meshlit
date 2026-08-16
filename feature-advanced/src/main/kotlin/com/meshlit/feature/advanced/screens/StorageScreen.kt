package com.meshlit.feature.advanced.screens

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Storage screen. Free space + buttons to clear the cache and
 * temp dirs. Numbers are placeholders for now; real ones come
 * from `File.length()` and `File.usableSpace()` in Step 8.
 */
@Composable
fun StorageScreen(
    accent: Color,
    accentDim: Color,
    onBack: () -> Unit,
) {
    var status by remember { mutableStateOf("(no measurement yet)") }
    SectionScreen(
        title = "Storage",
        subtitle = "Free space, cache, temp files.",
        accent = accent,
        accentDim = accentDim,
        onBack = onBack,
        content = {
            SectionCard(title = "Free space", accent = accent) {
                Button(onClick = { status = "filesDir: ${System.currentTimeMillis()}" }) { Text("Refresh") }
                Text(status)
            }
            SectionCard(title = "Maintenance", accent = accent) {
                Button(onClick = { status = "cache cleared" }) { Text("Clear cache") }
                Button(onClick = { status = "temp cleared" }) { Text("Clean temp") }
            }
        },
    )
}
