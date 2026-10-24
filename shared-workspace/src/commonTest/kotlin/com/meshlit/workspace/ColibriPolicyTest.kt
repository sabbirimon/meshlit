package com.meshlit.workspace
import kotlin.test.*
class ColibriPolicyTest {
    @Test fun routingUsesPermissionAndFreshEvidenceWithoutFallbackAfterDispatch() {
        assertEquals(ColibriRoute.LOCAL, colibriDecision(ColibriMode.OFF, true, true, false).route)
        assertEquals(ColibriRoute.BLOCKED, colibriDecision(ColibriMode.ON, false, true, true).route)
        assertEquals(ColibriRoute.BLOCKED, colibriDecision(ColibriMode.ON, true, false, true).route)
        assertEquals(ColibriRoute.HOST, colibriDecision(ColibriMode.ON, true, true, true).route)
        assertEquals(ColibriRoute.LOCAL, colibriDecision(ColibriMode.AUTO, true, true, true).route)
        assertEquals(ColibriRoute.HOST, colibriDecision(ColibriMode.AUTO, true, true, false).route)
        assertEquals(ColibriRoute.HOST, colibriDecision(ColibriMode.AUTO, true, true, true, true).route)
        assertEquals(ColibriRoute.LOCAL, colibriDecision(ColibriMode.AUTO, false, true, true, true).route)
        assertEquals(ColibriRoute.BLOCKED, colibriDecision(ColibriMode.AUTO, true, false, false).route)
    }
    @Test fun agentModesAreChatBoundExpiringAndRevocable() {
        var clock = 0L; val modes = ColibriSessionModes { clock }
        assertFails { modes.request("a", ColibriMode.ON, false, true) }
        assertFails { modes.request("a", ColibriMode.ON, true, false) }
        modes.request("a", ColibriMode.AUTO, true, true)
        assertEquals(ColibriMode.AUTO, modes.effective("a", ColibriMode.OFF, true, true))
        assertEquals(ColibriMode.OFF, modes.effective("b", ColibriMode.OFF, true, true))
        clock = 1_800_000; assertEquals(ColibriMode.OFF, modes.effective("a", ColibriMode.OFF, true, true))
        modes.request("a", ColibriMode.ON, true, true)
        assertEquals(ColibriMode.OFF, modes.effective("a", ColibriMode.OFF, false, true))
        assertEquals(ColibriMode.OFF, modes.effective("a", ColibriMode.OFF, true, true))
        modes.request("a", ColibriMode.ON, true, true); modes.clear()
        assertEquals(ColibriMode.OFF, modes.effective("a", ColibriMode.OFF, true, true))
        assertEquals(ColibriMode.OFF, ColibriMode.parse("invalid"))
    }
}
