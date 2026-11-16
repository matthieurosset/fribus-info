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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.StarBorder
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

private val MenuItemPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 16.dp)

/** Overflow menu anchored at the right edge of the window, level with its anchor's top. */
@Composable
private fun OverflowMenu(expanded: Boolean, onDismiss: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    if (!expanded) return
    val position = remember {
        object : androidx.compose.ui.window.PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: androidx.compose.ui.unit.IntRect,
                windowSize: androidx.compose.ui.unit.IntSize,
                layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                popupContentSize: androidx.compose.ui.unit.IntSize,
            ) = androidx.compose.ui.unit.IntOffset(windowSize.width - popupContentSize.width, anchorBounds.top)
        }
    }
    androidx.compose.ui.window.Popup(
        popupPositionProvider = position,
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.PopupProperties(focusable = true),
    ) {
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(3.dp), color = Color.White, shadowElevation = 6.dp,
            modifier = Modifier.width(247.dp),
        ) { Column(content = content) }
    }
}

private val zurich = ZoneId.of("Europe/Zurich")
private val LOOK_BACK: Duration = Duration.ofMinutes(60)
private const val RAW_LIMIT = 150

/** Tests switch the blinking of the « now » button off (an infinite animation never lets Compose idle). */
val LocalBlinkEnabled = androidx.compose.runtime.staticCompositionLocalOf { true }

private val Yellow = Color(0xFFFFFF00)
private val BlueGradient = Brush.verticalGradient(listOf(Colors.MinutesTop, Colors.MinutesBottom))
private val GreyGradient = Brush.verticalGradient(listOf(Colors.PassedTop, Colors.PassedBottom))

@Composable
fun DepartureRow(dep: Departure, now: ZonedDateTime, absolute: Boolean, delay: Duration?, onClick: () -> Unit) {
    val state = departureState(dep, now, delay)
    val passed = state == DepartureState.PASSED
    val textColor = if (passed || !isDelayed(delay)) Color.White else Yellow
    Column(
        Modifier.testTag("departure-row").clickable(onClick = onClick).semantics(mergeDescendants = true) {},
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 57.dp).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LargeBadge(dep)
            Column(Modifier.weight(1f).padding(start = 8.dp, end = 8.dp)) {
                Text(
                    dep.headsign, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    color = if (passed) Colors.Disabled else Color.Black, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(dep.headsignCommune, fontSize = 13.5.sp, color = Colors.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val pill = if (absolute) delayPill(delay) else null
            if (pill != null) {
                Box(
                    Modifier.padding(end = 4.dp).defaultMinSize(minWidth = 26.dp).height(27.dp)
                        .clip(RoundedCornerShape(4.dp)).background(if (passed) GreyGradient else BlueGradient)
                        .padding(horizontal = 3.dp).testTag("delay-badge"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        pill, color = textColor,
                        fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    )
                }
            }
            val alpha = if (state == DepartureState.NOW && LocalBlinkEnabled.current) {
                rememberInfiniteTransition(label = "blink").animateFloat(
                    1f, 0.5f,
                    infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Reverse),
                    label = "alpha",
                ).value
            } else 1f
            Box(Modifier.width(50.dp).height(27.dp).clip(RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                Box(Modifier.matchParentSize().alpha(alpha).background(if (passed) GreyGradient else BlueGradient).testTag("minutes-bg"))
                Text(
                    departureLabel(dep, now, absolute, delay),
                    color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    modifier = Modifier.defaultMinSize(minWidth = 30.dp).padding(horizontal = 1.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
        HorizontalDivider(color = Colors.Divider, thickness = 1.dp)
    }
}

/** Square badge of the departures list: 35 x 35 dp; labels of 3+ characters are set at 16 sp and
 *  squeezed to 70 % of their width, like the reference app. */
@Composable
private fun LargeBadge(dep: Departure) {
    val line = dep.line
    val long = line.label.length > 2
    Box(
        Modifier
            .size(35.dp)
            .background(Color(0xFF000000.toInt() or line.bg))
            .let { if (line.bordered) it.border(1.dp, Color.Black) else it },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            line.label,
            color = Color(0xFF000000.toInt() or line.fg),
            fontSize = if (long) 16.sp else 22.sp,
            fontWeight = if (line.bordered) FontWeight.Normal else FontWeight.Bold,
            maxLines = 1, softWrap = false,
            modifier = if (long) Modifier.graphicsLayer(scaleX = 0.7f) else Modifier,
        )
    }
}

/** Departures of one stop. [repository] is null while the database opens. [now] is injectable
 *  for tests. The list is recomputed on every exact minute ([tickMillis] overrides this in tests). */
@Composable
fun DeparturesScreen(
    navController: NavController,
    repository: TimetableRepository?,
    favorites: Favorites,
    stopId: Long,
    now: () -> ZonedDateTime = { ZonedDateTime.now(zurich) },
    tickMillis: Long? = null,
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
                    departures = withContext(Dispatchers.IO) { repository.departures(stopId, t.minus(LOOK_BACK), RAW_LIMIT, Duration.ofMinutes(120).plus(LOOK_BACK)) }
                    current = t
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("DeparturesScreen", "departures refresh failed", e)
                }
                delay(tickMillis ?: millisUntilNextMinute(now()))
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
                Icon(Icons.Filled.Search, contentDescription = "Recherche", modifier = Modifier.size(28.dp))
            }
            IconButton(onClick = {
                if (!navController.popBackStack(Routes.NEARBY, false)) navController.navigate(Routes.NEARBY)
            }) {
                Icon(Icons.Filled.MyLocation, contentDescription = "Ma position", modifier = Modifier.size(25.dp))
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Plus")
                }
                // Like the reference: 247 dp wide, right edge flush with the screen, top level with the
                // anchor button (4 dp below the bar's top, so the menu overlaps the bar), no vertical padding.
                OverflowMenu(expanded = menuOpen, onDismiss = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(if (isFav) "Supprimer des favoris" else "Ajouter aux favoris", fontSize = 17.sp) },
                        contentPadding = MenuItemPadding,
                        leadingIcon = { Box(Modifier.width(32.dp)) { Icon(Icons.Outlined.StarBorder, null, tint = Colors.Secondary, modifier = Modifier.size(26.dp)) } },
                        onClick = { favorites.toggle(stopId); menuOpen = false },
                    )
                    DropdownMenuItem(
                        text = { Text(if (absolute) "Heure de départ en minutes" else "Heure de départ absolue", fontSize = 17.sp) },
                        contentPadding = MenuItemPadding,
                        leadingIcon = { Box(Modifier.width(32.dp)) { Icon(Icons.Outlined.Schedule, null, tint = Colors.Secondary, modifier = Modifier.size(26.dp)) } },
                        onClick = { favorites.setAbsoluteTime(!absolute); menuOpen = false },
                    )
                }
            }
        },
    ) {
        val list = remember(departures, delays, current) {
            departures?.let { visibleDepartures(it, delays, current) }
        }
        val listState = rememberLazyListState()
        var userScrolled by remember { mutableStateOf(false) }
        val scrollWatcher = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (source == NestedScrollSource.UserInput) userScrolled = true
                    return Offset.Zero
                }
            }
        }
        // After each refresh the first non-passed row goes to the top, unless the user has scrolled.
        LaunchedEffect(list) {
            if (list != null && list.isNotEmpty() && !userScrolled) {
                listState.scrollToItem(firstUpcomingIndex(list, delays, current))
            }
        }
        when {
            list == null -> Unit
            list.isEmpty() -> Box(Modifier.fillMaxSize().testTag("empty"), contentAlignment = Alignment.Center) {
                Text(
                    "Pas de départ ces 2 prochaines heures",
                    color = Colors.Disabled, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp),
                )
            }
            else -> LazyColumn(Modifier.nestedScroll(scrollWatcher), state = listState) {
                items(list, key = { "${it.tripId}/${serviceDateInt(it)}/${it.seq}" }) { d ->
                    val delay = delays[d.tripId]
                    DepartureRow(d, current, absolute, delay, onClick = {
                        navController.navigate(Routes.trip(d.tripId, serviceDateInt(d), d.seq, delaySeconds(delay).toInt(), repository?.version.orEmpty()))
                    })
                }
            }
        }
    }
}
