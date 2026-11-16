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
import ch.fribusinfo.data.TripStop
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
    private lateinit var stops: List<TripStop>
    private var openedStop: String? = null
    private val chosenTag get() = "trip-row-${dep.seq}"
    private val terminusTag get() = "trip-row-${stops.last().seq}"

    private companion object {
        /** Fribourg, Tilleul/Cathédrale: a stop with line 2 that is not the first of its trip. */
        const val TEST_STOP = 8587255L
        const val TEST_LINE = "2"
    }

    @Before fun setUp() {
        repo = TimetableRepository(TimetableDb.open(ctx))
        // Today at noon: the bundled DB is rebuilt weekly and drops past trips, so no fixed date.
        val noon = ZonedDateTime.now(ZoneId.of("Europe/Zurich")).withHour(12).withMinute(0).withSecond(0).withNano(0)
        // A trip of TEST_LINE leaving TEST_STOP with at least one stop before it.
        dep = repo.departures(TEST_STOP, noon, limit = 500).firstOrNull { d ->
            d.line.label == TEST_LINE && repo.trip(d.tripId, d.serviceDate).first().seq < d.seq
        } ?: error("No line $TEST_LINE trip with an earlier stop leaves stop $TEST_STOP after $noon in the bundled DB")
        stops = repo.trip(dep.tripId, dep.serviceDate)
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
        rule.onAllNodesWithTag(chosenTag).fetchSemanticsNodes().isNotEmpty()
    }

    private fun rowText(seq: Int): String =
        rule.onNodeWithTag("trip-row-$seq").fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text } ?: ""

    private fun ImageBitmap.pixels(): IntArray = IntArray(width * height).also { readPixels(it) }

    @Test fun titleShowsBadgeAndHeadsign() {
        show(); waitRows()
        rule.onNodeWithTag("title-badge").assertIsDisplayed()
        rule.onNodeWithText(dep.headsign).assertIsDisplayed()
    }

    @Test fun minutesStartAtChosenStopAndBlankBefore() {
        show(); waitRows()
        val ci = stops.indexOfFirst { it.seq == dep.seq }
        val minutes = Regex("[0-9]+'")
        val chosen = rowText(dep.seq)
        assertTrue(chosen, chosen.contains("0'"))
        // Previous stop (peeking above the chosen one) shows no minutes.
        val prev = rowText(stops[ci - 1].seq)
        assertTrue(prev, prev.contains(stops[ci - 1].stop.stop.name))
        assertTrue(prev, !minutes.containsMatchIn(prev))
        // The stop after the chosen one is labelled in whole minutes from it.
        val expected = java.time.Duration.between(stops[ci].scheduled, stops[ci + 1].scheduled).seconds / 60
        val after = rowText(stops[ci + 1].seq)
        assertTrue(after, after.contains("$expected'"))
    }

    @Test fun chosenStopIsVisibleOnOpen() {
        show(); waitRows()
        rule.onNodeWithTag(chosenTag).assertIsDisplayed()
    }

    @Test fun chosenRowIsBlueWithWhiteText() {
        show(); waitRows()
        val img = rule.onNodeWithTag(chosenTag).captureToImage()
        assertEquals(Colors.PearlCurrent.toArgb(), img.pixels()[2 + 2 * img.width])
        val name = rule.onNodeWithText(repo.stop(TEST_STOP)!!.stop.name, useUnmergedTree = true)
            .captureToImage().pixels()
        assertTrue(name.any { it == Color.White.toArgb() })
    }

    @Test fun terminusRowHasLightBackgroundAndFilledRedDot() {
        show(); waitRows()
        rule.onNodeWithTag("trip-list").performScrollToNode(hasTestTag(terminusTag))
        val img = rule.onNodeWithTag(terminusTag).captureToImage()
        val px = img.pixels()
        assertEquals(Colors.PearlEnd.toArgb(), px[2 + 2 * img.width])
        val d = rule.density.density
        val dot = px[(PEARL_CX.value * d).toInt() + (img.height / 2) * img.width]
        assertEquals(0xFF8B1010.toInt(), dot)
    }

    @Test fun tappingAStopOpensItsDepartures() {
        show(); waitRows()
        rule.onNodeWithTag(chosenTag).performClick()
        rule.waitForIdle()
        assertEquals(TEST_STOP.toString(), openedStop)
    }
}
