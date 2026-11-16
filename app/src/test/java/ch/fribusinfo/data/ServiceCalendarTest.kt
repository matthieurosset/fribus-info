package ch.fribusinfo.data

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceCalendarTest {
    // service 1: Mon-Fri (bits 0..4), 2026-01-01..2026-12-31; with exceptions
    private val cal = ServiceCalendar(
        rules = mapOf(1L to ServiceCalendar.Rule(20260101, 20261231, 0b0011111)),
        exceptions = mapOf(
            1L to mapOf(
                20260304 to 2, // Wednesday removed
                20260307 to 1, // Saturday added
            ),
        ),
    )

    @Test fun weekdayInRangeIsActive() {
        assertTrue(cal.isActive(1, LocalDate.of(2026, 3, 2))) // Monday
    }

    @Test fun weekendIsInactive() {
        assertFalse(cal.isActive(1, LocalDate.of(2026, 3, 8))) // Sunday
    }

    @Test fun outOfRangeIsInactive() {
        assertFalse(cal.isActive(1, LocalDate.of(2025, 12, 31)))
        assertFalse(cal.isActive(1, LocalDate.of(2027, 1, 4)))
    }

    @Test fun removedExceptionOnActiveDay() {
        assertFalse(cal.isActive(1, LocalDate.of(2026, 3, 4)))
    }

    @Test fun addedExceptionOnInactiveDay() {
        assertTrue(cal.isActive(1, LocalDate.of(2026, 3, 7)))
    }

    @Test fun unknownServiceIsInactive() {
        assertFalse(cal.isActive(99, LocalDate.of(2026, 3, 2)))
    }
}
