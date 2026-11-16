package ch.fribusinfo.data

import java.time.LocalDate
import java.time.ZonedDateTime

data class Stop(val id: Long, val name: String, val commune: String, val lat: Double, val lon: Double)

data class Line(val id: Int, val label: String, val bg: Int, val fg: Int, val bordered: Boolean)

data class StopWithLines(val stop: Stop, val lines: List<Line>, val distanceMeters: Int?)

data class Departure(
    val tripId: Long,
    val line: Line,
    val headsign: String,
    val headsignCommune: String,
    val serviceDate: LocalDate,
    val seq: Int,
    val scheduled: ZonedDateTime,
)

data class TripHeader(val line: Line, val headsign: String, val headsignCommune: String)

data class TripStop(val stop: StopWithLines, val seq: Int, val scheduled: ZonedDateTime)
