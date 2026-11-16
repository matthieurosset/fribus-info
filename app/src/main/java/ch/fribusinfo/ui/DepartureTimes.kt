package ch.fribusinfo.ui

import ch.fribusinfo.data.Departure
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val clock = DateTimeFormatter.ofPattern("HH:mm")
private val zurich = ZoneId.of("Europe/Zurich")

/** Signed real-time offset in seconds (0 without real-time data). */
fun delaySeconds(delay: Duration?): Long = delay?.seconds ?: 0L

/** Delay in whole minutes, truncated toward zero like the reference app (-59 s -> 0, -61 s -> -1). */
fun delayMinutes(delay: Duration?): Long = delaySeconds(delay) / 60

/** Real passing time: scheduled + signed delay (seconds precision, early buses count). */
fun realTime(dep: Departure, delay: Duration?): ZonedDateTime = dep.scheduled.plusSeconds(delaySeconds(delay))

/** Clock-minute difference between [now] and [real] (both cut to the minute): 10:04:30 -> 10:05:00 is 1. */
fun minutesFromNow(now: ZonedDateTime, real: ZonedDateTime): Long =
    Duration.between(now.truncatedTo(ChronoUnit.MINUTES), real.truncatedTo(ChronoUnit.MINUTES)).toMinutes()

/** « 7' », « 1h05' » from 60 minutes on, « -1' » below zero. */
fun formatMinutes(m: Long): String =
    if (m >= 60) "${m / 60}h${"%02d".format(m % 60)}'" else "$m'"

/** Text of the time button: scheduled « HH:mm » in absolute mode, minutes from now otherwise. */
fun departureLabel(dep: Departure, now: ZonedDateTime, absolute: Boolean, delay: Duration?): String =
    if (absolute) dep.scheduled.withZoneSameInstant(zurich).format(clock)
    else formatMinutes(minutesFromNow(now, realTime(dep, delay)))

/** Pill of the absolute mode: « +3' », « +0' », « -2' »; null without real-time data. */
fun delayPill(delay: Duration?): String? {
    if (delay == null) return null
    val m = delayMinutes(delay)
    return if (m < 0) "-${-m}'" else "+$m'"
}

/** Yellow text (button, pill) when the delay is at least one minute either way. */
fun isDelayed(delay: Duration?): Boolean = delayMinutes(delay) != 0L

enum class DepartureState { UPCOMING, NOW, PASSED }

fun departureState(dep: Departure, now: ZonedDateTime, delay: Duration?): DepartureState {
    val m = minutesFromNow(now, realTime(dep, delay))
    return when {
        m < 0 -> DepartureState.PASSED
        m == 0L -> DepartureState.NOW
        else -> DepartureState.UPCOMING
    }
}

/** Departures to list. With real-time data a row stays while its real minute is not older than
 *  the previous one (just-left rows, greyed); without, only from the current minute. Ordered by real
 *  time (seconds) then headsign, cut to [limit] after sorting. */
fun visibleDepartures(local: List<Departure>, delays: Map<Long, Duration>, now: ZonedDateTime, limit: Int = 30): List<Departure> =
    local.filter {
        val d = delays[it.tripId]
        minutesFromNow(now, realTime(it, d)) >= (if (d != null) -1 else 0)
    }
        .sortedWith(compareBy<Departure>({ realTime(it, delays[it.tripId]) }, { it.headsign }))
        .take(limit)

/** Index of the first non-passed row (the list is scrolled so that it sits at the top). */
fun firstUpcomingIndex(list: List<Departure>, delays: Map<Long, Duration>, now: ZonedDateTime): Int =
    list.indexOfFirst { departureState(it, now, delays[it.tripId]) != DepartureState.PASSED }.coerceAtLeast(0)

/** Milliseconds until the next exact minute (60 s on the minute), plus a small margin. */
fun millisUntilNextMinute(now: ZonedDateTime): Long =
    60_000L - (now.second * 1000L + now.nano / 1_000_000) + 20

/** yyyymmdd service date as used by the trip route. */
fun serviceDateInt(dep: Departure): Int =
    dep.serviceDate.year * 10000 + dep.serviceDate.monthValue * 100 + dep.serviceDate.dayOfMonth
