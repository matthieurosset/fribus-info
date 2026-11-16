package ch.fribusinfo.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import ch.fribusinfo.data.Departure
import ch.fribusinfo.data.TimetableDb
import ch.fribusinfo.data.TimetableRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class TripScreenTest {
    @get:Rule val rule = createComposeRule()
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var repo: TimetableRepository
    private lateinit var dep: Departure
    private var openedStop: String? = null

    @Before fun setUp() {
        repo = TimetableRepository(TimetableDb.open(ctx))
        val noon = ZonedDateTime.of(2026, 11, 17, 12, 0, 0, 0, ZoneId.of("Europe/Zurich"))
        // A line-2 trip leaving Tilleul/Cathedrale with at least one stop before it.
        dep = repo.departures(8587255L, noon).first { d ->
            d.line.label == "2" && repo.trip(d.tripId, d.serviceDate).first().seq < d.seq
        }
    }

    private fun show() = rule.setContent {
        FriBusTheme {
            val nav = rememberNavController()
            NavHost(nav, "trip/{tripId}/{serviceDate}/{fromSeq}") {
                composable("trip/{tripId}/{serviceDate}/{fromSeq}") {
                    TripScreen(dep.tripId, dep.serviceDate, dep.seq, null, nav, repo)
                }
                composable("stop/{stopId}") { e -> openedStop = e.arguments?.getString("stopId") }
                composable("search") { }
                composable("nearby") { }
            }
        }
    }

    private fun waitRows() = rule.waitUntil(5000) {
        rule.onAllNodesWithTag("trip-row-chosen").fetchSemanticsNodes().isNotEmpty()
    }

    private fun rowText(i: Int): String =
        rule.onAllNodesWithTag("trip-row")[i].fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text } ?: ""

    private fun ImageBitmap.pixels(): IntArray = IntArray(width * height).also { readPixels(it) }

    @Test fun titleShowsBadgeAndHeadsign() {
        show(); waitRows()
        rule.onNodeWithTag("title-badge").assertIsDisplayed()
        rule.onNodeWithText(dep.headsign).assertIsDisplayed()
    }

    @Test fun minutesStartAtChosenStopAndBlankBefore() {
        show(); waitRows()
        val chosen = rule.onNodeWithTag("trip-row-chosen").fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.Text)!!.joinToString(" ") { it.text }
        assertTrue(chosen, chosen.contains("0'"))
        val stops = repo.trip(dep.tripId, dep.serviceDate)
        val firstSeq = stops.first().seq
        assertTrue(firstSeq < dep.seq)
        // Previous stop (peeking above the chosen one) shows no minutes.
        val prev = rowText(0)
        assertTrue(prev, prev.contains(stops[stops.indexOfFirst { it.seq == dep.seq } - 1].stop.stop.name))
        assertTrue(prev, !prev.contains("'"))
        // The stop after the chosen one is labelled in whole minutes from it.
        val ci = stops.indexOfFirst { it.seq == dep.seq }
        val expected = java.time.Duration.between(stops[ci].scheduled, stops[ci + 1].scheduled).seconds / 60
        val after = rule.onAllNodesWithTag("trip-row")[1].fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.Text)!!.joinToString(" ") { it.text }
        assertTrue(after, after.contains("$expected'"))
    }

    @Test fun chosenStopIsVisibleOnOpen() {
        show(); waitRows()
        rule.onNodeWithTag("trip-row-chosen").assertIsDisplayed()
    }

    @Test fun chosenRowIsBlueWithWhiteText() {
        show(); waitRows()
        val img = rule.onNodeWithTag("trip-row-chosen").captureToImage()
        assertEquals(Colors.PearlCurrent.toArgb(), img.pixels()[2 + 2 * img.width])
        val name = rule.onNodeWithText(repo.stop(8587255L)!!.stop.name, useUnmergedTree = true)
            .captureToImage().pixels()
        assertTrue(name.any { it == Color.White.toArgb() })
    }

    @Test fun terminusRowHasLightBackgroundAndFilledRedDot() {
        show(); waitRows()
        rule.onNodeWithTag("trip-list").performScrollToNode(hasTestTag("trip-row-terminus"))
        val img = rule.onNodeWithTag("trip-row-terminus").captureToImage()
        val px = img.pixels()
        assertEquals(Colors.PearlEnd.toArgb(), px[2 + 2 * img.width])
        val d = rule.density.density
        val dot = px[(60 * d).toInt() + (img.height / 2) * img.width]
        assertEquals(0xFF8B1010.toInt(), dot)
    }

    @Test fun tappingAStopOpensItsDepartures() {
        show(); waitRows()
        rule.onNodeWithTag("trip-row-chosen").performClick()
        rule.waitForIdle()
        assertEquals("8587255", openedStop)
    }
}
