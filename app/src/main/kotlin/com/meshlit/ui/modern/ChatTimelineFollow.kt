package com.meshlit.ui.modern

import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*

/** Follows a trailing anchor, including long streamed blocks and viewport/IME changes.
 * Only a real drag suspends following; our own layout correction is not a user gesture. */
@Composable internal fun rememberChatFollowing(list:LazyListState,conversationId:String?,searchOpen:Boolean):MutableState<Boolean> {
    val following=remember(conversationId){mutableStateOf(true)}
    val dragged by list.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(searchOpen,conversationId){if(searchOpen)following.value=false}
    LaunchedEffect(list,conversationId) {
        var manual=false
        snapshotFlow{Triple(dragged,list.isScrollInProgress,list.canScrollForward)}.collect{(dragging,scrolling,forward)->
            if(dragging){manual=true;following.value=false}
            else if(manual && !scrolling){manual=false;following.value=!forward}
        }
    }
    LaunchedEffect(list,conversationId,following.value,searchOpen) {
        if(following.value && !searchOpen) {
            snapshotFlow {
                val layout=list.layoutInfo
                val last=layout.visibleItemsInfo.lastOrNull()
                listOf(layout.totalItemsCount,layout.viewportSize.height,last?.offset ?: 0,last?.size ?: 0,if(list.canScrollForward)1 else 0)
            }.collect { geometry ->
                if(geometry[0]>0 && geometry[4]==1)list.requestScrollToItem(geometry[0]-1)
            }
        }
    }
    return following
}
