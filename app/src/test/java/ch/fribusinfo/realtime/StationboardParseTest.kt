package ch.fribusinfo.realtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class StationboardParseTest {
    private val zurich = ZoneId.of("Europe/Zurich")
    private fun fixture(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    @Test fun parsesRealResponse() {
        val list = parseStationboard(fixture("stationboard_8587255.json"))
        assertTrue(list.size >= 10)
        val two = list.first { it.line == "2" }
        assertEquals(zurich, two.scheduled.zone)
        assertEquals(zurich, two.expected!!.zone)
        assertTrue(two.to.isNotBlank())
    }

    @Test fun delayedEntryHasExpectedAfterScheduled() {
        val late = parseStationboard(fixture("stationboard_8587255.json")).first { it.expected != it.scheduled }
        assertTrue(late.expected!!.isAfter(late.scheduled))
    }

    @Test fun readsExactTimes() {
        val json = """{"stationboard":[{"stop":{"departure":"2026-11-16T16:16:00+0100","delay":0,
            "prognosis":{"departure":"2026-11-16T16:15:58+0100"}},"number":"2","to":"Villars-sur-Glâne, L"}]}"""
        val d = parseStationboard(json).single()
        assertEquals("2", d.line)
        assertEquals("Villars-sur-Glâne, L", d.to)
        assertEquals("2026-11-16T16:16+01:00[Europe/Zurich]", d.scheduled.toString())
        assertEquals("2026-11-16T16:15:58+01:00[Europe/Zurich]", d.expected.toString())
    }

    @Test fun missingPrognosisMeansNoExpected() {
        val json = """{"stationboard":[{"stop":{"departure":"2026-11-16T16:16:00+0100","prognosis":{"departure":null}},"number":"2","to":"x"},
            {"stop":{"departure":"2026-11-16T16:17:00+0100"},"number":"1","to":"y"}]}"""
        assertEquals(listOf(null, null), parseStationboard(json).map { it.expected })
    }

    @Test fun emptyStationboardIsEmptyNotAnError() {
        // The funicular (8530013) returned an empty board: no delay, no error.
        assertEquals(emptyList<RtDeparture>(), parseStationboard(fixture("stationboard_8530013_empty.json")))
    }

    @Test fun entriesWithoutDepartureAreSkipped() {
        val json = """{"stationboard":[{"stop":{"departure":null},"number":"2","to":"x"}]}"""
        assertEquals(emptyList<RtDeparture>(), parseStationboard(json))
    }
}
