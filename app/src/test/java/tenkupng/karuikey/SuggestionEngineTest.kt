package tenkupng.karuikey

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionEngineTest {
    @Test
    fun prefixSuggestionsRemainAvailableAsWordsGrowInBothLanguages() {
        val suggestions = mutableListOf<String>()

        listOf("h", "he", "hel").forEach { prefix ->
            SuggestionEngine.fill("en_US", prefix, suggestions)
            assertTrue(suggestions.isNotEmpty())
        }
        listOf("п", "пр", "при").forEach { prefix ->
            SuggestionEngine.fill("ru_RU", prefix, suggestions)
            assertTrue(suggestions.isNotEmpty())
        }
    }

    @Test
    fun returnsAtMostThreeLocaleSpecificPrefixMatchesInStableOrder() {
        val english = mutableListOf<String>()
        SuggestionEngine.fill("en_US", "th", english)
        assertEquals(listOf("the", "that", "this"), english)

        val russian = mutableListOf<String>()
        SuggestionEngine.fill("ru_RU", "при", russian)
        assertEquals(listOf("при", "привет", "пример"), russian)
    }

    @Test
    fun unsupportedLayoutLanguageDoesNotBorrowAnotherLanguageDictionary() {
        val suggestions = mutableListOf("stale")

        SuggestionEngine.fill("de_DE", "ha", suggestions)

        assertEquals(emptyList<String>(), suggestions)
        assertEquals(false, SuggestionEngine.hasDictionary("de_DE"))
        assertEquals(true, SuggestionEngine.hasDictionary("en_US"))
        assertEquals(true, SuggestionEngine.hasDictionary("ru_RU"))
    }

    @Test
    fun contextChangesNextWordRankingWithoutChangingTheLexicon() {
        val suggestions = mutableListOf<String>()

        SuggestionEngine.fill("en_US", "thank", "", suggestions)
        assertEquals(listOf("you", "the"), suggestions.take(2))

        SuggestionEngine.fill("en_US", "how", "", suggestions)
        assertEquals(listOf("are", "do"), suggestions.take(2))
    }

    @Test
    fun contextChangesTheOrderOfTheSamePrefixCandidates() {
        val suggestions = mutableListOf<String>()

        SuggestionEngine.fill("en_US", "thank", "th", suggestions)
        assertEquals(listOf("the", "that", "this"), suggestions)

        SuggestionEngine.fill("en_US", "good", "th", suggestions)
        assertEquals(listOf("this", "that", "the"), suggestions)
    }

    @Test
    fun contextStillFiltersCompletionsByTheCurrentPrefix() {
        val suggestions = mutableListOf<String>()

        SuggestionEngine.fill("ru_RU", "привет", "м", suggestions)

        assertEquals("мир", suggestions.first())
        assertEquals(3, suggestions.size)
    }

    @Test
    fun gestureLookupRequiresAConfidentExactSequence() {
        assertEquals("hello", SuggestionEngine.findGestureCandidate("en_US", "hello"))
        assertEquals("hello", SuggestionEngine.findGestureCandidate("en_US", "helo"))
        assertEquals(null, SuggestionEngine.findGestureCandidate("en_US", "hxlo"))
        assertEquals("привет", SuggestionEngine.findGestureCandidate("ru_RU", "привет"))
    }
}
