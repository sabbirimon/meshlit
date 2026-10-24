package com.meshlit.peering

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlinx.serialization.json.Json

@RunWith(RobolectricTestRunner::class)
class HostLocationStoreTest {
    @Test fun pinIsPrivateByDefaultAndStopsSharingWhenDisabled() {
        val context=RuntimeEnvironment.getApplication()
        context.getSharedPreferences("host-map-location",Context.MODE_PRIVATE).edit().clear().commit()
        val store=HostLocationStore(context)
        store.setPin(23.783333,90.416667)
        assertNull(store.sharedPin())
        store.setEnabled(true)
        assertEquals(HostMapPin(23.78,90.42),store.sharedPin())
        val reopened=HostLocationStore(context)
        assertEquals(HostMapPin(23.78,90.42),reopened.sharedPin())
        reopened.setEnabled(false)
        assertNull(reopened.sharedPin())
    }

    @Test fun invalidCoordinatesCannotBeStored() {
        val context=RuntimeEnvironment.getApplication()
        val store=HostLocationStore(context)
        assertThrows(IllegalArgumentException::class.java) { store.setPin(Double.NaN,0.0) }
        assertThrows(IllegalArgumentException::class.java) { store.setPin(91.0,0.0) }
        assertThrows(IllegalArgumentException::class.java) { store.setPin(0.0,-181.0) }
    }

    @Test fun olderRelayMemberWithoutLocationStillDecodes() {
        val old="""{"nodeId":"abcd","name":"Phone","publicKey":"key","lastSeenMs":1234}"""
        val member=Json.decodeFromString(RelayMember.serializer(),old)
        assertEquals("Unknown",member.deviceType)
        assertNull(member.latitude)
        assertNull(member.longitude)
        assertEquals(0L,member.locationUpdatedMs)
        assertEquals("active",member.activityState)
    }
}
