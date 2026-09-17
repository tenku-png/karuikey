package tenkupng.karuikey

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SuggestionStatusTest {
    @Test
    fun userFacingStatusesDoNotExposeTheStorageFormat() {
        assertEquals("Suggestions ready", suggestionStatus(SuggestionEngine.DictionarySource.BUNDLED))
        assertEquals(
            "Local suggestions installed",
            suggestionStatus(SuggestionEngine.DictionarySource.EXTERNAL)
        )
        assertEquals(
            "Suggestions unavailable",
            suggestionStatus(SuggestionEngine.DictionarySource.NONE)
        )
        for (source in SuggestionEngine.DictionarySource.values()) {
            assertFalse(suggestionStatus(source).contains("KRD1"))
            assertFalse(suggestionStatus(source).contains("dictionary", ignoreCase = true))
        }
    }
}
