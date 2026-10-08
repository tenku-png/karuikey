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
        assertEquals("машет рукой", wave.localName)
        assertTrue(wave.keywords.containsAll(listOf("hello", "привет")))
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

    private val catalog = listOf(
        EmojiEntry("😺", "grinning cat", listOf("cat", "face"), localName = "кот улыбается"),
        EmojiEntry("🐈", "cat", listOf("pet"), localName = "кошка"),
        EmojiEntry("🙀", "weary cat", listOf("cat", "oh", "surprised"), localName = "испуганный кот"),
        EmojiEntry("🎉", "party popper", listOf("celebration", "tada"), localName = "хлопушка"),
        EmojiEntry("🧑‍🎓", "student", listOf("graduate", "education"), localName = "студент")
    )

    @Test
    fun exactNameRanksFirstThenNameWordThenKeywordThenSubstring() {
        assertEquals(listOf("🐈", "😺", "🙀", "🧑‍🎓"), EmojiCatalog.rank(catalog, "cat", emptyMap()).map { it.emoji })
        assertEquals(listOf("🎉"), EmojiCatalog.rank(catalog, "tad", emptyMap()).map { it.emoji })
        assertEquals(listOf("🧑‍🎓"), EmojiCatalog.rank(catalog, "ucat", emptyMap()).map { it.emoji })
    }

    @Test
    fun wholeWordBeatsLongerWordWithSamePrefix() {
        val hearts = listOf(
            EmojiEntry("🥰", "smiling face with hearts"),
            EmojiEntry("😍", "smiling face with heart-eyes"),
            EmojiEntry("❤️", "red heart")
        )
        assertEquals(listOf("❤️", "😍", "🥰"),
            EmojiCatalog.rank(hearts, "heart", emptyMap()).map { it.emoji })
    }

    @Test
    fun russianNamesAreSearchable() {
        assertEquals(listOf("😺", "🙀"), EmojiCatalog.rank(catalog, "кот", emptyMap()).map { it.emoji })
        assertEquals(listOf("🐈"), EmojiCatalog.rank(catalog, "Кошка", emptyMap()).map { it.emoji })
    }

    @Test
    fun frequentlyUsedEmojiWinTiesAndLimitApplies() {
        val usage = mapOf("🙀" to 5)
        assertEquals(listOf("🐈", "🙀", "😺", "🧑‍🎓"), EmojiCatalog.rank(catalog, "cat", usage).map { it.emoji })
        assertEquals(1, EmojiCatalog.rank(catalog, "cat", usage, limit = 1).size)
        assertTrue(EmojiCatalog.rank(catalog, "  ", usage).isEmpty())
    }
}
