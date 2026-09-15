package tenkupng.karuikey

import org.junit.Assert.assertEquals
import org.junit.Test

class SuggestionSessionTest {
    @Test
    fun editsAndCompletionKeepOnlyActiveSessionContext() {
        val session = SuggestionSession()

        session.append("he")
        assertEquals(1, session.deleteLastCodePoint())
        assertEquals("h", session.prefix.toString())
        session.append("ello")
        session.completeCurrentWord()
        assertEquals("hello", session.previousWord)
        assertEquals("", session.prefix.toString())

        session.clear()
        assertEquals(null, session.previousWord)
    }

    @Test
    fun candidateCompletionReplacesThePrefixInsteadOfAppendingToIt() {
        val session = SuggestionSession()
        session.append("hel")

        session.completeWord("hello")

        assertEquals("hello", session.previousWord)
        assertEquals("", session.prefix.toString())
    }
}
