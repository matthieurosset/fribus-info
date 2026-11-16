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
}
