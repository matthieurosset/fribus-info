package ch.fribusinfo.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import ch.fribusinfo.data.Departure
import ch.fribusinfo.data.Favorites
import ch.fribusinfo.data.StopWithLines
import ch.fribusinfo.data.TimetableRepository
import ch.fribusinfo.realtime.RealtimeCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val clock = DateTimeFormatter.ofPattern("HH:mm")
private val zurich = ZoneId.of("Europe/Zurich")
private val LOOK_BACK: Duration = Duration.ofMinutes(60)
private const val RAW_LIMIT = 150

/** The delay actually applied and shown: whole minutes (floored); under one minute, early or
 *  absent counts as on time, so the button always equals scheduled + the label. */
fun effectiveDelay(delay: Duration?): Duration =
    if (delay == null || delay.seconds < 60) Duration.ZERO else Duration.ofMinutes(delay.seconds / 60)

/** Real passing time of a departure. */
fun realTime(dep: Departure, delay: Duration?): ZonedDateTime = dep.scheduled.plus(effectiveDelay(delay))

/** Departures to list: real time (scheduled + delay) not before [now], ordered by real time then
 *  scheduled time, cut to [limit] after sorting. [local] may start before [now] (look-back) so
 *  that a late bus stays listed. Without delays this is exactly scheduled >= now. */
fun visibleDepartures(local: List<Departure>, delays: Map<Long, Duration>, now: ZonedDateTime, limit: Int = 60): List<Departure> =
    local.filter { !realTime(it, delays[it.tripId]).isBefore(now) }
        .sortedWith(compareBy({ realTime(it, delays[it.tripId]) }, { it.scheduled }))
        .take(limit)

/** « 7' » (floor of whole minutes, never negative) or « HH:mm » (24 h, Zurich). */
fun departureLabel(dep: Departure, now: ZonedDateTime, absolute: Boolean, delay: Duration?): String {
    val at = realTime(dep, delay)
    return if (absolute) at.withZoneSameInstant(zurich).format(clock)
    else "${Duration.between(now, at).seconds.coerceAtLeast(0) / 60}'"
}

/** « +3' » for a delay of at least one minute, otherwise null (early or on time: no label). */
fun delayBadge(delay: Duration?): String? =
    effectiveDelay(delay).let { if (it.isZero) null else "+${it.toMinutes()}'" }

/** yyyymmdd service date as used by the trip route. */
fun serviceDateInt(dep: Departure): Int =
    dep.serviceDate.year * 10000 + dep.serviceDate.monthValue * 100 + dep.serviceDate.dayOfMonth

@Composable
fun DepartureRow(dep: Departure, now: ZonedDateTime, absolute: Boolean, delay: Duration?, onClick: () -> Unit) {
    Column(
        Modifier.testTag("departure-row").clickable(onClick = onClick).semantics(mergeDescendants = true) {},
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 57.dp).padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LargeBadge(dep)
            Column(Modifier.weight(1f).padding(start = 8.dp, end = 8.dp)) {
                Text(
                    dep.headsign, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = Color.Black, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(dep.headsignCommune, fontSize = 16.sp, color = Colors.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            delayBadge(delay)?.let { label ->
                Box(
                    Modifier.padding(end = 6.dp).height(20.dp).clip(RoundedCornerShape(3.dp))
                        .background(Colors.DelayBadge).padding(horizontal = 5.dp).testTag("delay-badge"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
            Box(
                Modifier.width(67.dp).height(27.dp).clip(RoundedCornerShape(3.dp)).background(Colors.MinutesButton),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    departureLabel(dep, now, absolute, delay),
                    color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                )
            }
        }
        HorizontalDivider(color = Colors.Divider, thickness = 1.dp)
    }
}

/** Large square badge (about 35 dp) of the departures list. */
@Composable
private fun LargeBadge(dep: Departure) {
    val line = dep.line
    Box(
        Modifier
            .defaultMinSize(minWidth = 35.dp, minHeight = 35.dp)
            .background(Color(0xFF000000.toInt() or line.bg))
            .let { if (line.bordered) it.border(1.dp, Color.Black) else it }
            .padding(horizontal = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            line.label,
            color = Color(0xFF000000.toInt() or line.fg),
            fontSize = 22.sp,
            fontWeight = if (line.bordered) FontWeight.Normal else FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/** Departures of one stop. [repository] is null while the database opens. [now] is injectable
 *  for tests; the list is recomputed every [tickMillis] (departed rows disappear). */
@Composable
fun DeparturesScreen(
    navController: NavController,
    repository: TimetableRepository?,
    favorites: Favorites,
    stopId: Long,
    now: () -> ZonedDateTime = { ZonedDateTime.now(zurich) },
    tickMillis: Long = 30_000,
    realtime: RealtimeCache? = null,
) {
    val absolute by favorites.absoluteTime.collectAsState()
    val favIds by favorites.ids.collectAsState()
    var stop by remember { mutableStateOf<StopWithLines?>(null) }
    var current by remember { mutableStateOf(now()) }
    // Raw list starts LOOK_BACK before now so a late bus (scheduled in the past) is still known.
    var departures by remember { mutableStateOf<List<Departure>?>(null) }
    var delays by remember { mutableStateOf<Map<Long, Duration>>(emptyMap()) }

    LaunchedEffect(repository, stopId) {
        stop = if (repository == null) null else withContext(Dispatchers.IO) { repository.stop(stopId) }
    }
    // Refreshes immediately on start and every tick while STARTED; stops in the background.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(repository, stopId, lifecycle) {
        if (repository == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val t = now()
                try {
                    departures = withContext(Dispatchers.IO) { repository.departures(stopId, t.minus(LOOK_BACK), RAW_LIMIT, Duration.ofHours(6).plus(LOOK_BACK)) }
                    current = t
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("DeparturesScreen", "departures refresh failed", e)
                }
                delay(tickMillis)
            }
        }
    }

    // Realtime only for this stop and only while the screen is visible; any failure = no delays.
    LaunchedEffect(realtime, stopId, lifecycle) {
        if (realtime == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            snapshotFlow { departures }.collectLatest { list ->
                if (list.isNullOrEmpty()) {
                    delays = emptyMap()
                    return@collectLatest
                }
                try {
                    realtime.delays(stopId, list).collect { delays = it }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("DeparturesScreen", "realtime failed", e)
                    delays = emptyMap()
                }
            }
        }
    }

    var menuOpen by remember { mutableStateOf(false) }
    val isFav = stopId in favIds
    AppScaffold(
        title = stop?.stop?.name ?: "",
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
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Plus")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = Color.White) {
                    DropdownMenuItem(
                        text = { Text(if (isFav) "Retirer des favoris" else "Ajouter aux favoris", fontSize = 17.sp) },
                        leadingIcon = { Icon(Icons.Outlined.StarBorder, null, tint = Colors.Secondary) },
                        onClick = { favorites.toggle(stopId); menuOpen = false },
                    )
                    DropdownMenuItem(
                        text = { Text(if (absolute) "Afficher les minutes" else "Afficher l'heure", fontSize = 17.sp) },
                        leadingIcon = { Icon(Icons.Outlined.Schedule, null, tint = Colors.Secondary) },
                        onClick = { favorites.setAbsoluteTime(!absolute); menuOpen = false },
                    )
                }
            }
        },
    ) {
        val list = remember(departures, delays, current) {
            departures?.let { visibleDepartures(it, delays, current) }
        }
        when {
            list == null -> Unit
            list.isEmpty() -> Box(Modifier.fillMaxSize().testTag("empty"), contentAlignment = Alignment.Center) {
                Text(
                    "Aucun départ dans les prochaines heures",
                    color = Colors.Secondary, fontSize = 18.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp),
                )
            }
            else -> LazyColumn {
                items(list, key = { "${it.tripId}/${serviceDateInt(it)}/${it.seq}" }) { d ->
                    val delay = delays[d.tripId]
                    DepartureRow(d, current, absolute, delay) {
                        val secs = effectiveDelay(delay).seconds.toInt()
                        navController.navigate(Routes.trip(d.tripId, serviceDateInt(d), d.seq, secs))
                    }
                }
            }
        }
    }
}
