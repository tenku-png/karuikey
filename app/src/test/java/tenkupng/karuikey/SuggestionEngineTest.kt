package tenkupng.karuikey

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionEngineTest {
    @Test
    fun unsupportedLayoutLanguageDoesNotBorrowAnotherLanguageDictionary() {
        val suggestions = mutableListOf("stale")

        SuggestionEngine.fill("de_DE", "ha", suggestions)

        assertEquals(emptyList<String>(), suggestions)
        assertFalse(SuggestionEngine.hasDictionary("de_DE"))
        assertTrue(SuggestionEngine.hasDictionary("en_US"))
        assertTrue(SuggestionEngine.hasDictionary("ru_RU"))
    }
}
