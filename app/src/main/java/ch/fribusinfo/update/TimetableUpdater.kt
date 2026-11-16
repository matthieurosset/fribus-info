package ch.fribusinfo.update

import android.content.Context
import ch.fribusinfo.data.TimetableDb
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

fun interface Fetcher {
    /** Blocking GET returning the body; throws on any network or HTTP error. */
    fun get(url: String): ByteArray
}

/** Production [Fetcher]: HttpURLConnection, redirects followed (GitHub release assets answer
 *  with a 302 to another https host), 15 s connect / 60 s read timeouts. */
class HttpFetcher : Fetcher {
    override fun get(url: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000
            c.readTimeout = 60_000
            c.instanceFollowRedirects = true
            c.setRequestProperty("Accept-Encoding", "identity")
            check(c.responseCode == 200) { "HTTP ${c.responseCode}" }
            return c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }
}

/** Checks at most once per 24 h whether a newer timetable DB is published and, if so, swaps it
 *  in. Every failure keeps the installed DB and stays silent.
 *
 *  Swap: the download goes to `timetable.db.download` (not a file [TimetableDb] uses), is
 *  validated (sha256, opens as SQLite, meta.version == version.json's, strictly newer than the
 *  installed one), then atomically renamed over the live file. Nothing is closed: on Android
 *  (Linux) an already-open handle keeps pointing at the old inode, which stays valid until the
 *  handle is closed, so running queries are unaffected; [onInstalled] (the holder's `replace`)
 *  then opens the new file and publishes a new repository. */
class TimetableUpdater(
    private val context: Context,
    private val fetcher: Fetcher,
    private val clock: Clock = Clock.systemUTC(),
    private val onInstalled: suspend () -> Unit = {},
    private val fileName: String = TimetableDb.FILE,
    private val baseUrl: String = BASE_URL,
) {
    private val prefs = context.getSharedPreferences("timetable_update", Context.MODE_PRIVATE)
    private var attempted = false // at most one network attempt per app start

    /** True when a newer database was installed. */
    suspend fun checkIfDue(): Boolean = withContext(Dispatchers.IO) {
        synchronized(this@TimetableUpdater) {
            if (attempted) return@withContext false
            if (clock.millis() - prefs.getLong(KEY, 0L) < DAY_MS) return@withContext false
            attempted = true
        }
        try {
            val info = JSONObject(String(fetcher.get("$baseUrl/version.json"), Charsets.UTF_8))
            val version = info.getString("version")
            val sha = info.getString("sha256")
            val target = File(context.filesDir, fileName)
            val installed = TimetableDb.installLock.withLock {
                if (target.exists()) TimetableDb.versionOf(target) else null
            }
            if (installed != null && version <= installed) {
                markChecked()
                return@withContext false
            }
            val bytes = fetcher.get("$baseUrl/timetable.db")
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
            if (!hash.equals(sha, ignoreCase = true)) return@withContext false
            val download = File(context.filesDir, "$fileName.download")
            val swapped = try {
                download.writeBytes(bytes)
                TimetableDb.installLock.withLock {
                    // re-read under the lock: the bundled install may have run meanwhile
                    val current = if (target.exists()) TimetableDb.versionOf(target) else null
                    if (TimetableDb.versionOf(download) != version) false
                    else if (current != null && version <= current) false
                    else {
                        Files.move(
                            download.toPath(), target.toPath(),
                            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING,
                        )
                        true
                    }
                }
            } finally {
                download.delete()
            }
            if (!swapped) return@withContext false
            markChecked()
            onInstalled()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false // offline or bad data: keep what we have, retry at next launch
        }
    }

    private fun markChecked() {
        prefs.edit().putLong(KEY, clock.millis()).apply()
    }

    companion object {
        const val BASE_URL =
            "https://github.com/matthieurosset/fribus-info/releases/download/timetable"
        private const val KEY = "last_check_ms"
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
