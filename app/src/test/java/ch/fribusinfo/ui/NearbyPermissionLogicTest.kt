package ch.fribusinfo.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class NearbyPermissionLogicTest {
    @Test fun asksOnlyOnceAndOnlyWhenMissing() {
        assertEquals(true, shouldAskPermissionOnOpen(enabled = true, granted = false, alreadyAsked = false))
        assertEquals(false, shouldAskPermissionOnOpen(true, granted = true, alreadyAsked = false))
        assertEquals(false, shouldAskPermissionOnOpen(true, granted = false, alreadyAsked = true))
        assertEquals(false, shouldAskPermissionOnOpen(false, false, false))
    }

    @Test fun messageNotShownBeforePermissionResult() {
        // source already emitted null (no permission) while the dialog is still open
        assertEquals(false, isPositionUnavailable(permissionResolved = false, emitted = true, hasLocation = false))
    }

    @Test fun messageShownAfterDenial() {
        assertEquals(true, isPositionUnavailable(permissionResolved = true, emitted = true, hasLocation = false))
    }

    @Test fun grantedWithFixNeverShowsMessage() {
        assertEquals(false, isPositionUnavailable(true, emitted = true, hasLocation = true))
        assertEquals(false, isPositionUnavailable(true, emitted = false, hasLocation = false))
    }
}
