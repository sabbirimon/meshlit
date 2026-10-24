package com.meshlit.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Small original line icons, drawn natively; no copied branding or remote images. */
@Composable private fun MenuGlyph(kind: String, tint: Color, modifier: Modifier = Modifier.size(44.dp)) {
    Canvas(modifier) {
        val w = size.width; val h = size.height; val stroke = Stroke(w / 24)
        drawRoundRect(tint.copy(alpha = .12f), cornerRadius = CornerRadius(w / 4))
        when (kind) {
            "chat" -> { drawRoundRect(tint, Offset(w * .22f, h * .24f), Size(w * .56f, h * .46f), CornerRadius(w * .1f), style = stroke)
                drawLine(tint, Offset(w * .28f, h * .70f), Offset(w * .25f, h * .80f), w / 24) }
            "models" -> (0..2).forEach { i -> drawRoundRect(tint, Offset(w * .22f, h * (.22f + .20f * i)), Size(w * .56f, h * .14f), CornerRadius(w * .03f), style = stroke) }
            else -> { drawCircle(tint, w * .27f, style = stroke); (0..2).forEach { i -> drawCircle(tint, w * .04f, Offset(w * (.36f + .14f * i), h * .48f)) } }
        }
    }
}
@Composable internal fun MenuHeading(kind: String, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MenuGlyph(kind, MaterialTheme.colors.primary, Modifier.size(32.dp)); Text(title, fontWeight = FontWeight.Medium, fontSize = 18.sp)
    }
}
@Composable internal fun HeroMenu(t: (String, String) -> String, onChat: () -> Unit, onModels: () -> Unit, onStyle: () -> Unit) {
    Row(Modifier.widthIn(max = 560.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Triple("chat", t("Chat", "对话"), onChat), Triple("models", t("Models", "模型"), onModels), Triple("style", t("Style", "外观"), onStyle)).forEachIndexed { index, (kind, title, action) ->
            val tint = listOf(Color(0xFF69B9FF), MaterialTheme.colors.primary, Color(0xFF61DCA4))[index]
            Surface(Modifier.weight(1f).clickable(onClick = action), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colors.surface,
                border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = .08f))) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.Start) {
                    MenuGlyph(kind, tint); Text(title, fontWeight = FontWeight.Medium)
                    Text(when(kind) { "chat" -> t("Start fresh", "新对话"); "models" -> t("Connect your host", "连接主机"); else -> t("Make it yours", "个性化设置") }, style = MaterialTheme.typography.caption)
                }
            }
        }
    }
}
