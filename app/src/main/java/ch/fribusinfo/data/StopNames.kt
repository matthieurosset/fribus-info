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
