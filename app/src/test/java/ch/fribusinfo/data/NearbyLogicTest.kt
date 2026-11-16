package ch.fribusinfo.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

class NearbyLogicTest {
    private val lat = 46.8
    private val lon = 7.15
    private val mLat = 1 / 111_195.0
    private val mLon = 1 / (111_195.0 * cos(Math.toRadians(46.8)))

    @Test fun cornerOfTheSquareIsInside() {
        // 450 m north and 450 m east: about 636 m away in a straight line, still inside the square
        assertTrue(isInNearbySquare(lat, lon, lat + 450 * mLat, lon + 450 * mLon))
        assertTrue(isInNearbySquare(lat, lon, lat - 499 * mLat, lon - 499 * mLon))
    }

    @Test fun edgeBeyondFiveHundredMetersIsOutside() {
        assertFalse(isInNearbySquare(lat, lon, lat + 501 * mLat, lon))
        assertFalse(isInNearbySquare(lat, lon, lat, lon - 501 * mLon))
        assertTrue(isInNearbySquare(lat, lon, lat + 499 * mLat, lon))
    }

    @Test fun reloadsOnlyAfterHundredMeters() {
        assertTrue(shouldReloadNearby(null, null, lat, lon))
        assertFalse(shouldReloadNearby(lat, lon, lat + 99 * mLat, lon))
        assertTrue(shouldReloadNearby(lat, lon, lat + 101 * mLat, lon))
        assertTrue(shouldReloadNearby(lat, lon, lat, lon + 150 * mLon))
    }
}
