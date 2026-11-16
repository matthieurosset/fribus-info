package ch.fribusinfo.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyScrollTest {
    @Test fun firstListScrollsToTop() = assertTrue(shouldScrollToTop(null, listOf(1L, 2L)))
    @Test fun newLocationWithDifferentListScrollsToTop() = assertTrue(shouldScrollToTop(listOf(1L, 2L), listOf(2L, 1L, 3L)))
    @Test fun refreshOfSameListKeepsScroll() = assertFalse(shouldScrollToTop(listOf(1L, 2L), listOf(1L, 2L)))
}
