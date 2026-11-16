package ch.fribusinfo.data

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TimetableRepositoryTest {
    private val zone = ZoneId.of("Europe/Zurich")
    private lateinit var db: SQLiteDatabase
    private lateinit var repo: TimetableRepository

    @Before fun setUp() {
        val ctx = InstrumentationRegistry.getInstrumentation().context
        val out = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "test.db")
        ctx.assets.open("test.db").use { i -> out.outputStream().use { i.copyTo(it) } }
        db = SQLiteDatabase.openDatabase(out.path, null, SQLiteDatabase.OPEN_READONLY)
        repo = TimetableRepository(db, zone)
    }

    @After fun tearDown() = db.close()

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int) =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, zone)

    @Test fun departuresAfterMidnightComeFromPreviousServiceDay() {
        // Tuesday 2026-06-02 00:30; T2 runs Monday with dep 24:45 (= 89100) at Petit-Moncor
        val deps = repo.departures(8504100, at(2026, 6, 2, 0, 30))
        assertEquals(1, deps.size)
        val d = deps[0]
        assertEquals(at(2026, 6, 2, 0, 45), d.scheduled)
        assertEquals(LocalDate.of(2026, 6, 1), d.serviceDate)
        assertEquals("2", d.line.label)
        assertEquals("Marly Gare", d.headsign)
        assertEquals("Fribourg", d.headsignCommune)
    }

    @Test fun departuresAreSortedAndMerged() {
        // Monday 2026-06-01 from 07:00 over 12 h at Tilleul (two platforms collapse)
        val deps = repo.departures(8587255, at(2026, 6, 1, 7, 0), horizon = Duration.ofHours(12))
        // T6 terminates at Tilleul: an arrival, not a departure
        assertEquals(listOf("2", "1"), deps.map { it.line.label })
        assertEquals(deps.sortedBy { it.scheduled }, deps)
    }

    @Test fun departuresSkipInactiveDays() {
        // Saturday: service TA is Mon-Fri only
        assertTrue(repo.departures(8587255, at(2026, 6, 6, 7, 0)).isEmpty())
    }

    @Test fun departuresRespectLimit() {
        val deps = repo.departures(8587255, at(2026, 6, 1, 7, 0), limit = 2, horizon = Duration.ofHours(12))
        assertEquals(2, deps.size)
    }

    @Test fun nearbySortedByDistanceAndWithin2km() {
        val res = repo.nearby(46.8060, 7.1530)
        val ids = res.map { it.stop.id }
        assertEquals(listOf(8587255L, 8592385L, 8530013L), ids)
        assertTrue(8504100L !in ids) // ~3 km away
        val dists = res.map { it.distanceMeters!! }
        assertEquals(dists.sorted(), dists)
        assertTrue(dists.all { it <= 2000 })
    }

    @Test fun searchIsAccentInsensitive() {
        assertTrue(repo.search("tilleul").any { it.stop.id == 8587255L })
        assertTrue(repo.search("cathedrale").any { it.stop.id == 8587255L })
        assertTrue(repo.search("CATHÉDRALE").any { it.stop.id == 8587255L })
        assertTrue(repo.search("zzzz").isEmpty())
    }

    @Test fun linesFollowSortOrder() {
        val s = repo.stop(8587255)
        assertNotNull(s)
        assertEquals(listOf("1", "2", "6"), s!!.lines.map { it.label })
        assertEquals("Tilleul/Cathédrale", s.stop.name)
        assertEquals("Fribourg", s.stop.commune)
        assertNull(s.distanceMeters)
    }

    @Test fun sameNameStopsAreDistinct() {
        assertEquals("Neuveville/Motta", repo.stop(8592385)!!.stop.name)
        assertEquals("Fribourg-Neuveville/Motta", repo.stop(8530013)!!.stop.name)
        assertNull(repo.stop(1))
    }

    @Test fun tripListsStopsInOrderWithTimes() {
        val dep = repo.departures(8587255, at(2026, 6, 1, 7, 0)).first { it.line.label == "2" }
        val stops = repo.trip(dep.tripId, dep.serviceDate)
        assertEquals(listOf(8587255L, 8592385L, 8504100L, 8592385L), stops.map { it.stop.stop.id })
        assertEquals(listOf(1, 2, 3, 4), stops.map { it.seq })
        assertEquals(at(2026, 6, 1, 8, 0), stops[0].scheduled)
        assertEquals(at(2026, 6, 2, 0, 45), stops[2].scheduled)
        assertEquals(at(2026, 6, 2, 0, 50), stops[3].scheduled)
    }
}
