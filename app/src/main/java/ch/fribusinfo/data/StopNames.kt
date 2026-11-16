package ch.fribusinfo.data

import java.text.Normalizer

/** "Fribourg, Tilleul/Cathédrale" -> (name, commune). Same rule as the pipeline:
 *  first comma, both sides trimmed; no comma -> the full string twice. */
fun splitStopName(raw: String): Pair<String, String> {
    val i = raw.indexOf(',')
    if (i < 0) return raw to raw
    return raw.substring(i + 1).trim() to raw.substring(0, i).trim()
}

private val marks = Regex("\\p{M}+")

/** Lower-case, accent-free form used to compare search text with stop names. */
fun foldForSearch(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(marks, "").lowercase()

/** Old-app search: the query is split into words (commas count as spaces); every word must
 *  appear, ignoring case and accents, in « commune name ». Result sorted by name then
 *  commune, no limit. A blank query matches nothing. */
fun searchStops(stops: List<StopWithLines>, query: String): List<StopWithLines> {
    val words = foldForSearch(query).split(' ', ',', '\t').filter { it.isNotEmpty() }
    if (words.isEmpty()) return emptyList()
    return stops
        .map { Triple(it, foldForSearch(it.stop.name), foldForSearch(it.stop.commune)) }
        .filter { (_, name, commune) -> val hay = "$commune $name"; words.all { it in hay } }
        .sortedWith(compareBy({ it.second }, { it.third }))
        .map { it.first }
}
