package ch.fribusinfo.realtime

import ch.fribusinfo.data.Departure
import ch.fribusinfo.data.Line
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class RealtimeMatcherTest {
    private val z = ZoneId.of("Europe/Zurich")
    private fun at(h: Int, m: Int, s: Int = 0) = ZonedDateTime.of(2026, 11, 17, h, m, s, 0, z)
    private fun local(id: Long, line: String, head: String, commune: String, t: ZonedDateTime) =
        Departure(id, Line(1, line, 0, 0, false), head, commune, LocalDate.of(2026, 11, 17), 1, t)
    private fun rt(line: String, to: String, t: ZonedDateTime, exp: ZonedDateTime?) = RtDeparture(line, to, t, exp)
    private val none = emptyMap<Long, Duration>()

    @Test fun sameLineMinuteAndDestinationGivesDelay() {
        val r = RealtimeMatcher.match(
            listOf(local(1, "2", "Schönberg Dunant", "Fribourg", at(12, 0))),
            listOf(rt("2", "Fribourg, Schönberg Dunant", at(12, 0), at(12, 3))),
        )
        assertEquals(mapOf(1L to Duration.ofMinutes(3)), r)
    }

    @Test fun accentsCaseAndCommunePrefixIgnored() {
        val r = RealtimeMatcher.match(
            listOf(local(1, "2", "SCHONBERG  dunant", "Fribourg", at(12, 0))),
            listOf(rt("2", "Freiburg, Schönberg-Dunant", at(12, 0), at(12, 1))),
        )
        assertEquals(mapOf(1L to Duration.ofMinutes(1)), r)
    }

    @Test fun secondsOfScheduledTimeIgnored() {
        val r = RealtimeMatcher.match(
            listOf(local(1, "2", "A", "B", at(12, 0, 0))),
            listOf(rt("2", "B, A", at(12, 0, 0), at(12, 2, 30))),
        )
        assertEquals(Duration.ofSeconds(150), r[1L])
    }

    @Test fun differentDestinationButUniqueLineMinuteStillMatches() {
        val r = RealtimeMatcher.match(
            listOf(local(1, "1", "Gare routière", "Fribourg", at(12, 0))),
            listOf(rt("1", "Fribourg/Freiburg, gare rout.", at(12, 0), at(12, 2))),
        )
        assertEquals(mapOf(1L to Duration.ofMinutes(2)), r)
    }

    @Test fun twoCandidatesNoDestinationMatchGivesNoMatch() {
        val r = RealtimeMatcher.match(
            listOf(local(1, "2", "Inconnu", "Ailleurs", at(12, 0))),
            listOf(
                rt("2", "Villars-sur-Glâne, Les Dailles", at(12, 0), at(12, 4)),
                rt("2", "Fribourg, Schönberg Dunant", at(12, 0), at(12, 1)),
            ),
        )
        assertEquals(none, r)
    }

    @Test fun twoCandidatesPickTheOneWithMatchingDestination() {
        val r = RealtimeMatcher.match(
            listOf(local(1, "2", "Les Dailles", "Villars-sur-Glâne", at(12, 0))),
            listOf(
                rt("2", "Fribourg, Schönberg Dunant", at(12, 0), at(12, 1)),
                rt("2", "Villars-sur-Glâne, Les Dailles", at(12, 0), at(12, 4)),
            ),
        )
        assertEquals(mapOf(1L to Duration.ofMinutes(4)), r)
    }

    @Test fun twoLocalsOneRealtimeNeverShiftsTheWrongBus() {
        val r = RealtimeMatcher.match(
            listOf(
                local(1, "2", "Les Dailles", "Villars-sur-Glâne", at(12, 0)),
                local(2, "2", "Autre", "Ailleurs", at(12, 0)),
            ),
            listOf(rt("2", "Villars-sur-Glâne, Les Dailles", at(12, 0), at(12, 4))),
        )
        assertEquals(mapOf(1L to Duration.ofMinutes(4)), r)
    }

    @Test fun nullExpectedGivesNoEntry() {
        val r = RealtimeMatcher.match(
            listOf(local(1, "2", "A", "B", at(12, 0))),
            listOf(rt("2", "B, A", at(12, 0), null)),
        )
        assertEquals(none, r)
    }

    @Test fun otherLineOrMinuteIgnored() {
        val r = RealtimeMatcher.match(
            listOf(local(1, "2", "A", "B", at(12, 0))),
            listOf(rt("1", "B, A", at(12, 0), at(12, 3)), rt("2", "B, A", at(12, 1), at(12, 3))),
        )
        assertEquals(none, r)
    }

    @Test fun unknownTripIgnored() {
        assertEquals(none, RealtimeMatcher.match(emptyList(), listOf(rt("2", "x", at(12, 0), at(12, 5)))))
    }

    @Test fun funicularHasNoRealtimeEntryAndNoDelay() {
        // The API returns an empty stationboard for the funicular stop (8530013).
        val r = RealtimeMatcher.match(listOf(local(1, "FUN", "Basse-Ville", "Fribourg", at(12, 0))), emptyList())
        assertEquals(none, r)
    }

    @Test fun trainWithSameNumberAndMinuteNeverMatchesABus() {
        val r = RealtimeMatcher.match(
            listOf(local(1, "1", "Gérine", "Marly", at(12, 0))),
            listOf(RtDeparture("1", "Romont FR", at(12, 0), at(12, 9), "S")),
        )
        assertEquals(none, r)
    }

    @Test fun busCategoryStillMatches() {
        val r = RealtimeMatcher.match(
            listOf(local(1, "1", "Gérine", "Marly", at(12, 0))),
            listOf(
                RtDeparture("1", "Romont FR", at(12, 0), at(12, 9), "S"),
                RtDeparture("1", "Marly, Gérine", at(12, 0), at(12, 2), "B"),
            ),
        )
        assertEquals(mapOf(1L to Duration.ofMinutes(2)), r)
    }
}
