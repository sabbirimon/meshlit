package com.meshlit.chat

import org.junit.Assert.*
import org.junit.Test

class ChatToolSelectionTest {
    @Test fun eachGroupCanBeSelectedWithoutGrantingAnyOtherGroupOrChangingRoute() {
        for(group in ChatToolGroup.entries) {
            val original=ChatOptions(systemPrompt="Keep my instructions",maxTokens=256)
            val selected=group.update(original,true)
            selected.validate()
            assertEquals(group.count,selected.toolCount)
            assertEquals(setOf(group),ChatToolGroup.entries.filter{it.selected(selected)}.toSet())
            assertEquals(original,group.update(selected,false))
        }
    }
    @Test fun fullSelectionCanBeReducedButCannotSilentlyExceedTheLimit() {
        val full=ChatOptions(nodeTools=true,memoryTools=true,localSearchTools=true)
        assertEquals(10,full.toolCount)
        assertNotNull(ChatToolGroup.PHONE.unavailableReason(full,false))
        assertThrows(IllegalArgumentException::class.java){ChatToolGroup.PHONE.update(full,true)}
        assertNull(ChatToolGroup.NODE.unavailableReason(full,false))
        val reduced=ChatToolGroup.NODE.update(full,false)
        ChatToolGroup.PHONE.update(reduced,true).validate()
        // A legacy invalid selection can always be repaired by revoking a group.
        ChatToolGroup.PHONE.update(full.copy(phoneTools=true),false).validate()
    }
    @Test fun onlineRoutedAndHostChatsRequireAnExplicitLocalRouteChange() {
        for(options in listOf(ChatOptions(onlineProfileId="saved-provider"),ChatOptions(routeId="saved-route"),ChatOptions(colibriMode="ON"))) {
            assertFalse(options.supportsLocalToolSelection)
            assertNotNull(ChatToolGroup.LOCAL_SEARCH.unavailableReason(options,false))
            assertThrows(IllegalArgumentException::class.java){ChatToolGroup.LOCAL_SEARCH.update(options,true)}
        }
        assertTrue(ChatOptions().supportsLocalToolSelection)
    }
    @Test fun restrictedBuildExplainsUnavailableGroupsButAlwaysPermitsTurningThemOff() {
        assertNotNull(ChatToolGroup.PHONE.unavailableReason(ChatOptions(),true))
        assertThrows(IllegalArgumentException::class.java){ChatToolGroup.PHONE.update(ChatOptions(),true,true)}
        assertNull(ChatToolGroup.PHONE.unavailableReason(ChatOptions(phoneTools=true),true))
        assertFalse(ChatToolGroup.PHONE.update(ChatOptions(phoneTools=true),false,true).phoneTools)
        assertNull(ChatToolGroup.LOCAL_SEARCH.unavailableReason(ChatOptions(),true))
    }
}
