package ch.fribusinfo.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FavoritesOrderTest {
    @Test fun lastAddedComesFirst() {
        val l = toggledFavorites(toggledFavorites(toggledFavorites(emptyList(), 1), 2), 3)
        assertEquals(listOf(3L, 2L, 1L), l)
    }

    @Test fun toggleRemovesKeepingOrder() = assertEquals(listOf(3L, 1L), toggledFavorites(listOf(3L, 2L, 1L), 2))

    @Test fun storedOrderIsKept() = assertEquals(listOf(9L, 4L, 7L), parseFavorites("9,4,7", setOf("1")))

    @Test fun legacySetMigratesDeterministically() =
        assertEquals(listOf(4L, 7L, 9L), parseFavorites(null, setOf("9", "4", "7", "x")))

    @Test fun emptyStoredValueIsEmpty() = assertEquals(emptyList<Long>(), parseFavorites("", setOf("5")))
}
