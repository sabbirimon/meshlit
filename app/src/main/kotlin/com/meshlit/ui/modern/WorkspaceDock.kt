package com.meshlit.ui.modern

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Everyday destinations; the drawer continues to expose every advanced workspace. */
@Composable
internal fun WorkspaceDock(selected:String,onSelect:(String)->Unit) {
    val items=listOf(
        Triple("chat","Chat",Icons.Outlined.ChatBubbleOutline),
        Triple("models","Models",Icons.Outlined.Layers),
        Triple("devices","Devices",Icons.Outlined.Devices),
        Triple("agents","Agents",Icons.Outlined.SmartToy),
        Triple("settings","Settings",Icons.Outlined.Settings),
    ).filter{com.meshlit.BuildProfile.routeAllowed(it.first)}
    val colors=MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().navigationBarsPadding().testTag("workspace-dock")) {
        HorizontalDivider(color=colors.outlineVariant)
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            items.forEach { (id,label,icon) ->
                val active=selected==id
                Column(Modifier.weight(1f).heightIn(min=64.dp).selectable(active,role=Role.Tab,onClick={onSelect(id)})
                    .testTag("dock-$id").padding(vertical=6.dp), horizontalAlignment=Alignment.CenterHorizontally,
                    verticalArrangement=Arrangement.Center) {
                    val tint=if(active) colors.primary else colors.onSurfaceVariant
                    Icon(icon,null,Modifier.size(22.dp),tint=tint)
                    Spacer(Modifier.height(4.dp))
                    Text(label,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelMedium,color=tint)
                }
            }
        }
    }
}
