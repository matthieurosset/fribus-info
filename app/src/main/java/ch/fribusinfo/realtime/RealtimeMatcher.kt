package ch.fribusinfo.realtime

import ch.fribusinfo.data.Departure
import java.text.Normalizer
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** Matches local (timetable) departures to API departures. A wrong match would shift the wrong
 *  bus, so anything ambiguous is left out. */
object RealtimeMatcher {
    private val zurich = ZoneId.of("Europe/Zurich")
    private val commune = Regex("^(fribourg|freiburg)(/(fribourg|freiburg))?\\s*,\\s*")
    private val accents = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val nonAlnum = Regex("[^a-z0-9]+")

    /** lowercase, no accents, no « Fribourg, » prefix, punctuation collapsed to single spaces. */
    internal fun normalise(s: String): String {
        val plain = accents.replace(Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD), "")
        return nonAlnum.replace(commune.replace(plain.trim(), ""), " ").trim()
    }

    /** Bus, tram and funicular categories of the API; an absent category is kept. Trains (S, R,
     *  IR, ...) share stops and line numbers with buses and must never match. */
    private val surface = setOf("", "B", "BN", "BUS", "NFB", "NFT", "NFO", "T", "TRAM", "FUN", "FUNI", "M", "GB", "SL", "CC")

    private fun minute(t: ZonedDateTime) = t.withZoneSameInstant(zurich).truncatedTo(ChronoUnit.MINUTES).toInstant()

    private fun delayOf(r: RtDeparture): Duration? = r.expected?.let { Duration.between(r.scheduled, it) }

    /** Delay (expected − scheduled) per local trip id. */
    fun match(local: List<Departure>, rt: List<RtDeparture>): Map<Long, Duration> {
        val rtByKey = rt.filter { it.category.uppercase() in surface }.groupBy { it.line to minute(it.scheduled) }
        val localCount = local.groupingBy { it.line.label to minute(it.scheduled) }.eachCount()
        val out = HashMap<Long, Duration>()
        for (d in local) {
            val key = d.line.label to minute(d.scheduled)
            val candidates = rtByKey[key] ?: continue
            val dest = normalise("${d.headsignCommune}, ${d.headsign}")
            val same = candidates.filter { normalise(it.to) == dest }
            val chosen = when {
                same.size == 1 -> same[0]
                same.size > 1 -> if (same.map(::delayOf).distinct().size == 1) same[0] else null
                candidates.size == 1 && localCount[key] == 1 -> candidates[0]
                else -> null
            } ?: continue
            delayOf(chosen)?.let { out[d.tripId] = it }
        }
        return out
    }
}
