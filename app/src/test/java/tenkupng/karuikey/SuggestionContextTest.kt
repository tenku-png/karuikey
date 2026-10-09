package tenkupng.karuikey

import org.junit.Assert.assertEquals
import org.junit.Test

class SuggestionContextTest {
    private fun parse(text: String) = SuggestionSession.parseBeforeCursor(text)

    @Test
    fun wordAtCursorWithPreviousWordsNearestFirst() {
        assertEquals(SuggestionSession.TextContext("hel", listOf("say", "to", "want")),
            parse("I want to say hel"))
    }

    @Test
    fun spaceBeforeCursorGivesNextWordContext() {
        assertEquals(SuggestionSession.TextContext("", listOf("hello")), parse("hello "))
    }

    @Test
    fun punctuationBreaksContext() {
        assertEquals(SuggestionSession.TextContext("wor", emptyList(), true), parse("Hi. wor"))
        assertEquals(SuggestionSession.TextContext("", emptyList(), true), parse("end."))
    }

    @Test
    fun cyrillicApostropheAndDigitsStayInsideWords() {
        assertEquals(SuggestionSession.TextContext("при", listOf("всем")), parse("всем при"))
        assertEquals(SuggestionSession.TextContext("don't", emptyList(), true), parse("don't"))
        assertEquals(SuggestionSession.TextContext("mp3", emptyList(), true), parse("mp3"))
    }

    @Test
    fun emptyAndLineBreakInputs() {
        assertEquals(SuggestionSession.TextContext("", emptyList(), true), parse(""))
        assertEquals(SuggestionSession.TextContext("b", emptyList(), true), parse("a\nb"))
    }
}
