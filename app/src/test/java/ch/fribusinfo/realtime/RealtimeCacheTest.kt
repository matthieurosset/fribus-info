package ch.fribusinfo.realtime

import ch.fribusinfo.data.Departure
import ch.fribusinfo.data.Line
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class RealtimeCacheTest {
    private val z = ZoneId.of("Europe/Zurich")
    private val t = ZonedDateTime.of(2026, 11, 17, 12, 0, 0, 0, z)
    private val local = listOf(Departure(7, Line(1, "2", 0, 0, false), "A", "B", LocalDate.of(2026, 11, 17), 1, t))
    private val none = emptyMap<Long, Duration>()

    private class Fake(var result: () -> Result<List<RtDeparture>>) : StationboardClient() {
        var calls = 0
        override suspend fun fetch(stopId: Long, limit: Int): Result<List<RtDeparture>> { calls++; return result() }
    }

    private var now = Instant.parse("2026-11-17T11:00:00Z")
    private val ok = { Result.success(listOf(RtDeparture("2", "B, A", t, t.plusMinutes(2)))) }

    @Test fun secondCallWithin30sDoesNotHitClient() = runBlocking {
        val c = Fake(ok); val cache = RealtimeCache(c) { now }
        assertEquals(Duration.ofMinutes(2), cache.delays(1, local).first()[7])
        now = now.plusSeconds(29)
        assertEquals(Duration.ofMinutes(2), cache.delays(1, local).first()[7])
        assertEquals(1, c.calls)
        now = now.plusSeconds(2)
        cache.delays(1, local).first()
        assertEquals(2, c.calls)
    }

    @Test fun stopsAreCachedSeparately() = runBlocking {
        val c = Fake(ok); val cache = RealtimeCache(c) { now }
        cache.delays(1, local).first(); cache.delays(2, local).first()
        assertEquals(2, c.calls)
    }

    @Test fun tooManyRequestsBacksOffFiveMinutesWithEmptyMap() = runBlocking {
        val c = Fake { Result.failure(StationboardClient.TooManyRequests()) }
        val cache = RealtimeCache(c) { now }
        assertEquals(none, cache.delays(1, local).first())
        now = now.plusSeconds(299)
        assertEquals(none, cache.delays(1, local).first())
        assertEquals(none, cache.delays(2, local).first())
        assertEquals(1, c.calls)
        c.result = ok
        now = now.plusSeconds(2)
        assertEquals(Duration.ofMinutes(2), cache.delays(1, local).first()[7])
        assertEquals(2, c.calls)
    }

    @Test fun otherErrorGivesEmptyMapWithoutThrowing() = runBlocking {
        val c = Fake { Result.failure(java.io.IOException("offline")) }
        val cache = RealtimeCache(c) { now }
        assertEquals(none, cache.delays(1, local).first())
    }

    @Test fun recoversAfterOtherError() = runBlocking {
        val c = Fake { Result.failure(java.io.IOException("offline")) }
        val cache = RealtimeCache(c) { now }
        cache.delays(1, local).first()
        c.result = ok; now = now.plusSeconds(31)
        assertEquals(Duration.ofMinutes(2), cache.delays(1, local).first()[7])
    }
}
