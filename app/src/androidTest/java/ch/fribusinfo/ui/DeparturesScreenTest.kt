package ch.fribusinfo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import ch.fribusinfo.data.Departure
import ch.fribusinfo.data.Favorites
import ch.fribusinfo.data.Line
import ch.fribusinfo.data.TimetableDb
import ch.fribusinfo.data.TimetableRepository
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** FriBusTheme with the blinking of the « now » button off (infinite animations never idle). */
@Composable
internal fun TestTheme(content: @Composable () -> Unit) =
    FriBusTheme { CompositionLocalProvider(LocalBlinkEnabled provides false) { content() } }

class DeparturesScreenTest {
    @get:Rule val rule = createComposeRule()
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var repo: TimetableRepository
    private lateinit var favorites: Favorites
    private val zone = ZoneId.of("Europe/Zurich")
    // Today at noon: the bundled DB is rebuilt weekly and drops past trips, so no fixed date.
    private val noon = ZonedDateTime.now(zone).withHour(12).withMinute(0).withSecond(0).withNano(0)
    private val stopId = 8587255L

    @Before fun setUp() {
        ctx.getSharedPreferences("favorites", 0).edit().clear().commit()
        favorites = Favorites(ctx)
        repo = TimetableRepository(TimetableDb.open(ctx))
    }

    private fun dep(minutes: Long, seconds: Long = 0) = Departure(
        1, Line(1, "2", 0xFF2440, 0xFFFFFF, false), "Schönberg Dunant", "Fribourg",
        noon.toLocalDate(), 3, noon.plusMinutes(minutes).plusSeconds(seconds),
    )

    @Test fun rowShowsBadgeHeadsignCommuneAndMinutes() {
        rule.setContent { TestTheme { DepartureRow(dep(7, 30), noon, false, null) {} } }
        rule.onNodeWithText("2").assertIsDisplayed()
        rule.onNodeWithText("Schönberg Dunant").assertIsDisplayed()
        rule.onNodeWithText("Fribourg").assertIsDisplayed()
        rule.onNodeWithText("7'").assertIsDisplayed()
    }

    @Test fun rowUnderOneMinuteShowsZero() {
        rule.setContent { TestTheme { DepartureRow(dep(0, 59), noon, false, null) {} } }
        rule.onNodeWithText("0'").assertIsDisplayed()
    }

    @Test fun rowAbsoluteShowsClockTime() {
        rule.setContent { TestTheme { DepartureRow(dep(7), noon, true, null) {} } }
        rule.onNodeWithText("12:07").assertIsDisplayed()
    }

    @Test fun lateRowRelativeShowsShiftedMinutesAndNoPill() {
        rule.setContent { TestTheme { DepartureRow(dep(7), noon, false, java.time.Duration.ofMinutes(3)) {} } }
        rule.onNodeWithText("10'", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(rule.onAllNodesWithTag("delay-badge", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }

    @Test fun lateRowAbsoluteShowsScheduledTimeAndPill() {
        rule.setContent { TestTheme { DepartureRow(dep(7), noon, true, java.time.Duration.ofMinutes(3)) {} } }
        rule.onNodeWithText("+3'", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("12:07", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun earlyRowAbsoluteShowsMinusPill() {
        rule.setContent { TestTheme { DepartureRow(dep(7), noon, true, java.time.Duration.ofMinutes(-2)) {} } }
        rule.onNodeWithText("-2'", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun onTimeRealtimeRowShowsPlusZeroPill() {
        rule.setContent { TestTheme { DepartureRow(dep(7), noon, true, java.time.Duration.ZERO) {} } }
        rule.onNodeWithText("+0'", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun rowWithoutRealtimeHasNoPill() {
        rule.setContent { TestTheme { DepartureRow(dep(7), noon, true, null) {} } }
        assertTrue(rule.onAllNodesWithTag("delay-badge", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }

    @Test fun earlyRowRelativeShowsEarlierMinutes() {
        rule.setContent { TestTheme { DepartureRow(dep(7), noon, false, java.time.Duration.ofMinutes(-2)) {} } }
        rule.onNodeWithText("5'", useUnmergedTree = true).assertIsDisplayed()
    }

    private fun bgPixel(): Int {
        val img = rule.onNodeWithTag("minutes-bg", useUnmergedTree = true).captureToImage()
        return IntArray(img.width * img.height).also { img.readPixels(it) }[6 + (img.height / 2) * img.width]
    }

    @Test fun passedRowHasGreyButtonAndGreyTitle() {
        rule.setContent { TestTheme { DepartureRow(dep(-1), noon, false, java.time.Duration.ZERO) {} } }
        rule.onNodeWithText("-1'", useUnmergedTree = true).assertIsDisplayed()
        val px = bgPixel()
        val r = (px shr 16) and 0xFF; val b = px and 0xFF
        assertTrue("grey expected, got ${Integer.toHexString(px)}", r in 0xD0..0xE0 && b in 0xD0..0xE0)
    }

    @Test fun upcomingRowHasBlueButton() {
        rule.setContent { TestTheme { DepartureRow(dep(5), noon, false, null) {} } }
        val px = bgPixel()
        assertTrue("blue expected, got ${Integer.toHexString(px)}", ((px shr 16) and 0xFF) < 0x20 && (px and 0xFF) in 0xB0..0xC8)
    }

    @Test fun hourFormatFromSixtyMinutes() {
        rule.setContent { TestTheme { DepartureRow(dep(65), noon, false, null) {} } }
        rule.onNodeWithText("1h05'", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun rowTapReportsClick() {
        var clicked = false
        rule.setContent { TestTheme { DepartureRow(dep(3), noon, false, null) { clicked = true } } }
        rule.onNodeWithText("Schönberg Dunant").performClick()
        assertTrue(clicked)
    }

    private fun show(now: () -> ZonedDateTime = { noon }, tickMillis: Long? = null, route: String? = null) =
        rule.setContent {
            TestTheme {
                val nav = rememberNavController()
                NavHost(nav, "stop/{stopId}") {
                    composable("stop/{stopId}") {
                        DeparturesScreen(nav, repo, favorites, stopId, now, tickMillis)
                    }
                    composable("search") { }
                    composable("nearby") { }
                    composable("trip/{tripId}/{serviceDate}/{fromSeq}") { }
                }
            }
        }

    private fun rowText(i: Int): String =
        rule.onAllNodesWithTag("departure-row")[i].fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text } ?: ""

    private fun waitRows() = rule.waitUntil(5000) {
        rule.onAllNodesWithTag("departure-row").fetchSemanticsNodes().isNotEmpty()
    }

    @Test fun screenListsRealDeparturesAndStopName() {
        show()
        waitRows()
        rule.onNodeWithText("Tilleul/Cathédrale").assertIsDisplayed()
        assertTrue(rowText(0).contains("'"))
    }

    @Test fun menuSwitchesToAbsoluteTimeAndPersists() {
        show()
        waitRows()
        rule.onNodeWithContentDescription("Plus").performClick()
        rule.onNodeWithText("Heure de départ absolue").performClick()
        rule.waitUntil(3000) {
            Regex("[0-9]{2}:[0-9]{2}").containsMatchIn(rowText(0))
        }
        assertTrue(Favorites(ctx).absoluteTime.value)
        rule.onNodeWithContentDescription("Plus").performClick()
        rule.onNodeWithText("Heure de départ en minutes").assertIsDisplayed()
    }

    @Test fun menuAddsAndRemovesFavorite() {
        show()
        waitRows()
        rule.onNodeWithContentDescription("Plus").performClick()
        rule.onNodeWithText("Ajouter aux favoris").performClick()
        rule.waitForIdle()
        assertTrue(stopId in favorites.ids.value)
        rule.onNodeWithContentDescription("Plus").performClick()
        rule.onNodeWithText("Supprimer des favoris").assertIsDisplayed()
    }

    @Test fun listReticksAndDroppsDepartedRows() {
        val now = mutableStateOf(noon)
        favorites.setAbsoluteTime(true)
        show(now = { now.value }, tickMillis = 200)
        waitRows()
        val before = repo.departures(stopId, noon)
        val firstText = rowText(0)
        now.value = before[0].scheduled.plusSeconds(61)
        rule.waitUntil(5000) {
            val rows = rule.onAllNodesWithTag("departure-row").fetchSemanticsNodes()
            rows.isNotEmpty() && rowText(0) != firstText
        }
    }

    @Test fun emptyWindowShowsMessage() {
        val night = ZonedDateTime.of(2030, 1, 1, 12, 0, 0, 0, zone)  // outside the bundled validity
        show(now = { night })
        rule.waitUntil(5000) { rule.onAllNodesWithTag("empty").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Pas de départ ces 2 prochaines heures").assertIsDisplayed()
    }
}
