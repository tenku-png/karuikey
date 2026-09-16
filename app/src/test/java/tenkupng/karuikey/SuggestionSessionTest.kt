package tenkupng.karuikey

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun keepsOnlyThreeRecentCompletedWords() {
        val session = SuggestionSession()

        session.completeWord("one")
        session.completeWord("two")
        session.completeWord("three")
        session.completeWord("four")

        assertEquals("four", session.previousWord)
        assertEquals("three", session.recentWord(1))
        assertEquals("two", session.recentWord(2))
        assertEquals(null, session.recentWord(3))
    }

    @Test
    fun automaticSpaceIsTransientSessionState() {
        val session = SuggestionSession()

        assertFalse(session.hasAutomaticSpace)
        session.markAutomaticSpace()
        assertTrue(session.hasAutomaticSpace)
        session.clearAutomaticSpace()
        assertFalse(session.hasAutomaticSpace)
        session.markAutomaticSpace()
        session.clear()
        assertFalse(session.hasAutomaticSpace)
    }
}
