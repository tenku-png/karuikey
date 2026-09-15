package tenkupng.karuikey

import org.junit.Assert.assertEquals
import org.junit.Test

class SuggestionEngineTest {
    @Test
    fun returnsAtMostThreeLocaleSpecificPrefixMatchesInStableOrder() {
        val english = mutableListOf<String>()
        SuggestionEngine.fill("en_US", "th", english)
        assertEquals(listOf("the", "that", "this"), english)

        val russian = mutableListOf<String>()
        SuggestionEngine.fill("ru_RU", "при", russian)
        assertEquals(listOf("при", "привет", "пример"), russian)
    }
}
