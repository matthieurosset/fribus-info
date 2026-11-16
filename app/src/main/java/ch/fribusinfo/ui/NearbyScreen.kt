package ch.fribusinfo.ui

import android.Manifest
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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

/** Home screen. [repository] is null while the database opens (blank, no spinner). */
@Composable
fun NearbyScreen(
    navController: NavController,
    repository: TimetableRepository?,
    locations: LocationSource,
    requestPermissionOnOpen: Boolean = true,
) {
    // Bumped to (re)start location collection: permission result, target icon.
    var attempt by remember { mutableIntStateOf(0) }
    var location by remember { mutableStateOf<Location?>(null) }
    var located by remember { mutableStateOf(false) }   // an emission (possibly null) arrived

    val perms = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        attempt++
    }
    LaunchedEffect(Unit) { if (requestPermissionOnOpen) launcher.launch(perms) }

    LaunchedEffect(attempt, locations) {
        locations.updates().collect { location = it; located = true }
    }

    // Only the coordinates matter: no re-query for a repeated identical fix.
    val lat = location?.latitude
    val lon = location?.longitude
    var stops by remember { mutableStateOf<List<StopWithLines>?>(null) }
    LaunchedEffect(repository, lat, lon) {
        stops = if (repository == null || lat == null || lon == null) null
        else withContext(Dispatchers.IO) { repository.nearby(lat, lon) }
    }

    AppScaffold(
        title = "À proximité",
        navController = navController,
        actions = {
            IconButton(onClick = { navController.navigate(Routes.SEARCH) }) {
                Icon(Icons.Filled.Search, contentDescription = "Recherche")
            }
            IconButton(onClick = {
                // Re-ask the permission if still missing (no-op dialog if granted), then re-collect.
                if (requestPermissionOnOpen) launcher.launch(perms) else attempt++
            }) {
                Icon(Icons.Filled.MyLocation, contentDescription = "Ma position")
            }
        },
    ) {
        val list = stops
        when {
            located && location == null -> Message("Position indisponible", "unavailable")
            list == null -> Unit
            list.isEmpty() -> Message("Aucun arrêt à proximité", "empty")
            else -> LazyColumn {
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
