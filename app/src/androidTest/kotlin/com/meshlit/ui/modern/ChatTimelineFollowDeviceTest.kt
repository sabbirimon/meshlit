package com.meshlit.ui.modern

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.meshlit.MainActivity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Isolated UI fixture, never inserted into owner chats or reported as model output. */
class ChatTimelineFollowDeviceTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun longStreamingTailViewportResizeAndManualHistoryStayPredictable() {
        val ready=java.util.concurrent.atomic.AtomicBoolean(false)
        lateinit var list:LazyListState
        lateinit var following:MutableState<Boolean>
        var tail by mutableStateOf("Initial tail\n".repeat(80))
        var footer by mutableStateOf(80.dp)
        compose.activityRule.scenario.onActivity{activity->activity.setContent{
            MaterialTheme {
                list=rememberLazyListState();SideEffect{ready.set(true)}
                following=rememberChatFollowing(list,"fixture-conversation",false)
                Column(Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("fixture-timeline"),state=list) {
                        items(12){index->Text("History fixture $index\n".repeat(12))}
                        item{Text(tail)}
                        item{Spacer(Modifier.fillMaxWidth().height(1.dp).testTag("fixture-end"))}
                    }
                    Button(onClick={following.value=true},modifier=Modifier.testTag("fixture-latest")){Text("Latest")}
                    Spacer(Modifier.height(footer))
                }
            }
        }}
        compose.waitUntil(15_000){ready.get() && !list.canScrollForward && list.layoutInfo.totalItemsCount>0}
        compose.onNodeWithTag("fixture-end").assertIsDisplayed()
        compose.runOnIdle{tail+="Streamed chunk\n".repeat(100)}
        compose.waitUntil(10_000){!list.canScrollForward}
        compose.onNodeWithTag("fixture-end").assertIsDisplayed()
        // Keyboard/composer growth changes the same viewport geometry as this footer.
        compose.runOnIdle{footer=240.dp}
        compose.waitUntil(10_000){!list.canScrollForward}
        compose.onNodeWithTag("fixture-end").assertIsDisplayed()
        compose.onNodeWithTag("fixture-timeline").performTouchInput{swipeDown(durationMillis=500)}
        compose.waitUntil(10_000){!list.isScrollInProgress && !following.value}
        val oldPosition=compose.runOnIdle{list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset}
        compose.runOnIdle{tail+="More streaming fixture text\n".repeat(100)}
        compose.waitForIdle()
        assertEquals(oldPosition,compose.runOnIdle{list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset})
        compose.onNodeWithTag("fixture-latest").performClick()
        compose.waitUntil(10_000){following.value && !list.canScrollForward}
        compose.onNodeWithTag("fixture-end").assertIsDisplayed()
    }
}
