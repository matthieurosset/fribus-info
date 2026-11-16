package ch.fribusinfo.ui

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import ch.fribusinfo.data.StopWithLines
import ch.fribusinfo.data.TimetableRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Search: the field lives in the bar; results update as you type (debounced, off the main thread). */
@Composable
fun SearchScreen(navController: NavController, repository: TimetableRepository?) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<StopWithLines>>(emptyList()) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    LaunchedEffect(repository, query) {
        if (repository == null || query.isBlank()) { results = emptyList(); return@LaunchedEffect }
        delay(150)
        results = try {
            withContext(Dispatchers.IO) { repository.search(query) }
        } catch (e: Exception) {
            Log.w("SearchScreen", "search failed", e)
            emptyList()
        }
    }

    AppScaffold(
        title = "Rechercher",
        navController = navController,
        onBack = { navController.popBackStack() },
        titleContent = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text("Rechercher un arrêt", color = Color.White.copy(alpha = 0.7f), fontSize = 18.sp, maxLines = 1)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
                        cursorBrush = SolidColor(Color(0xFF018BC1)),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("search-field"),
                    )
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = "Effacer", tint = Color.White)
                    }
                }
            }
        },
    ) {
        when {
            query.isBlank() -> Unit
            results.isEmpty() -> if (repository != null) NoMatch()
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(results, key = { it.stop.id }) { s ->
                    StopRow(s) { navController.navigate(Routes.stop(s.stop.id)) }
                }
            }
        }
    }
}

@Composable
private fun NoMatch() {
    Box(Modifier.fillMaxSize().testTag("empty"), contentAlignment = Alignment.Center) {
        Text("Aucun arrêt trouvé", color = Colors.Secondary, fontSize = 18.sp, textAlign = TextAlign.Center)
    }
}
