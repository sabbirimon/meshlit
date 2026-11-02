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
    Surface(Modifier.fillMaxWidth().testTag("workspace-dock"),color=MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=4.dp).selectableGroup()) {
            items.forEach{(id,label,icon)->
                val active=selected==id
                Column(Modifier.weight(1f).heightIn(min=52.dp).selectable(active,role=Role.Tab,onClick={onSelect(id)}).testTag("dock-$id")
                    .padding(vertical=4.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                    Surface(shape=RoundedCornerShape(12.dp),color=if(active)MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent) {
                        Icon(icon,null,Modifier.padding(horizontal=14.dp,vertical=3.dp).size(19.dp),
                            tint=if(active)MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(label,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelSmall,
                        color=if(active)MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
