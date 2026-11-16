package ch.fribusinfo.ui

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import ch.fribusinfo.data.Favorites
import ch.fribusinfo.data.StopWithLines
import ch.fribusinfo.data.TimetableRepository
import ch.fribusinfo.data.foldForSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Favourite stops sorted by name; follows [Favorites.ids] live. */
@Composable
fun FavoritesScreen(navController: NavController, repository: TimetableRepository?, favorites: Favorites) {
    val ids by favorites.ids.collectAsState()
    var stops by remember { mutableStateOf<List<StopWithLines>?>(null) }

    LaunchedEffect(repository, ids) {
        stops = if (repository == null) null else try {
            withContext(Dispatchers.IO) {
                ids.mapNotNull { repository.stop(it) }
                    .sortedWith(compareBy({ foldForSearch(it.stop.name) }, { foldForSearch(it.stop.commune) }))
            }
        } catch (e: Exception) {
            Log.w("FavoritesScreen", "load failed", e)
            emptyList()
        }
    }

    AppScaffold(title = "Favoris", navController = navController) {
        val list = stops
        when {
            list == null -> Unit
            list.isEmpty() -> Box(Modifier.fillMaxSize().testTag("empty"), contentAlignment = Alignment.Center) {
                Text("Aucun arrêt favori", color = Colors.Secondary, fontSize = 18.sp, textAlign = TextAlign.Center)
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(list, key = { it.stop.id }) { s ->
                    StopRow(s) { navController.navigate(Routes.stop(s.stop.id)) }
                }
            }
        }
    }
}
