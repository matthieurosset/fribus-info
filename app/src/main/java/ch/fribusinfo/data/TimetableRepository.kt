package ch.fribusinfo.data

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.cos

/** Every method does disk I/O (the first call also loads the calendar, lines and stop-lines
 *  tables): call them off the main thread, e.g. on Dispatchers.IO. */
class TimetableRepository(
    private val db: SQLiteDatabase,
    private val zone: ZoneId = ZoneId.of("Europe/Zurich"),
) {
    /** Loads the lazy tables now; call on IO right after opening. */
    fun warmUp() {
        version
        calendar
        lines
        lineOrder
        stopLines
    }

    /** `meta.version` of the database this repository reads (empty if absent). Routes carry it so a
     *  screen opened on an older database does not reuse its ids on a newer one. */
    val version: String by lazy {
        db.rawQuery("SELECT value FROM meta WHERE key = 'version'", null).use { c ->
            if (c.moveToFirst()) c.getString(0) else ""
        }
    }

    private val calendar by lazy { ServiceCalendar.load(db) }
    private val lines: Map<Int, Line> by lazy {
        val m = LinkedHashMap<Int, Line>()
        db.rawQuery("SELECT id, label, bg, fg, bordered FROM line ORDER BY sort", null).use { c ->
            while (c.moveToNext()) {
                m[c.getInt(0)] = Line(c.getInt(0), c.getString(1), c.getInt(2), c.getInt(3), c.getInt(4) != 0)
            }
        }
        m
    }
    private val lineOrder: Map<Int, Int> by lazy { lines.keys.withIndex().associate { it.value to it.index } }
    private val stopLines: Map<Long, List<Line>> by lazy {
        val m = HashMap<Long, MutableList<Line>>()
        db.rawQuery("SELECT stop_id, line_id FROM stop_line", null).use { c ->
            while (c.moveToNext()) {
                val line = lines[c.getInt(1)] ?: continue
                m.getOrPut(c.getLong(0)) { ArrayList() }.add(line)
            }
        }
        m.values.forEach { l -> l.sortBy { lineOrder[it.id] } }
        m
    }

    private fun stopOf(c: Cursor): StopWithLines {
        val id = c.getLong(0)
        return StopWithLines(
            Stop(id, c.getString(1), c.getString(2), c.getDouble(3), c.getDouble(4)),
            stopLines[id].orEmpty(),
            null,
        )
    }

    private fun stops(where: String = "", args: Array<String>? = null): List<StopWithLines> =
        db.rawQuery("SELECT id, name, commune, lat, lon FROM stop $where", args).use { c ->
            buildList { while (c.moveToNext()) add(stopOf(c)) }
        }

    fun stop(id: Long): StopWithLines? = stops("WHERE id = ?", arrayOf(id.toString())).firstOrNull()

    /** Stops inside the ±[radius] m square around the position (corners reach about 707 m),
     *  nearest first, no count limit. Distances are the straight-line meters, truncated. */
    fun nearby(lat: Double, lon: Double, radius: Int = NEARBY_RADIUS_METERS): List<StopWithLines> {
        val dLat = radius * 1.05 / METERS_PER_DEGREE_BOX
        val dLon = radius * 1.05 / (METERS_PER_DEGREE_BOX * cos(Math.toRadians(lat)).coerceAtLeast(0.01))
        return stops(
            "WHERE lat BETWEEN ? AND ? AND lon BETWEEN ? AND ?",
            arrayOf((lat - dLat).toString(), (lat + dLat).toString(), (lon - dLon).toString(), (lon + dLon).toString()),
        ).filter { isInNearbySquare(lat, lon, it.stop.lat, it.stop.lon, radius) }
            .map { it.copy(distanceMeters = haversineMeters(lat, lon, it.stop.lat, it.stop.lon).toInt()) }
            .sortedBy { it.distanceMeters }
    }

    /** Multi-word search, see [searchStops]. */
    fun search(query: String): List<StopWithLines> = searchStops(stops(), query)

    /** Departures from [from] within [horizon]. Times past 24:00 belong to the previous service
     *  day, so the service dates yesterday (only dep >= 86400 can fall in the window) and today
     *  are both queried, then merged and sorted by scheduled time. The last stop of a trip is
     *  an arrival only and is not listed. */
    fun departures(stopId: Long, from: ZonedDateTime, limit: Int = 60, horizon: Duration = Duration.ofHours(6)): List<Departure> {
        val end = from.plus(horizon)
        var date = from.withZoneSameInstant(zone).toLocalDate().minusDays(1)
        val last = end.withZoneSameInstant(zone).toLocalDate()
        val result = ArrayList<Departure>()
        while (!date.isAfter(last)) {
            val base = serviceDayStart(date)
            val lo = Duration.between(base, from).seconds.coerceAtLeast(0)
            val hi = Duration.between(base, end).seconds
            if (hi > 0) queryDepartures(stopId, date, lo, hi, result)
            date = date.plusDays(1)
        }
        result.sortBy { it.scheduled }
        return result.take(limit)
    }

    // GTFS: stop times count from noon minus 12 h of the service date.
    private fun serviceDayStart(date: LocalDate): ZonedDateTime =
        date.atTime(12, 0).atZone(zone).minusHours(12)

    private fun queryDepartures(stopId: Long, date: LocalDate, lo: Long, hi: Long, out: MutableList<Departure>) {
        val sql = """
            SELECT st.trip_id, st.seq, st.dep, t.line_id, t.service_id, t.headsign, t.headsign_commune
            FROM stop_time st JOIN trip t ON t.id = st.trip_id
            WHERE st.stop_id = ? AND st.dep >= ? AND st.dep < ?
              AND st.seq < (SELECT max(seq) FROM stop_time WHERE trip_id = st.trip_id)
            ORDER BY st.dep"""
        val base = serviceDayStart(date)
        db.rawQuery(sql, arrayOf(stopId.toString(), lo.toString(), hi.toString())).use { c ->
            while (c.moveToNext()) {
                val line = lines[c.getInt(3)] ?: continue
                if (!calendar.isActive(c.getLong(4), date)) continue
                out.add(
                    Departure(
                        c.getLong(0), line, c.getString(5), c.getString(6), date,
                        c.getInt(1), base.plusSeconds(c.getLong(2)),
                    ),
                )
            }
        }
    }

    /** Line and headsign of a trip, or null if the trip is unknown. */
    fun tripHeader(tripId: Long): TripHeader? =
        db.rawQuery("SELECT line_id, headsign, headsign_commune FROM trip WHERE id = ?", arrayOf(tripId.toString())).use { c ->
            if (!c.moveToFirst()) return null
            val line = lines[c.getInt(0)] ?: return null
            TripHeader(line, c.getString(1), c.getString(2))
        }

    /** Every stop of a trip run on [serviceDate], in order. */
    fun trip(tripId: Long, serviceDate: LocalDate): List<TripStop> {
        val rows = ArrayList<Triple<Long, Int, Long>>()
        db.rawQuery("SELECT stop_id, seq, dep FROM stop_time WHERE trip_id = ? ORDER BY seq", arrayOf(tripId.toString())).use { c ->
            while (c.moveToNext()) rows.add(Triple(c.getLong(0), c.getInt(1), c.getLong(2)))
        }
        val base = serviceDayStart(serviceDate)
        val cache = HashMap<Long, StopWithLines?>()
        return rows.mapNotNull { (sid, seq, dep) ->
            val s = cache.getOrPut(sid) { stop(sid) } ?: return@mapNotNull null
            TripStop(s, seq, base.plusSeconds(dep))
        }
    }
}

private const val METERS_PER_DEGREE_BOX = 111_000.0
