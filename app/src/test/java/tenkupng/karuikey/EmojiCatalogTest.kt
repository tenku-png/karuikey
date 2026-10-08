package tenkupng.karuikey

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EmojiCatalogTest {
    private val sample = listOf(
        "FACES\t😀\t\tgrinning face\tsmile|happy\tширoко улыбается\tулыбка",
        "PEOPLE\t👋\t👋🏻 👋🏼\twaving hand\thello\tмашет рукой\tпривет",
        "UNKNOWN\t❓\t\tquestion\t\t\t",
        "FLAGS\t🇷🇺\t\tflag: Russia\t\tфлаг: Россия\t"
    )

    @Test
    fun parsesCategoriesNamesKeywordsAndVariants() {
        val data = EmojiCatalog.parse(sample.asSequence()) { true }
        assertEquals(listOf(EmojiCategory.FACES, EmojiCategory.PEOPLE, EmojiCategory.FLAGS),
            data.keys.toList())
        val wave = data.getValue(EmojiCategory.PEOPLE).single()
        assertEquals("waving hand", wave.name)
        assertEquals(listOf("👋🏻", "👋🏼"), wave.variants)
        assertTrue(wave.keywords.containsAll(listOf("hello", "машет рукой", "привет")))
    }

    @Test
    fun unsupportedEmojiAndVariantsAreDropped() {
        val data = EmojiCatalog.parse(sample.asSequence()) { it != "🇷🇺" && it != "👋🏼" }
        assertTrue(EmojiCategory.FLAGS !in data)
        assertEquals(listOf("👋🏻"), data.getValue(EmojiCategory.PEOPLE).single().variants)
    }

    @Test
    fun generatedAssetHasEveryCategoryAndNames() {
        val asset = File("src/main/assets/emoji/emoji.tsv")
        val data = EmojiCatalog.parse(asset.readLines().asSequence()) { true }
        assertEquals(EmojiCategory.entries - EmojiCategory.RECENT, data.keys.toList())
        assertTrue(data.values.sumOf { it.size } > 1800)
        assertTrue(data.values.flatten().all { it.name.isNotEmpty() })
    }
}
