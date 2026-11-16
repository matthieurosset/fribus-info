package ch.fribusinfo.ui

import android.Manifest
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import android.content.pm.PackageManager
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import ch.fribusinfo.data.StopWithLines
import ch.fribusinfo.data.TimetableRepository
import ch.fribusinfo.location.LocationSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Process-level: the permission dialog is shown on first open at most once per app launch. */
private var permissionAskedThisLaunch = false

/** Ask on open only when enabled, not yet granted, and not already asked this launch. */
fun shouldAskPermissionOnOpen(enabled: Boolean, granted: Boolean, alreadyAsked: Boolean) =
    enabled && !granted && !alreadyAsked

/** « Position indisponible » only once the permission outcome is known and no location came. */
fun isPositionUnavailable(permissionResolved: Boolean, emitted: Boolean, hasLocation: Boolean) =
    permissionResolved && emitted && !hasLocation

/** The list jumps back to the top when a new location yields a different list of stops (not on a
 *  refresh that returns the same stops in the same order). */
fun shouldScrollToTop(previousIds: List<Long>?, newIds: List<Long>) = previousIds != newIds

/** Home screen. [repository] is null while the database opens (blank, no spinner). */
@Composable
fun NearbyScreen(
    navController: NavController,
    repository: TimetableRepository?,
    locations: LocationSource,
    requestPermissionOnOpen: Boolean = true,
) {
    val context = LocalContext.current
    fun granted() = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    // Bumped to (re)start location collection: permission result, target icon.
    var attempt by remember { mutableIntStateOf(0) }
    var location by remember { mutableStateOf<Location?>(null) }
    var located by remember { mutableStateOf(false) }   // an emission (possibly null) arrived
    // The permission outcome is known (granted, answered, or already asked this launch).
    var resolved by rememberSaveable {
        mutableStateOf(!requestPermissionOnOpen || granted() || permissionAskedThisLaunch)
    }

    val perms = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        resolved = true
        attempt++
    }
    LaunchedEffect(Unit) {
        if (shouldAskPermissionOnOpen(requestPermissionOnOpen, granted(), permissionAskedThisLaunch)) {
            permissionAskedThisLaunch = true
            launcher.launch(perms)
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(attempt, resolved, locations) {
        location = null
        located = false
        if (!resolved) return@LaunchedEffect
        // Collect only while the screen is visible (no background GPS).
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            locations.updates().collect { location = it; located = true }
        }
    }

    // Only the coordinates matter: no re-query for a repeated identical fix.
    val lat = location?.latitude
    val lon = location?.longitude
    var stops by remember { mutableStateOf<List<StopWithLines>?>(null) }
    LaunchedEffect(repository, lat, lon) {
        stops = if (repository == null || lat == null || lon == null) null
        else withContext(Dispatchers.IO) { repository.nearby(lat, lon) }
    }

    val listState = rememberLazyListState()
    var shownIds by remember { mutableStateOf<List<Long>?>(null) }
    LaunchedEffect(stops) {
        val ids = stops?.map { it.stop.id } ?: return@LaunchedEffect
        if (shouldScrollToTop(shownIds, ids)) listState.scrollToItem(0)
        shownIds = ids
    }

    AppScaffold(
        title = "À proximité",
        navController = navController,
        actions = {
            IconButton(onClick = { navController.navigate(Routes.SEARCH) }) {
                Icon(Icons.Filled.Search, contentDescription = "Recherche", modifier = Modifier.size(28.dp))
            }
            IconButton(onClick = {
                // Explicit request: ask again if the permission is missing, else just re-collect.
                if (requestPermissionOnOpen && !granted()) launcher.launch(perms) else attempt++
            }) {
                Icon(Icons.Filled.MyLocation, contentDescription = "Ma position", modifier = Modifier.size(25.dp))
            }
        },
    ) {
        val list = stops
        when {
            isPositionUnavailable(resolved, located, location != null) -> Message("Position indisponible", "unavailable")
            list == null -> Unit
            list.isEmpty() -> Message("Aucun arrêt à proximité", "empty")
            else -> LazyColumn(state = listState) {
                items(list, key = { it.stop.id }) { s ->
                    StopRow(s) { navController.navigate(Routes.stop(s.stop.id)) }
                }
            }
        }
    }
}

@Composable
private fun Message(text: String, tag: String) {
    Box(Modifier.fillMaxSize().testTag(tag), contentAlignment = Alignment.Center) {
        Text(text, color = Colors.Secondary, fontSize = 18.sp, textAlign = TextAlign.Center)
    }
}
