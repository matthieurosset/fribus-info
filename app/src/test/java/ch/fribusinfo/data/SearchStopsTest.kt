package ch.fribusinfo.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchStopsTest {
    private fun s(id: Long, name: String, commune: String) =
        StopWithLines(Stop(id, name, commune, 0.0, 0.0), emptyList(), null)

    private val all = listOf(
        s(1, "Tilleul/Cathédrale", "Fribourg"),
        s(2, "Gare", "Marly"),
        s(3, "Gare routière", "Fribourg"),
        s(4, "Gare", "Fribourg"),
        s(5, "Forum/casino", "Granges-Paccot"),
        s(6, "Ancienne gare", "Bulle"),
    )

    private fun ids(q: String) = searchStops(all, q).map { it.stop.id }

    @Test fun everyWordMustMatchInCommuneAndName() {
        assertEquals(listOf(4L, 3L), ids("fribourg gare"))   // by name: "gare" < "gare routière"
        assertEquals(listOf(4L, 3L), ids("gare fribourg"))
        assertEquals(emptyList<Long>(), ids("gare zurich"))
    }

    @Test fun accentsCaseAndCommasAreIgnored() {
        assertEquals(listOf(1L), ids("CATHEDRALE"))
        assertEquals(listOf(1L), ids("fribourg, tilleul"))
        assertEquals(listOf(5L), ids("granges forum"))
    }

    @Test fun sortedByNameThenCommuneWithoutLimitOrPrefixPriority() {
        // « Ancienne gare » (contains, does not start with) is not pushed after the prefix matches
        assertEquals(listOf(6L, 4L, 2L, 3L), ids("gare"))
    }

    @Test fun blankMatchesNothing() {
        assertEquals(emptyList<Long>(), ids(""))
        assertEquals(emptyList<Long>(), ids("  , "))
    }
}
