package ch.fribusinfo.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class StopRowFormatTest {
    @Test fun distanceRoundsUpToNext25() {
        assertEquals("25 m", formatDistance(0))
        assertEquals("25 m", formatDistance(24))
        assertEquals("50 m", formatDistance(25))
        assertEquals("150 m", formatDistance(137))
        assertEquals("150 m", formatDistance(125))
        assertEquals("525 m", formatDistance(500))
    }

    @Test fun badgeSlotsShowAllUpToEight() {
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8), badgeSlots(8).first)
        assertEquals(false, badgeSlots(8).second)
    }

    @Test fun badgeSlotsTruncateToSevenPlusEllipsis() {
        val (n, more) = badgeSlots(9)
        assertEquals(7, n.size)
        assertEquals(true, more)
    }
}
