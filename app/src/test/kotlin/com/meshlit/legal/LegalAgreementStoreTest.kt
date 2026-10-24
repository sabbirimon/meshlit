package com.meshlit.legal

import android.content.Context
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class LegalAgreementStoreTest {
    @Test fun bothChoicesAreRequiredAndAcceptancePersists() {
        val context = RuntimeEnvironment.getApplication()
        val name = "test-legal-${UUID.randomUUID()}"
        val store = LegalAgreementStore(context, name)
        assertFalse(store.accepted())
        assertThrows(IllegalArgumentException::class.java) { store.accept(true, false) }
        assertThrows(IllegalArgumentException::class.java) { store.accept(false, true) }
        assertFalse(store.accepted())
        store.accept(true, true)
        assertTrue(LegalAgreementStore(context, name).accepted())
        assertTrue(store.acceptedAt()!! > 0)
    }
    @Test fun oldDocumentVersionsRequireFreshAcceptance() {
        val context = RuntimeEnvironment.getApplication()
        val name = "test-legal-${UUID.randomUUID()}"
        context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().putString("terms", "old")
            .putString("privacy", LegalAgreementStore.VERSION).putLong("accepted-at", 1).commit()
        assertFalse(LegalAgreementStore(context, name).accepted())
        assertNull(LegalAgreementStore(context, name).acceptedAt())
    }
}
