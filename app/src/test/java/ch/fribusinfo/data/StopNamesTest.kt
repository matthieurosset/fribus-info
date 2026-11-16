package ch.fribusinfo.data

import org.junit.Assert.assertEquals
import org.junit.Test

class StopNamesTest {
    @Test fun splitsOnComma() {
        assertEquals("Tilleul/Cathédrale" to "Fribourg", splitStopName("Fribourg, Tilleul/Cathédrale"))
    }

    @Test fun splitsWithoutSpace() {
        assertEquals("Petit-Moncor" to "Villars-sur-Glâne", splitStopName("Villars-sur-Glâne,Petit-Moncor"))
    }

    @Test fun noCommaKeepsFullStringTwice() {
        assertEquals("Fribourg-Neuveville/Motta" to "Fribourg-Neuveville/Motta",
            splitStopName("Fribourg-Neuveville/Motta"))
    }

    @Test fun splitsOnFirstCommaOnly() {
        assertEquals("A, B" to "X", splitStopName("X, A, B"))
    }

    @Test fun foldRemovesAccentsAndCase() {
        assertEquals("cathedrale", foldForSearch("Cathédrale"))
        assertEquals("villars-sur-glane", foldForSearch("Villars-sur-Glâne"))
    }
}
