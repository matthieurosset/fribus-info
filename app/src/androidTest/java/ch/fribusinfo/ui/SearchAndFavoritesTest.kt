package ch.fribusinfo.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import ch.fribusinfo.data.Favorites
import ch.fribusinfo.data.TimetableDb
import ch.fribusinfo.data.TimetableRepository
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SearchAndFavoritesTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var repo: TimetableRepository
    private lateinit var favorites: Favorites
    private var opened: String? = null

    @Before fun setUp() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        repo = TimetableRepository(TimetableDb.open(ctx))
        favorites = Favorites(ctx)
        favorites.ids.value.forEach { favorites.toggle(it) }
    }

    @After fun tearDown() {
        favorites.ids.value.forEach { favorites.toggle(it) }
    }

    private fun show(start: String) = rule.setContent {
        FriBusTheme {
            val nav = rememberNavController()
            NavHost(nav, start) {
                composable("nearby") { }
                composable("search") { SearchScreen(nav, repo) }
                composable("favorites") { FavoritesScreen(nav, repo, favorites) }
                composable("stop/{stopId}") { e -> opened = e.arguments?.getString("stopId") }
            }
        }
    }

    private fun rows() = rule.onAllNodesWithTag("stop-row")
    private fun waitRows() = rule.waitUntil(5000) { rows().fetchSemanticsNodes().isNotEmpty() }

    @Test fun typingGareListsStopsWithoutDistance() {
        show("search")
        rule.onNodeWithText("Rechercher un arrêt").assertIsDisplayed()
        rule.onNodeWithTag("search-field").performTextInput("gare")
        waitRows()
        val first = rows().fetchSemanticsNodes().size
        assert(first > 0)
        rule.onAllNodesWithText(" m", substring = true).assertCountEquals(0)
    }

    @Test fun clearButtonEmptiesQueryAndList() {
        show("search")
        rule.onNodeWithTag("search-field").performTextInput("gare")
        waitRows()
        rule.onNodeWithContentDescription("Effacer").performClick()
        rule.waitUntil(5000) { rows().fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText("Rechercher un arrêt").assertIsDisplayed()
    }

    @Test fun noMatchShowsMessage() {
        show("search")
        rule.onNodeWithTag("search-field").performTextInput("zzzzqq")
        rule.waitUntil(5000) { rule.onAllNodesWithTag("empty").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Aucun arrêt trouvé").assertIsDisplayed()
    }

    @Test fun tappingResultOpensStop() {
        show("search")
        rule.onNodeWithTag("search-field").performTextInput("gare")
        waitRows()
        rows()[0].performClick()
        rule.waitUntil(5000) { opened != null }
    }

    @Test fun favoritesEmptyState() {
        show("favorites")
        rule.onNodeWithText("Aucun arrêt favori").assertIsDisplayed()
        rule.onNodeWithContentDescription("Menu").assertIsDisplayed()
    }

    @Test fun favoritesSortedByNameAndLive() {
        val all = repo.search("gare", 50).map { it.stop }.sortedBy { it.name }
        val a = all.first(); val b = all.last { it.id != a.id }
        favorites.toggle(b.id); favorites.toggle(a.id)
        show("favorites")
        waitRows()
        rows().assertCountEquals(2)
        rows()[0].assert(hasText(a.name))
        rows()[1].assert(hasText(b.name))
        favorites.toggle(a.id)
        rule.waitUntil(5000) { rows().fetchSemanticsNodes().size == 1 }
        rows()[0].assert(hasText(b.name))
        rows()[0].performClick()
        rule.waitUntil(5000) { opened == b.id.toString() }
    }
}
