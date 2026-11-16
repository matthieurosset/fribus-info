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
    private val now = ZonedDateTime.of(2026, 11, 17, 12, 0, 0, 0, ZoneId.of("Europe/Zurich"))
    private fun dep(sec: Long) = Departure(
        1, Line(1, "2", 0, 0, false), "h", "c", LocalDate.of(2026, 11, 17), 1, now.plusSeconds(sec),
    )

    @Test fun minutesFloor() = assertEquals("7'", departureLabel(dep(7 * 60 + 59), now, false, null))
    @Test fun underOneMinuteIsZero() = assertEquals("0'", departureLabel(dep(59), now, false, null))
    @Test fun absoluteIs24h() = assertEquals("12:07", departureLabel(dep(7 * 60), now, true, null))
    @Test fun absoluteAfterNoon24h() = assertEquals("15:07", departureLabel(dep(3 * 3600 + 7 * 60), now, true, null))
    @Test fun delayShiftsTime() = assertEquals("12:09", departureLabel(dep(7 * 60), now, true, Duration.ofMinutes(2)))
    @Test fun serviceDateIsYyyymmdd() = assertEquals(20261117, serviceDateInt(dep(0)))

    @Test fun earlyBusIsTreatedAsOnTime() = assertEquals("12:07", departureLabel(dep(7 * 60), now, true, Duration.ofMinutes(-2)))
    @Test fun delayAddsToMinutes() = assertEquals("9'", departureLabel(dep(7 * 60), now, false, Duration.ofMinutes(2)))
    @Test fun badgeShownFromOneMinute() {
        assertEquals("+3'", delayBadge(Duration.ofMinutes(3)))
        assertEquals("+1'", delayBadge(Duration.ofSeconds(60)))
        assertEquals(null, delayBadge(Duration.ofSeconds(59)))
        assertEquals(null, delayBadge(Duration.ofMinutes(-2)))
        assertEquals(null, delayBadge(null))
    }

    @Test fun subMinuteDelayCountsAsZeroForButtonAndBadge() {
        assertEquals("12:07", departureLabel(dep(7 * 60), now, true, Duration.ofSeconds(59)))
        assertEquals(null, delayBadge(Duration.ofSeconds(59)))
    }

    @Test fun buttonEqualsScheduledPlusShownLabel() {
        val d = Duration.ofSeconds(150) // 2 min 30: label +2', button +2'
        assertEquals("+2'", delayBadge(d))
        assertEquals("12:09", departureLabel(dep(7 * 60), now, true, d))
    }

    private fun d(id: Long, sec: Long) = dep(sec).copy(tripId = id)

    @Test fun lateBusStaysListedAfterItsScheduledMinute() {
        val late = d(1, -120) // scheduled 2 min ago, 5 min late -> real in 3 min
        val out = visibleDepartures(listOf(late), mapOf(1L to Duration.ofMinutes(5)), now)
        assertEquals(listOf(1L), out.map { it.tripId })
    }

    @Test fun passedBusIsDropped() {
        val gone = d(1, -300)
        assertEquals(emptyList<Long>(), visibleDepartures(listOf(gone), mapOf(1L to Duration.ofMinutes(2)), now).map { it.tripId })
    }

    @Test fun orderedByRealTimeThenScheduled() {
        val a = d(1, 60)   // +5 -> 6 min
        val b = d(2, 180)  // on time -> 3 min
        val c = d(3, 0)    // on time -> 0
        val e = d(4, -60)  // +4 -> 3 min, ties with b: scheduled first
        val out = visibleDepartures(listOf(a, b, c, e), mapOf(1L to Duration.ofMinutes(5), 4L to Duration.ofMinutes(4)), now)
        assertEquals(listOf(3L, 4L, 2L, 1L), out.map { it.tripId })
    }

    @Test fun defaultLimitIsThirty() {
        val out = visibleDepartures((1L..40L).map { d(it, it * 10) }, emptyMap(), now)
        assertEquals(30, out.size)
    }

    @Test fun limitAppliedAfterResort() {
        val first = d(1, 60)  // delayed far
        val out = visibleDepartures(listOf(first, d(2, 120), d(3, 180)), mapOf(1L to Duration.ofMinutes(30)), now, limit = 2)
        assertEquals(listOf(2L, 3L), out.map { it.tripId })
    }

    @Test fun withoutDelaysKeepsJustDepartedThenUpcoming() {
        val out = visibleDepartures(listOf(d(3, 10), d(1, -1), d(2, 0), d(4, -61)), emptyMap(), now)
        assertEquals(listOf(1L, 2L, 3L), out.map { it.tripId })
    }

    @Test fun busLeftThirtySecondsAgoIsKeptAndPassed() {
        val gone = d(1, -30)
        val out = visibleDepartures(listOf(gone, d(2, 120)), emptyMap(), now)
        assertEquals(listOf(1L, 2L), out.map { it.tripId })
        assertEquals(true, isPassed(gone, null, now))
        assertEquals(false, isPassed(d(2, 120), null, now))
    }

    @Test fun busLeftNinetySecondsAgoIsDropped() {
        assertEquals(emptyList<Long>(), visibleDepartures(listOf(d(1, -90)), emptyMap(), now).map { it.tripId })
    }

    @Test fun lateBusWithFutureRealTimeIsNotPassed() {
        val late = d(1, -120)
        val delay = Duration.ofMinutes(5)
        assertEquals(listOf(1L), visibleDepartures(listOf(late), mapOf(1L to delay), now).map { it.tripId })
        assertEquals(false, isPassed(late, delay, now))
    }

    @Test fun passedUsesRealTimeNotScheduled() {
        val b = d(1, -210) // scheduled 3.5 min ago, +3 -> left 30 s ago
        assertEquals(true, isPassed(b, Duration.ofMinutes(3), now))
        assertEquals(listOf(1L), visibleDepartures(listOf(b), mapOf(1L to Duration.ofMinutes(3)), now).map { it.tripId })
    }

    @Test fun passedRowsOrderedByRealTime() {
        val out = visibleDepartures(listOf(d(1, 60), d(2, -20), d(3, -50)), emptyMap(), now)
        assertEquals(listOf(3L, 2L, 1L), out.map { it.tripId })
    }
}
