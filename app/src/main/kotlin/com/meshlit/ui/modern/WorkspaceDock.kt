package com.meshlit.ui.modern

import androidx.compose.foundation.layout.*
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
    Surface(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=12.dp,vertical=4.dp).testTag("workspace-dock"),
        shape=RoundedCornerShape(26.dp),color=MaterialTheme.colorScheme.surfaceContainerLow) {
        NavigationBar(containerColor=MaterialTheme.colorScheme.surfaceContainerLow,tonalElevation=0.dp,
            windowInsets=WindowInsets(0,0,0,0)) {
            items.forEach{(id,label,icon)->NavigationBarItem(selected=selected==id,onClick={onSelect(id)},
                icon={Icon(icon,null,Modifier.size(22.dp))},label={Text(label,maxLines=1,overflow=TextOverflow.Ellipsis)},
                modifier=Modifier.testTag("dock-$id"))}
        }
    }
}
