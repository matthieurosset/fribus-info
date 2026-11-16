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

private val RingRed = Color(0xFFA80231)
private val DotRed = Color(0xFF8B1010)
private val RingGrey = Color(0xFF858585)

/** One label per stop: null before the chosen stop (no minutes), « 0' » at it, then whole
 *  minutes (floored) since its scheduled time. All null if [fromSeq] is not in the trip. */
fun tripMinutes(stops: List<TripStop>, fromSeq: Int): List<String?> {
    val from = stops.firstOrNull { it.seq == fromSeq } ?: return stops.map { null }
    return stops.map {
        if (it.seq < fromSeq) null
        else "${Duration.between(from.scheduled, it.scheduled).seconds.coerceAtLeast(0) / 60}'"
    }
}

private class TripData(val header: TripHeader?, val stops: List<TripStop>)

/** All stops of one trip. [delay] (realtime offset of the chosen departure, wired in a later
 *  task) shifts every stop equally, so minutes counted from the chosen stop do not change. */
@Composable
fun TripScreen(
    tripId: Long,
    serviceDate: LocalDate,
    fromSeq: Int,
    delay: Duration?,
    navController: NavController,
    repository: TimetableRepository?,
) {
    var data by remember { mutableStateOf<TripData?>(null) }
    LaunchedEffect(repository, tripId, serviceDate) {
        if (repository == null) return@LaunchedEffect
        try {
            data = withContext(Dispatchers.IO) { TripData(repository.tripHeader(tripId), repository.trip(tripId, serviceDate)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("TripScreen", "trip load failed", e)
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
        val labels = remember(stops, fromSeq) { tripMinutes(stops, fromSeq) }
        val chosen = stops.indexOfFirst { it.seq == fromSeq }
        val state = rememberLazyListState()
        val peek = with(LocalDensity.current) { 55.dp.roundToPx() }
        // Chosen stop near the top, the previous one peeking above (as in the reference).
        LaunchedEffect(stops, fromSeq) {
            if (chosen > 0) state.scrollToItem(chosen - 1, peek)
        }
        LazyColumn(Modifier.fillMaxSize().testTag("trip-list"), state = state) {
            itemsIndexed(stops, key = { _, s -> s.seq }) { i, s ->
                TripRow(
                    s, labels[i], isChosen = i == chosen, isTerminus = i == stops.lastIndex,
                    onClick = { navController.navigate(Routes.stop(s.stop.stop.id)) },
                )
            }
        }
    }
}

@Composable
private fun TripRow(s: TripStop, minutes: String?, isChosen: Boolean, isTerminus: Boolean, onClick: () -> Unit) {
    val bg = when {
        isChosen -> Colors.PearlCurrent
        isTerminus -> Colors.PearlEnd
        else -> Color.White
    }
    val fg = if (isChosen) Color.White else Color.Black
    val sub = if (isChosen) Color.White else Colors.Secondary
    val tag = when {
        isChosen -> "trip-row-chosen"
        isTerminus -> "trip-row-terminus"
        else -> "trip-row"
    }
    Row(
        Modifier
            .testTag(tag)
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .background(bg)
            .drawBehind { drawPearl(isChosen, isTerminus) }
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
            if (minutes != null) Text(minutes, color = fg, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Box(Modifier.width(36.dp))
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                s.stop.stop.name, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = fg, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text(s.stop.stop.commune, fontSize = 16.sp, color = sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Badges(s.stop.lines)
    }
}

/** Pearl chain: the vertical line (full row height, so it also runs above the first and below
 *  the last stop) and the circle centred at x = 60 dp. */
private fun DrawScope.drawPearl(isChosen: Boolean, isTerminus: Boolean) {
    val cx = 60.dp.toPx()
    val cy = size.height / 2
    val lw = 11.dp.toPx()
    drawRect(Colors.PearlLine, Offset(cx - lw / 2, 0f), Size(lw, size.height))
    val r = 10.5.dp.toPx()
    val sw = (if (isChosen) 3.dp else 2.dp).toPx()
    drawCircle(Color.White, r, Offset(cx, cy))
    drawCircle(if (isChosen) RingRed else RingGrey, r - sw / 2, Offset(cx, cy), style = Stroke(sw))
    if (isTerminus) drawCircle(DotRed, 7.dp.toPx(), Offset(cx, cy))
}
