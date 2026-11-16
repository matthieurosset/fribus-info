package ch.fribusinfo.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class StopRowFormatTest {
    @Test fun distanceRoundsToNearest25() {
        assertEquals("125 m", formatDistance(125))
        assertEquals("125 m", formatDistance(137))
        assertEquals("150 m", formatDistance(138))
        assertEquals("0 m", formatDistance(5))
        assertEquals("1950 m", formatDistance(1949))
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
