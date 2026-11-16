package ch.fribusinfo.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchNoMatchTest {
    @Test fun neverShownWhileQueryIsPending() {
        assertFalse(shouldShowNoMatch("gare", null, true))        // restored/typed, not yet resolved
        assertFalse(shouldShowNoMatch("gare", "gar", true))       // previous query resolved empty
        assertFalse(shouldShowNoMatch("gare", "gare", false))     // has results
    }

    @Test fun shownOnceCurrentQueryResolvedEmpty() {
        assertTrue(shouldShowNoMatch("zzzz", "zzzz", true))
    }

    @Test fun neverShownForBlankQuery() {
        assertFalse(shouldShowNoMatch("", "", true))
        assertFalse(shouldShowNoMatch("  ", "  ", true))
    }
}
