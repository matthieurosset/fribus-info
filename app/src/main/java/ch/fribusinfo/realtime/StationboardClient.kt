package ch.fribusinfo.realtime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** One departure of the public transport.opendata.ch stationboard. [expected] is null when the
 *  API has no prognosis for it. */
data class RtDeparture(val line: String, val to: String, val scheduled: ZonedDateTime, val expected: ZonedDateTime?, val category: String = "")

private val zurich = ZoneId.of("Europe/Zurich")
private val fromFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private val apiTime = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")

private fun JSONObject.optTime(name: String): ZonedDateTime? {
    if (isNull(name)) return null
    return try {
        OffsetDateTime.parse(getString(name), apiTime).atZoneSameInstant(zurich)
    } catch (e: Exception) {
        null
    }
}

/** Entries without a scheduled departure (arrival-only rows) are skipped. */
fun parseStationboard(json: String): List<RtDeparture> {
    val board = JSONObject(json).optJSONArray("stationboard") ?: return emptyList()
    val out = ArrayList<RtDeparture>(board.length())
    for (i in 0 until board.length()) {
        val e = board.getJSONObject(i)
        val stop = e.optJSONObject("stop") ?: continue
        val scheduled = stop.optTime("departure") ?: continue
        val expected = stop.optJSONObject("prognosis")?.optTime("departure")
        out += RtDeparture(e.optString("number", ""), e.optString("to", ""), scheduled, expected, e.optString("category", ""))
    }
    return out
}

open class StationboardClient(
    private val baseUrl: String = "https://transport.opendata.ch/v1/",
    private val clock: () -> ZonedDateTime = { ZonedDateTime.now(zurich) },
) {
    /** HTTP 429: the caller must back off. */
    class TooManyRequests : IOException("HTTP 429")

    /** Without `datetime` the API starts at the current time by SCHEDULED time and omits a late
     *  bus whose scheduled time has passed; asking from 60 min back (the local look-back of the departures screen) returns it with its
     *  prognosis. */
    internal fun buildUrl(stopId: Long, limit: Int, now: ZonedDateTime): String {
        val from = now.withZoneSameInstant(zurich).minusMinutes(60).format(fromFormat)
        return "${baseUrl}stationboard?id=$stopId&limit=$limit&datetime=${URLEncoder.encode(from, "UTF-8")}"
    }

    open suspend fun fetch(stopId: Long, limit: Int = 150): Result<List<RtDeparture>> = withContext(Dispatchers.IO) {
        try {
            val conn = URL(buildUrl(stopId, limit, clock())).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 8_000
                conn.readTimeout = 8_000
                when (val code = conn.responseCode) {
                    200 -> Result.success(parseStationboard(conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }))
                    429 -> Result.failure(TooManyRequests())
                    else -> Result.failure(IOException("HTTP $code"))
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
