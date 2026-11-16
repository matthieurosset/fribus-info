package ch.fribusinfo.ui

import ch.fribusinfo.data.Stop
import ch.fribusinfo.data.StopWithLines
import ch.fribusinfo.data.TripStop
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

class TripMinutesTest {
    private val t0 = ZonedDateTime.of(2026, 11, 17, 12, 0, 0, 0, ZoneId.of("Europe/Zurich"))
    private fun ts(seq: Int, sec: Long) =
        TripStop(StopWithLines(Stop(seq.toLong(), "s$seq", "c", 0.0, 0.0), emptyList(), null), seq, t0.plusSeconds(sec))
    private val trip = listOf(ts(1, 0), ts(2, 120), ts(3, 300), ts(4, 359), ts(5, 3900))

    @Test fun minutesFromNowForEveryStopIncludingBeforeChosen() =
        assertEquals(listOf(0L, 2L, 5L, 5L, 65L), tripMinutes(trip, 3, null, t0))

    @Test fun nowAfterStartMakesEarlierStopsNegative() =
        assertEquals(listOf(-3L, -1L, 2L, 2L, 62L), tripMinutes(trip, 3, null, t0.plusMinutes(3)))

    @Test fun delayShiftsChosenAndLaterStopsOnly() =
        assertEquals(listOf(0L, 2L, 9L, 9L, 69L), tripMinutes(trip, 3, Duration.ofMinutes(4), t0))

    @Test fun earlyDelayShiftsBackwards() =
        assertEquals(listOf(0L, 2L, 3L, 3L, 63L), tripMinutes(trip, 3, Duration.ofMinutes(-2), t0))

    @Test fun clockMinuteDifference() =
        assertEquals(1L, tripMinutes(trip, 99, null, t0.minusSeconds(30).withSecond(59).minusMinutes(0))[1] - tripMinutes(trip, 99, null, t0)[1])

    @Test fun formatOverAnHour() = assertEquals("1h05'", formatMinutes(65))

    @Test fun realTimesShiftOnlyFromChosenStop() {
        val real = tripRealTimes(trip, 3, Duration.ofMinutes(4))
        assertEquals(listOf(t0, t0.plusSeconds(120), t0.plusSeconds(540), t0.plusSeconds(599), t0.plusSeconds(4140)), real)
    }

    @Test fun negativeDelayShiftsBack() =
        assertEquals(trip.map { it.scheduled.minusSeconds(180) }, tripRealTimes(trip, 1, Duration.ofMinutes(-3)))
}
