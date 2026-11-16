package ch.fribusinfo.ui

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.flow.filter
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

/** « Aucun arrêt trouvé » only once the current query has actually been resolved. */
fun shouldShowNoMatch(query: String, resolvedQuery: String?, resultsEmpty: Boolean) =
    query.isNotBlank() && resolvedQuery == query && resultsEmpty

/** Search: the field lives in the bar; results update as you type (debounced, off the main thread). */
@Composable
fun SearchScreen(navController: NavController, repository: TimetableRepository?) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<StopWithLines>>(emptyList()) }
    var resolvedQuery by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()

    // Open the keyboard on entry only for a fresh search, not when returning to a saved query.
    LaunchedEffect(Unit) { if (query.isEmpty()) runCatching { focus.requestFocus() } }
    // Hide the keyboard as soon as the list is scrolled.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.filter { it }.collect { keyboard?.hide() }
    }

    LaunchedEffect(repository, query) {
        if (repository == null || query.isBlank()) { results = emptyList(); resolvedQuery = null; return@LaunchedEffect }
        delay(150)
        val found = try {
            withContext(Dispatchers.IO) { repository.search(query) }
        } catch (e: Exception) {
            Log.w("SearchScreen", "search failed", e)
            emptyList()
        }
        results = found
        resolvedQuery = query
    }

    AppScaffold(
        title = "Rechercher",
        navController = navController,
        onBack = { navController.popBackStack() },
        titleContent = {
            Row(Modifier.fillMaxWidth().padding(end = 39.dp), verticalAlignment = Alignment.CenterVertically) {
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
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
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
            shouldShowNoMatch(query, resolvedQuery, results.isEmpty()) -> NoMatch()
            results.isEmpty() -> Unit
            else -> LazyColumn(Modifier.fillMaxSize(), state = listState) {
                items(results, key = { it.stop.id }) { s ->
                    StopRow(s) {
                        keyboard?.hide()
                        focusManager.clearFocus()
                        navController.navigate(Routes.stop(s.stop.id))
                    }
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
