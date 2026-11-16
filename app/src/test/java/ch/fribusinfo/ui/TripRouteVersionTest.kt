package ch.fribusinfo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripRouteVersionTest {
    @Test fun sameVersionIsNotStale() = assertFalse(tripRouteIsStale("202611161806", "202611161806"))

    @Test fun otherVersionIsStale() = assertTrue(tripRouteIsStale("202611161806", "202611230400"))

    @Test fun routeCarriesTheVersion() =
        assertEquals("trip/7/20261116/3?delay=0&version=202611161806", Routes.trip(7, 20261116, 3, 0, "202611161806"))
}
