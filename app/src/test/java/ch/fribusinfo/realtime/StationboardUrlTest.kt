package ch.fribusinfo.realtime

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class StationboardUrlTest {
    private val z = ZoneId.of("Europe/Zurich")

    @Test fun requestsFromSixtyMinutesBefore() {
        val now = ZonedDateTime.of(2026, 11, 16, 18, 14, 40, 0, z)
        assertEquals(
            "https://transport.opendata.ch/v1/stationboard?id=8587255&limit=150&datetime=2026-11-16+17%3A14",
            StationboardClient(clock = { now }).buildUrl(8587255, 150, now),
        )
    }

    @Test fun crossesMidnightAndUsesZurichTime() {
        val now = ZonedDateTime.of(2026, 11, 16, 23, 10, 0, 0, ZoneId.of("UTC")) // 00:10 Zurich on the 17th
        assertEquals(
            "http://x/stationboard?id=5&limit=7&datetime=2026-11-16+23%3A10",
            StationboardClient("http://x/").buildUrl(5, 7, now),
        )
    }
}
