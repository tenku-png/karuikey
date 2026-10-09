package tenkupng.karuikey

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class SuggestionRankerTest {
    private fun word(text: String, score: Int) = SuggestionRanker.ScoredWord(text, score)

    @Test
    fun strongCorrectionOfUnknownWordAutoCorrectsAndKeepsTypedWord() {
        val ranked = SuggestionRanker.rank(
            "teh", listOf(word("ten", 300_000), word("the", 900_000)), false, Locale.ENGLISH
        )

        assertEquals("the", ranked.autoCorrection)
        assertEquals(listOf("the", "teh", "ten"), ranked.words)
    }

    @Test
    fun validTypedWordIsNotAutoCorrected() {
        val ranked = SuggestionRanker.rank(
            "the", listOf(word("the", 900_000), word("then", 800_000), word("they", 700_000)),
            true, Locale.ENGLISH
        )

        assertNull(ranked.autoCorrection)
        assertEquals(listOf("then", "they"), ranked.words)
    }

    @Test
    fun weakCorrectionIsOnlySuggested() {
        val ranked = SuggestionRanker.rank(
            "xq", listOf(word("we", 10)), false, Locale.ENGLISH
        )

        assertNull(ranked.autoCorrection)
        assertEquals(listOf("we"), ranked.words)
    }

    @Test
    fun capitalizationFollowsInputAndDuplicatesCollapse() {
        val ranked = SuggestionRanker.rank(
            "Hel", listOf(word("hello", 500_000), word("Hello", 400_000), word("help", 300_000)),
            false, Locale.ENGLISH
        )

        assertEquals(listOf("Hello", "Help"), ranked.words.filter { it != "Hel" })
    }

    @Test
    fun digitsAndAddressesAreNeverAutoCorrected() {
        assertNull(SuggestionRanker.rank("mp3x", listOf(word("mp3", 900_000)), false,
            Locale.ENGLISH).autoCorrection)
        assertNull(SuggestionRanker.rank("a@b", listOf(word("ab", 900_000)), false,
            Locale.ENGLISH).autoCorrection)
    }

    @Test
    fun editDistanceCountsTranspositionOnce() {
        assertEquals(1, SuggestionRanker.editDistance("teh", "the"))
        assertEquals(3, SuggestionRanker.editDistance("", "abc"))
    }

    @Test
    fun learnedWordsAreBoostedButNeverAutoCorrectionTargets() {
        val personalized = SuggestionRanker.personalize(
            listOf(word("прием", 300_000), word("привет", 200_000)),
            listOf("приветик" to 5, "привет" to 3)
        ) { if (it == "привет") 1 else 0 }

        val ranked = SuggestionRanker.rank("прив", personalized, false, Locale.ROOT)
        assertEquals("привет", ranked.words.first())
        assertNull(SuggestionRanker.rank("пр", SuggestionRanker.personalize(
            emptyList(), listOf("приветик" to 5)) { 0 }, false, Locale.ROOT).autoCorrection)
    }

    @Test
    fun learnedTypedWordIsNotCorrected() {
        val ranked = SuggestionRanker.rank(
            "зорошо", listOf(word("хорошо", 900_000)), true, Locale.ROOT
        )
        assertNull(ranked.autoCorrection)
        assertEquals(listOf("хорошо"), ranked.words)
    }
}
