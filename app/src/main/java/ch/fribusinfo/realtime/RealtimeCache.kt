package ch.fribusinfo.realtime

import android.util.Log
import ch.fribusinfo.data.Departure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant

/** Per-stop cache (30 s) in front of [StationboardClient]. After HTTP 429 nothing is requested
 *  for 5 minutes; every failure yields an empty list, i.e. the theoretical timetable. */
class RealtimeCache(
    private val client: StationboardClient,
    private val clock: () -> Instant = Instant::now,
    private val log: (String, Throwable?) -> Unit = { m, e -> Log.w("RealtimeCache", m, e) },
) {
    private class Entry(val at: Instant, val list: List<RtDeparture>)

    private val mutex = Mutex()
    private val entries = HashMap<Long, Entry>()
    private var backoffUntil: Instant = Instant.MIN

    private suspend fun board(stopId: Long): List<RtDeparture> = mutex.withLock {
        val now = clock()
        if (now.isBefore(backoffUntil)) return emptyList()
        entries[stopId]?.let { if (Duration.between(it.at, now) < TTL) return it.list }
        val result = client.fetch(stopId)
        val list = result.getOrElse { e ->
            if (e is CancellationException) throw e
            if (e is StationboardClient.TooManyRequests) backoffUntil = now.plus(BACKOFF)
            log("stationboard $stopId failed", e)
            emptyList()
        }
        entries[stopId] = Entry(now, list)
        list
    }

    /** Emits the delays of [local] now, then again every 30 s, until the collector stops
     *  (callers collect only while the screen is STARTED). */
    fun delays(stopId: Long, local: List<Departure>): Flow<Map<Long, Duration>> = flow {
        while (true) {
            emit(RealtimeMatcher.match(local, board(stopId)))
            delay(TTL.toMillis())
        }
    }

    private companion object {
        val TTL: Duration = Duration.ofSeconds(30)
        val BACKOFF: Duration = Duration.ofMinutes(5)
    }
}
