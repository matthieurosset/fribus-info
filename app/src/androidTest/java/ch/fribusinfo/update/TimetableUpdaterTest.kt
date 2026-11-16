package ch.fribusinfo.update

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import ch.fribusinfo.data.TimetableDb
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// Instrumented (not a JVM unit test): validation opens real SQLite files, which the plain JVM
// unit-test classpath does not provide.
class TimetableUpdaterTest {
    private val ctx: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "updater-test.db"
    private val live get() = File(ctx.filesDir, name)
    private val download get() = File(ctx.filesDir, "$name.download")

    private var nowMs = 1_800_000_000_000L
    private val clock = object : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
        override fun instant(): Instant = Instant.ofEpochMilli(nowMs)
    }

    private class FakeFetcher(var files: Map<String, ByteArray>) : Fetcher {
        val requests = mutableListOf<String>()
        var fail = false
        override fun get(url: String): ByteArray {
            requests += url
            if (fail) throw IOException("offline")
            return files[url.substringAfterLast('/')] ?: throw IOException("404")
        }
    }

    private fun dbBytes(version: String): ByteArray {
        val f = File(ctx.cacheDir, "make-$version.db")
        f.delete()
        SQLiteDatabase.openOrCreateDatabase(f, null).use {
            it.execSQL("CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT)")
            it.execSQL("INSERT INTO meta VALUES('version', '$version')")
        }
        return f.readBytes().also { f.delete() }
    }

    private fun sha(b: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private fun versionJson(version: String, bytes: ByteArray, sha: String = sha(bytes)) =
        """{"version":"$version","sha256":"$sha","bytes":${bytes.size}}""".toByteArray()

    private fun fetcherFor(version: String, advertised: String = version): FakeFetcher {
        val db = dbBytes(version)
        return FakeFetcher(mapOf("version.json" to versionJson(advertised, db), "timetable.db" to db))
    }

    private var installedCalls = 0
    private fun updater(f: Fetcher) =
        TimetableUpdater(ctx, f, clock, { installedCalls++ }, name)

    @Before fun setUp() {
        live.delete(); download.delete()
        ctx.getSharedPreferences("timetable_update", 0).edit().clear().commit()
        live.writeBytes(dbBytes("202601010000"))
        installedCalls = 0
    }

    @After fun tearDown() {
        live.delete(); download.delete()
        ctx.getSharedPreferences("timetable_update", 0).edit().clear().commit()
    }

    @Test fun newerVersionIsInstalledAndOldHandleKeepsWorking() = runBlocking {
        val old = SQLiteDatabase.openDatabase(live.path, null, SQLiteDatabase.OPEN_READONLY)
        val f = fetcherFor("202611161654")
        assertTrue(updater(f).checkIfDue())
        assertEquals("202611161654", TimetableDb.versionOf(live))
        assertEquals(1, installedCalls)
        assertFalse(download.exists())
        // the handle opened before the rename still reads the old inode
        old.rawQuery("SELECT value FROM meta WHERE key='version'", null).use {
            it.moveToFirst(); assertEquals("202601010000", it.getString(0))
        }
        old.close()
    }

    @Test fun notDueWithinTwentyFourHours() = runBlocking {
        val f = fetcherFor("202611161654")
        assertTrue(updater(f).checkIfDue())
        nowMs += 23 * 3600_000L
        val tooSoon = fetcherFor("202701010000")
        assertFalse(updater(tooSoon).checkIfDue())
        assertTrue(tooSoon.requests.isEmpty())
        nowMs += 2 * 3600_000L
        val f2 = fetcherFor("202701010000")
        assertTrue(updater(f2).checkIfDue())
        assertEquals("202701010000", TimetableDb.versionOf(live))
    }

    @Test fun sameVersionDownloadsNothingButCountsAsChecked() = runBlocking {
        val f = fetcherFor("202601010000")
        assertFalse(updater(f).checkIfDue())
        assertEquals(listOf("version.json"), f.requests.map { it.substringAfterLast('/') })
        f.requests.clear()
        assertFalse(updater(f).checkIfDue())
        assertTrue(f.requests.isEmpty()) // checked, so not due again
    }

    @Test fun olderRemoteVersionIsIgnored() = runBlocking {
        val f = fetcherFor("202501010000")
        assertFalse(updater(f).checkIfDue())
        assertEquals("202601010000", TimetableDb.versionOf(live))
    }

    @Test fun badHashLeavesOldDbUntouched() = runBlocking {
        val db = dbBytes("202611161654")
        val f = FakeFetcher(mapOf("version.json" to versionJson("202611161654", db, "00".repeat(32)), "timetable.db" to db))
        val before = live.readBytes()
        assertFalse(updater(f).checkIfDue())
        assertArrayEquals(before, live.readBytes())
        assertFalse(download.exists())
        assertEquals(0, installedCalls)
    }

    @Test fun corruptFileWithMatchingHashLeavesOldDbUntouched() = runBlocking {
        val junk = ByteArray(5000) { (it * 31).toByte() }
        val f = FakeFetcher(mapOf("version.json" to versionJson("202611161654", junk), "timetable.db" to junk))
        val before = live.readBytes()
        assertFalse(updater(f).checkIfDue())
        assertArrayEquals(before, live.readBytes())
        assertFalse(download.exists())
    }

    @Test fun metaVersionMismatchLeavesOldDbUntouched() = runBlocking {
        val f = fetcherFor("202611161654", advertised = "202612010000")
        val before = live.readBytes()
        assertFalse(updater(f).checkIfDue())
        assertArrayEquals(before, live.readBytes())
    }

    @Test fun networkErrorIsSilentAndRetriesAtNextLaunchButNotSooner() = runBlocking {
        val f = fetcherFor("202611161654")
        f.fail = true
        val u = updater(f)
        assertFalse(u.checkIfDue())
        assertEquals(1, f.requests.size)
        // same app start: no second attempt even though the failure did not mark a check
        f.fail = false
        assertFalse(u.checkIfDue())
        assertEquals(1, f.requests.size)
        // next launch (new instance): tries again and succeeds
        assertTrue(updater(f).checkIfDue())
        assertEquals("202611161654", TimetableDb.versionOf(live))
    }
}
