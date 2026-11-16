package ch.fribusinfo.ui

import android.location.Location
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import ch.fribusinfo.data.TimetableDb
import ch.fribusinfo.data.TimetableRepository
import ch.fribusinfo.location.LocationSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class NearbyScreenTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var repo: TimetableRepository

    @Before fun setUp() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        repo = TimetableRepository(TimetableDb.open(ctx))
    }

    private fun fake(lat: Double?, lon: Double = 0.0) = object : LocationSource {
        override fun updates(): Flow<Location?> = flowOf(
            lat?.let { Location("fake").apply { latitude = it; longitude = lon } },
        )
    }

    private fun show(source: LocationSource) = rule.setContent {
        FriBusTheme {
            val nav = rememberNavController()
            NavHost(nav, "nearby") {
                composable("nearby") { NearbyScreen(nav, repo, source, requestPermissionOnOpen = false) }
                composable("stop/{stopId}") { }
            }
        }
    }

    @Test fun nearFribourgListsNeuvevilleMottaFirstWithRoundedDistance() {
        show(fake(46.8032, 7.1597))
        rule.waitUntil(5000) { rule.onAllNodesWithTag("stop-row").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithTag("stop-row")[0]
            .assert(hasText("Neuveville/Motta"))
            .assert(hasText("100 m"))
    }

    @Test fun noLocationShowsMessageAndDrawerStillWorks() {
        show(fake(null))
        rule.onNodeWithText("Position indisponible").assertIsDisplayed()
        rule.onNodeWithContentDescription("Menu").performClick()
        rule.onNodeWithText("Favoris").assertIsDisplayed()
    }

    @Test fun farFromFribourgShowsNoStopMessage() {
        show(fake(47.3769, 8.5417))
        rule.waitUntil(5000) { rule.onAllNodesWithTag("empty").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Aucun arrêt à proximité").assertIsDisplayed()
    }
}
