package ch.fribusinfo.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.derivedStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import ch.fribusinfo.data.TimetableRepository
import ch.fribusinfo.data.TripHeader
import ch.fribusinfo.data.TripStop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

/** Horizontal centre of the pearl chain, shared with the pixel tests. */
internal val PEARL_CX = 60.dp

private val LineBefore = Color(0xFFDDDDDD)
private val RingBefore = Color(0xFFAAAAAA)
private val LineAfter = Color(0xFF999696)
private val RingAfter = Color(0xFF666666)
private val RingChosen = Color(0xFF891313)
private val RingEnd = Color(0xFF423E3E)

/** Real time of every stop: before the chosen one the scheduled time, from it on scheduled +
 *  the departure's signed delay (seconds). */
fun tripRealTimes(stops: List<TripStop>, fromSeq: Int, delay: Duration?): List<ZonedDateTime> {
    val d = delaySeconds(delay)
    return stops.map { if (it.seq >= fromSeq) it.scheduled.plusSeconds(d) else it.scheduled }
}

/** Minutes from [now] to the real time of every stop (clock-minute difference, negative once
 *  passed); shown through [formatMinutes]. */
fun tripMinutes(stops: List<TripStop>, fromSeq: Int, delay: Duration?, now: ZonedDateTime): List<Long> =
    tripRealTimes(stops, fromSeq, delay).map { minutesFromNow(now, it) }

/** True when the trip ids of a route were issued by another database than [repoVersion]: trip ids
 *  are only meaningful inside one database, so the screen must not reload them. */
fun tripRouteIsStale(routeVersion: String, repoVersion: String): Boolean = routeVersion != repoVersion

private class TripData(val header: TripHeader?, val stops: List<TripStop>)

/** All stops of one trip. [delay] (realtime offset of the chosen departure) shifts the stops
 *  from the chosen one on, so minutes counted from the chosen stop do not change. */
@Composable
fun TripScreen(
    tripId: Long,
    serviceDate: LocalDate,
    fromSeq: Int,
    delay: Duration?,
    navController: NavController,
    repository: TimetableRepository?,
    version: String,
    now: () -> ZonedDateTime = { ZonedDateTime.now(java.time.ZoneId.of("Europe/Zurich")) },
) {
    var data by remember { mutableStateOf<TripData?>(null) }
    LaunchedEffect(repository, tripId, serviceDate) {
        if (repository == null) return@LaunchedEffect
        try {
            val loaded = withContext(Dispatchers.IO) {
                if (tripRouteIsStale(version, repository.version)) null
                else TripData(repository.tripHeader(tripId), repository.trip(tripId, serviceDate))
            }
            if (loaded == null) {
                // The timetable was swapped under this screen: back to the departures.
                navController.popBackStack()
                return@LaunchedEffect
            }
            data = loaded
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("TripScreen", "trip load failed", e)
        }
    }

    // Minutes are counted from now: refresh on every exact minute while the screen is STARTED.
    var current by remember { mutableStateOf(now()) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                current = now()
                delay(millisUntilNextMinute(now()))
            }
        }
    }

    AppScaffold(
        title = data?.header?.headsign ?: "",
        titleBadge = data?.header?.line,
        navController = navController,
        onBack = { navController.popBackStack() },
        actions = {
            IconButton(onClick = { navController.navigate(Routes.SEARCH) { launchSingleTop = true } }) {
                Icon(Icons.Filled.Search, contentDescription = "Recherche")
            }
            IconButton(onClick = {
                if (!navController.popBackStack(Routes.NEARBY, false)) navController.navigate(Routes.NEARBY)
            }) {
                Icon(Icons.Filled.MyLocation, contentDescription = "Ma position")
            }
        },
    ) {
        val stops = data?.stops ?: return@AppScaffold
        val minutes = remember(stops, fromSeq, delay, current) { tripMinutes(stops, fromSeq, delay, current) }
        val chosen = stops.indexOfFirst { it.seq == fromSeq }
        val state = rememberLazyListState()
        val peek = with(LocalDensity.current) { 55.dp.roundToPx() }
        // Chosen stop near the top, the previous one peeking above (as in the reference).
        LaunchedEffect(stops, fromSeq) {
            if (chosen > 0) state.scrollToItem(chosen - 1, peek)
        }
        // The terminus row is pinned at the bottom until the real one is scrolled into view.
        val terminusVisible by remember(stops) {
            derivedStateOf {
                val info = state.layoutInfo
                info.visibleItemsInfo.any { it.index == stops.lastIndex && it.offset + it.size <= info.viewportEndOffset }
            }
        }
        Column(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("trip-list"), state = state) {
                itemsIndexed(stops, key = { _, s -> s.seq }) { i, s ->
                    TripRow(
                        s, formatMinutes(minutes[i]), isChosen = i == chosen, isFirst = i == 0, isTerminus = i == stops.lastIndex,
                        beforeChosen = chosen >= 0 && i < chosen, passed = minutes[i] < 0,
                        onClick = { navController.navigate(Routes.stop(s.stop.stop.id)) },
                    )
                }
            }
            if (!terminusVisible) {
                val last = stops.last()
                PinnedTerminus(last, onClick = { navController.navigate(Routes.stop(last.stop.stop.id)) })
            }
        }
    }
}

@Composable
private fun TripRow(
    s: TripStop, minutes: String, isChosen: Boolean, isFirst: Boolean, isTerminus: Boolean,
    beforeChosen: Boolean, passed: Boolean, onClick: () -> Unit,
) {
    val bg = when {
        isChosen -> Colors.PearlCurrent
        isTerminus -> Colors.PearlEnd
        else -> Color.White
    }
    val grey = !isChosen && (beforeChosen || passed)
    val fg = when { isChosen -> Color.White; grey -> Colors.Disabled; else -> Color.Black }
    val sub = when { isChosen -> Color.White; grey -> Colors.Disabled; else -> Colors.Secondary }
    Row(
        Modifier
            .testTag("trip-row-${s.seq}")
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .background(bg)
            .drawBehind { drawPearl(isChosen, isFirst, isTerminus, beforeChosen) }
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
            Text(minutes, color = fg, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Box(Modifier.width(36.dp))
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                s.stop.stop.name, modifier = Modifier.offset(y = (-2.5).dp), fontSize = 17.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold,
                color = fg, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text(s.stop.stop.commune, fontSize = 13.5.sp, lineHeight = 15.5.sp, color = sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Badges(s.stop.lines)
    }
}

/** Copy of the terminus row stuck to the bottom while the real one is out of view (grey text, an
 *  arrow in the ring pointing down to it). */
@Composable
private fun PinnedTerminus(s: TripStop, onClick: () -> Unit) {
    Row(
        Modifier
            .testTag("trip-pinned-terminus")
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .background(Colors.PearlEnd)
            .drawBehind { drawPearl(isChosen = false, isFirst = false, isTerminus = true, beforeChosen = false, arrow = true) }
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(76.dp))
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                s.stop.stop.name, modifier = Modifier.offset(y = (-2.5).dp), fontSize = 17.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold,
                color = Colors.Gray, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text(s.stop.stop.commune, fontSize = 13.5.sp, lineHeight = 15.5.sp, color = Colors.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Pearl chain: the vertical line, cut above the first stop and below the terminus, and the ring
 *  centred at x = 60 dp. Light grey before the chosen stop, darker after it; the chosen ring is red,
 *  the terminus ring dark with a red dot. */
private fun DrawScope.drawPearl(isChosen: Boolean, isFirst: Boolean, isTerminus: Boolean, beforeChosen: Boolean, arrow: Boolean = false) {
    val cx = PEARL_CX.toPx()
    val cy = size.height / 2
    val lw = 10.7.dp.toPx()
    val above = if (beforeChosen || isChosen) LineBefore else LineAfter
    val below = if (beforeChosen) LineBefore else LineAfter
    if (!isFirst) drawRect(above, Offset(cx - lw / 2, 0f), Size(lw, cy))
    if (!isTerminus) drawRect(below, Offset(cx - lw / 2, cy), Size(lw, size.height - cy))
    val r = 10.65.dp.toPx()
    val sw = 2.7.dp.toPx()
    val ring = when { isTerminus -> RingEnd; isChosen -> RingChosen; beforeChosen -> RingBefore; else -> RingAfter }
    drawCircle(Color.White, r, Offset(cx, cy))
    drawCircle(ring, r - sw / 2, Offset(cx, cy), style = Stroke(sw))
    if (isTerminus) {
        if (arrow) {
            val a = 5.dp.toPx()
            val p = androidx.compose.ui.graphics.Path().apply {
                moveTo(cx - a, cy - a / 2); lineTo(cx, cy + a / 2); lineTo(cx + a, cy - a / 2)
            }
            drawPath(p, RingEnd, style = Stroke(2.dp.toPx()))
        } else drawCircle(RingChosen, 7.dp.toPx(), Offset(cx, cy))
    }
}
