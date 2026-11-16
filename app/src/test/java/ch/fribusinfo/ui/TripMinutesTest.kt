package ch.fribusinfo.ui

import ch.fribusinfo.data.Stop
import ch.fribusinfo.data.StopWithLines
import ch.fribusinfo.data.TripStop
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class TripMinutesTest {
    private val t0 = ZonedDateTime.of(2026, 11, 17, 12, 0, 0, 0, ZoneId.of("Europe/Zurich"))
    private fun ts(seq: Int, sec: Long) =
        TripStop(StopWithLines(Stop(seq.toLong(), "s$seq", "c", 0.0, 0.0), emptyList(), null), seq, t0.plusSeconds(sec))
    private val trip = listOf(ts(1, 0), ts(2, 120), ts(3, 300), ts(4, 359), ts(5, 600))

    @Test fun zeroAtChosenBlankBeforeFlooredAfter() =
        assertEquals(listOf(null, null, "0'", "0'", "5'"), tripMinutes(trip, 3))

    @Test fun chosenFirstCountsEverythingAfter() =
        assertEquals(listOf("0'", "2'", "5'", "5'", "10'"), tripMinutes(trip, 1))

    @Test fun unknownSeqShowsNoMinutes() =
        assertEquals(List(5) { null }, tripMinutes(trip, 99))
}
