package com.meshlit.ui.modern

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** A flat touch target. Holding it explains the action without executing it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TouchIcon(icon: ImageVector, label: String, modifier: Modifier = Modifier,
                       enabled: Boolean = true, onClick: () -> Unit) {
    val context = LocalContext.current
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Box(modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).combinedClickable(
        enabled = enabled, role = Role.Button, onClickLabel = label,
        onLongClickLabel = "Show details",
        onLongClick = { Toast.makeText(context, label, Toast.LENGTH_SHORT).show() },
        onClick = onClick
    ), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = if (enabled) tint else tint.copy(alpha = 0.38f))
    }
}
