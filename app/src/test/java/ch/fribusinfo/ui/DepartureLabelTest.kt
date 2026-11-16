package ch.fribusinfo.ui

import ch.fribusinfo.data.Departure
import ch.fribusinfo.data.Line
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class DepartureLabelTest {
    private val zone = ZoneId.of("Europe/Zurich")
    private val now = ZonedDateTime.of(2026, 11, 17, 12, 0, 0, 0, zone)
    private fun dep(sec: Long, headsign: String = "h", id: Long = 1) = Departure(
        id, Line(1, "2", 0, 0, false), headsign, "c", LocalDate.of(2026, 11, 17), 1, now.plusSeconds(sec),
    )

    private fun label(sec: Long, delay: Duration? = null, at: ZonedDateTime = now) = departureLabel(dep(sec), at, false, delay)

    // Minutes = clock-minute difference.
    @Test fun clockMinuteDifferenceNotFloor() {
        val at = ZonedDateTime.of(2026, 11, 17, 10, 4, 30, 0, zone)
        val d = dep(0).copy(scheduled = ZonedDateTime.of(2026, 11, 17, 10, 5, 0, 0, zone))
        assertEquals("1'", departureLabel(d, at, false, null))
    }

    @Test fun sameMinuteIsZero() = assertEquals("0'", label(59))
    @Test fun sevenMinutes() = assertEquals("7'", label(7 * 60 + 59))
    @Test fun midnightCrossing() {
        val at = ZonedDateTime.of(2026, 11, 17, 23, 58, 40, 0, zone)
        val d = dep(0).copy(scheduled = ZonedDateTime.of(2026, 11, 18, 0, 3, 0, 0, zone))
        assertEquals("5'", departureLabel(d, at, false, null))
    }

    @Test fun overAnHourUsesHoursFormat() {
        assertEquals("1h05'", label(65 * 60))
        assertEquals("2h00'", label(120 * 60))
        assertEquals("59'", label(59 * 60))
        assertEquals("1h00'", label(60 * 60))
    }

    @Test fun absoluteIsScheduled24h() {
        assertEquals("12:07", departureLabel(dep(7 * 60), now, true, null))
        assertEquals("15:07", departureLabel(dep(3 * 3600 + 7 * 60), now, true, null))
        // Absolute shows the SCHEDULED time even when delayed.
        assertEquals("12:07", departureLabel(dep(7 * 60), now, true, Duration.ofMinutes(2)))
    }

    @Test fun delayAddsToMinutes() = assertEquals("9'", label(7 * 60, Duration.ofMinutes(2)))
    @Test fun earlyBusCounts() = assertEquals("5'", label(7 * 60, Duration.ofMinutes(-2)))
    @Test fun delayWithSecondsPrecision() = assertEquals("8'", label(7 * 60, Duration.ofSeconds(90)))
    @Test fun passedGivesNegative() = assertEquals("-1'", label(-30, Duration.ZERO))

    // delayMin truncated toward zero.
    @Test fun delayMinutesTruncation() {
        assertEquals(0L, delayMinutes(Duration.ofSeconds(-59)))
        assertEquals(-1L, delayMinutes(Duration.ofSeconds(-61)))
        assertEquals(0L, delayMinutes(Duration.ofSeconds(59)))
        assertEquals(1L, delayMinutes(Duration.ofSeconds(60)))
        assertEquals(0L, delayMinutes(null))
        assertEquals(false, isDelayed(Duration.ofSeconds(-59)))
        assertEquals(true, isDelayed(Duration.ofSeconds(-61)))
    }

    @Test fun pillText() {
        assertEquals("+3'", delayPill(Duration.ofMinutes(3)))
        assertEquals("+0'", delayPill(Duration.ofSeconds(59)))
        assertEquals("+0'", delayPill(Duration.ZERO))
        assertEquals("+0'", delayPill(Duration.ofSeconds(-59)))
        assertEquals("-1'", delayPill(Duration.ofSeconds(-61)))
        assertEquals("-2'", delayPill(Duration.ofMinutes(-2)))
        assertEquals(null, delayPill(null))
    }

    @Test fun serviceDateIsYyyymmdd() = assertEquals(20261117, serviceDateInt(dep(0)))

    // Now / passed classification.
    @Test fun stateNowUpcomingPassed() {
        assertEquals(DepartureState.NOW, departureState(dep(59), now, null))
        assertEquals(DepartureState.UPCOMING, departureState(dep(60), now, null))
        assertEquals(DepartureState.PASSED, departureState(dep(-1), now, Duration.ZERO))
        // A late bus is not passed; an early one can be.
        assertEquals(DepartureState.UPCOMING, departureState(dep(-120), now, Duration.ofMinutes(5)))
        assertEquals(DepartureState.PASSED, departureState(dep(30), now, Duration.ofMinutes(-2)))
    }

    @Test fun stateBoundaries() {
        // 12:00:00 now: 11:59:59 and 11:59:00 are the previous minute, 12:00:00 is now
        assertEquals(DepartureState.PASSED, departureState(dep(-60), now, null))
        assertEquals(DepartureState.PASSED, departureState(dep(-1), now, null))
        assertEquals(DepartureState.NOW, departureState(dep(0), now, null))
    }

    private fun d(id: Long, sec: Long, headsign: String = "h") = dep(sec, headsign, id)
    private fun ids(l: List<Departure>) = l.map { it.tripId }

    @Test fun lateBusStaysListedAfterItsScheduledMinute() =
        assertEquals(listOf(1L), ids(visibleDepartures(listOf(d(1, -120)), mapOf(1L to Duration.ofMinutes(5)), now)))

    @Test fun windowWithRealtimeKeepsPreviousMinute() {
        val delays = mapOf(1L to Duration.ZERO, 2L to Duration.ZERO, 3L to Duration.ZERO)
        // -1 min: kept (passed); -2 min: dropped.
        assertEquals(listOf(1L), ids(visibleDepartures(listOf(d(1, -1)), delays, now)))
        assertEquals(listOf(1L), ids(visibleDepartures(listOf(d(1, -60), d(2, -61)), delays, now)))
        assertEquals(emptyList<Long>(), ids(visibleDepartures(listOf(d(3, -121)), delays, now)))
    }

    @Test fun windowWithoutRealtimeNeverShowsPassed() {
        val out = visibleDepartures(listOf(d(3, 10), d(1, -1), d(2, 0), d(4, -61)), emptyMap(), now)
        assertEquals(listOf(2L, 3L), ids(out))
    }

    @Test fun earlyBusAlreadyGone() {
        // Scheduled in 1 min but 5 min early: real minute is 4 min ago, dropped.
        assertEquals(emptyList<Long>(), ids(visibleDepartures(listOf(d(1, 60)), mapOf(1L to Duration.ofMinutes(-5)), now)))
    }

    @Test fun orderedByRealTimeSecondsThenHeadsign() {
        val a = d(1, 60)              // +5 -> 6 min
        val b = d(2, 180, "Zèbre")    // on time -> 3 min
        val c = d(3, 0)               // 0
        val e = d(4, -60, "Alpha")    // +4 -> 3 min, same second as b: headsign first
        val out = visibleDepartures(listOf(a, b, c, e), mapOf(1L to Duration.ofMinutes(5), 4L to Duration.ofMinutes(4)), now)
        assertEquals(listOf(3L, 4L, 2L, 1L), ids(out))
    }

    @Test fun sameMinuteDifferentSecondsOrderedBySeconds() {
        val out = visibleDepartures(listOf(d(1, 50, "A"), d(2, 10, "Z")), emptyMap(), now)
        assertEquals(listOf(2L, 1L), ids(out))
    }

    @Test fun tieOnRealTimeBrokenByHeadsign() {
        val out = visibleDepartures(listOf(d(1, 120, "Schönberg"), d(2, 120, "Campus"), d(3, 120, "Dorf")), emptyMap(), now)
        assertEquals(listOf(2L, 3L, 1L), ids(out))
    }

    @Test fun defaultLimitIsThirty() =
        assertEquals(30, visibleDepartures((1L..40L).map { d(it, it * 10) }, emptyMap(), now).size)

    @Test fun limitAppliedAfterResort() {
        val out = visibleDepartures(listOf(d(1, 60), d(2, 120), d(3, 180)), mapOf(1L to Duration.ofMinutes(30)), now, limit = 2)
        assertEquals(listOf(2L, 3L), ids(out))
    }

    @Test fun firstUpcomingSkipsPassedRows() {
        val delays = mapOf(1L to Duration.ZERO, 2L to Duration.ZERO)
        val list = visibleDepartures(listOf(d(1, -30), d(2, -60), d(3, 90)), delays, now)
        assertEquals(listOf(2L, 1L, 3L), ids(list))
        assertEquals(2, firstUpcomingIndex(list, delays, now))
    }

    @Test fun nextMinuteBoundary() {
        assertEquals(20L + 30_000, millisUntilNextMinute(now.withSecond(30)))
        assertEquals(20L + 60_000, millisUntilNextMinute(now))
    }
}
