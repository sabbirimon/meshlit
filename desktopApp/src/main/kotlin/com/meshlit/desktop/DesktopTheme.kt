package com.meshlit.desktop

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshlit.workspace.WorkspaceLook

internal fun desktopColors(look: WorkspaceLook): Colors {
    val studio = look == WorkspaceLook.STUDIO
    return if (studio) darkColors(primary = Color(0xFFA8C7FA), secondary = Color(0xFFC4B5FD),
        background = Color(0xFF131314), surface = Color(0xFF1E1F20),
        onBackground = Color(0xFFE3E3E3), onSurface = Color(0xFFE3E3E3), onPrimary = Color(0xFF182C4A))
        else if (look == WorkspaceLook.PAPER) lightColors(primary = Color(look.accent), background = Color(look.background),
        surface = Color(look.surface), onBackground = Color(look.foreground), onSurface = Color(look.foreground))
        else darkColors(primary = Color(look.accent), background = Color(look.background), surface = Color(look.surface),
            onBackground = Color(look.foreground), onSurface = Color(look.foreground), onPrimary = Color(0xFF101113))
}

@Composable internal fun DesktopTheme(look: WorkspaceLook = WorkspaceLook.STUDIO, scale: Float = 1f, content: @Composable () -> Unit) {
    val colors = desktopColors(look)
    MaterialTheme(colors = colors, shapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp)), typography = Typography(defaultFontFamily = FontFamily(androidx.compose.ui.text.platform.Font("fonts/figtree.ttf")),
        h4 = TextStyle(fontSize = (36 * scale).sp, fontWeight = FontWeight.Medium, letterSpacing = (-.8).sp),
        h5 = TextStyle(fontSize = (22 * scale).sp, fontWeight = FontWeight.Medium),
        h6 = TextStyle(fontSize = (18 * scale).sp, fontWeight = FontWeight.Medium),
        button = TextStyle(fontSize = (14 * scale).sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
        caption = TextStyle(fontSize = (12 * scale).sp, lineHeight = (18 * scale).sp),
        body1 = TextStyle(fontSize = (15 * scale).sp, lineHeight = (23 * scale).sp)), content = content)
}
