package tenkupng.karuikey

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictionHistoryTest {
    @Test
    fun historyRanksNextWordsAndKeepsPrefixesLiteral() {
        val model = HistoryModel()
        model.record("Hello", "WORLD")
        model.record("hello", "world")
        model.record("раз", "world")

        val next = ArrayList<String>()
        model.fill("", "world", next)
        assertEquals("hello", next.first())

        val prefix = ArrayList<String>()
        model.fill("при", null, prefix)
        assertTrue(prefix.none { it == "раз" })
    }
}
