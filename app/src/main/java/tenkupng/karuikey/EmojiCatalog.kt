package tenkupng.karuikey

import java.util.Locale

internal data class EmojiEntry(
    val emoji: String,
    val name: String,
    val keywords: String = "",
    val variants: List<String> = emptyList()
)

internal enum class EmojiCategory(val title: String, val tab: String) {
    RECENT("Recent", "⟳"),
    FACES("Faces", "🙂"),
    PEOPLE("People", "🧑"),
    ANIMALS("Animals & nature", "🐻"),
    FOOD("Food", "🍔"),
    TRAVEL("Travel & places", "🚗"),
    ACTIVITIES("Activities", "⚽"),
    OBJECTS("Objects", "💡"),
    SYMBOLS("Symbols", "💟"),
    FLAGS("Flags", "🏳️")
}

internal object EmojiCatalog {
    private val skinTones = listOf("🏻", "🏼", "🏽", "🏾", "🏿")

    private fun withSkinTones(base: String) = skinTones.map { tone -> base + tone }

    private val data = mapOf(
        EmojiCategory.FACES to listOf(
            EmojiEntry("😀", "grinning face", "smile happy улыбка радость"),
            EmojiEntry("😃", "grinning face big eyes", "smile happy"),
            EmojiEntry("😄", "grinning face smiling eyes", "smile happy"),
            EmojiEntry("😁", "beaming face", "smile happy"),
            EmojiEntry("😂", "face with tears of joy", "laugh lol tears"),
            EmojiEntry("🤣", "rolling on the floor laughing", "laugh lol"),
            EmojiEntry("😊", "smiling face with smiling eyes", "smile blush happy"),
            EmojiEntry("🙂", "slightly smiling face", "smile"),
            EmojiEntry("🙃", "upside down face", "silly"),
            EmojiEntry("😉", "winking face", "wink"),
            EmojiEntry("😍", "smiling face with heart eyes", "love heart"),
            EmojiEntry("😘", "face blowing a kiss", "love kiss"),
            EmojiEntry("😎", "smiling face with sunglasses", "cool"),
            EmojiEntry("🤔", "thinking face", "think"),
            EmojiEntry("😢", "crying face", "sad tear"),
            EmojiEntry("😭", "loudly crying face", "sad tear"),
            EmojiEntry("😡", "enraged face", "angry mad"),
            EmojiEntry("😱", "face screaming in fear", "scared fear"),
            EmojiEntry("😴", "sleeping face", "sleep tired"),
            EmojiEntry("🤗", "hugging face", "hug"),
            EmojiEntry("🤩", "star struck", "star excited"),
            EmojiEntry("🥳", "partying face", "party celebrate"),
            EmojiEntry("😇", "smiling face with halo", "angel"),
            EmojiEntry("🤯", "exploding head", "mind blown"),
            EmojiEntry("🤝", "handshake", "agreement"),
            EmojiEntry("❤️", "red heart", "heart love сердце любовь",
                listOf("❤️", "🩷", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎")),
            EmojiEntry("🔥", "fire", "hot flame огонь пламя"),
            EmojiEntry("✨", "sparkles", "stars magic"),
            EmojiEntry("💯", "hundred points", "perfect"),
            EmojiEntry("✅", "check mark button", "yes done correct")
        ),
        EmojiCategory.PEOPLE to listOf(
            EmojiEntry("👋", "waving hand", "hello hi wave", withSkinTones("👋")),
            EmojiEntry("🤚", "raised back of hand", "hand", withSkinTones("🤚")),
            EmojiEntry("🖐️", "hand with fingers splayed", "hand", withSkinTones("🖐️")),
            EmojiEntry("✋", "raised hand", "stop hand", withSkinTones("✋")),
            EmojiEntry("👏", "clapping hands", "applause", withSkinTones("👏")),
            EmojiEntry("🙌", "raising hands", "celebrate", withSkinTones("🙌")),
            EmojiEntry("🙏", "folded hands", "please thanks pray", withSkinTones("🙏")),
            EmojiEntry("👍", "thumbs up", "like yes good", withSkinTones("👍")),
            EmojiEntry("👎", "thumbs down", "dislike no", withSkinTones("👎")),
            EmojiEntry("💪", "flexed biceps", "strong", withSkinTones("💪")),
            EmojiEntry("👀", "eyes", "look see"),
            EmojiEntry("👶", "baby", "child"),
            EmojiEntry("🧠", "brain", "think"),
            EmojiEntry("💋", "kiss mark", "love"),
            EmojiEntry("💃", "woman dancing", "dance", withSkinTones("💃")),
            EmojiEntry("🕺", "man dancing", "dance", withSkinTones("🕺"))
        ),
        EmojiCategory.ANIMALS to listOf(
            EmojiEntry("🐶", "dog face", "dog puppy собака"), EmojiEntry("🐱", "cat face", "cat kitten кот"),
            EmojiEntry("🐭", "mouse face", "mouse"), EmojiEntry("🐹", "hamster", "pet"),
            EmojiEntry("🐰", "rabbit face", "bunny"), EmojiEntry("🦊", "fox", "animal"),
            EmojiEntry("🐻", "bear", "animal"), EmojiEntry("🐼", "panda", "animal"),
            EmojiEntry("🐨", "koala", "animal"), EmojiEntry("🐯", "tiger face", "animal"),
            EmojiEntry("🦁", "lion", "animal"), EmojiEntry("🐮", "cow face", "animal"),
            EmojiEntry("🐷", "pig face", "animal"), EmojiEntry("🐸", "frog", "animal"),
            EmojiEntry("🐵", "monkey face", "animal"), EmojiEntry("🙈", "see no evil monkey", "monkey"),
            EmojiEntry("🐔", "chicken", "bird"), EmojiEntry("🐧", "penguin", "bird"),
            EmojiEntry("🐦", "bird", "animal"), EmojiEntry("🦄", "unicorn", "animal"),
            EmojiEntry("🐝", "honeybee", "bee insect"), EmojiEntry("🦋", "butterfly", "insect"),
            EmojiEntry("🐢", "turtle", "animal"), EmojiEntry("🐍", "snake", "animal"),
            EmojiEntry("🌸", "cherry blossom", "flower spring"), EmojiEntry("🌹", "rose", "flower"),
            EmojiEntry("🌻", "sunflower", "flower"), EmojiEntry("🌲", "evergreen tree", "nature"),
            EmojiEntry("🌈", "rainbow", "nature"), EmojiEntry("☀️", "sun", "weather"),
            EmojiEntry("🌙", "crescent moon", "night")
        ),
        EmojiCategory.FOOD to listOf(
            EmojiEntry("🍎", "red apple", "fruit"), EmojiEntry("🍐", "pear", "fruit"),
            EmojiEntry("🍊", "tangerine", "orange fruit"), EmojiEntry("🍋", "lemon", "fruit"),
            EmojiEntry("🍌", "banana", "fruit"), EmojiEntry("🍉", "watermelon", "fruit"),
            EmojiEntry("🍇", "grapes", "fruit"), EmojiEntry("🍓", "strawberry", "fruit"),
            EmojiEntry("🍒", "cherries", "fruit"), EmojiEntry("🥑", "avocado", "food"),
            EmojiEntry("🍕", "pizza", "food"), EmojiEntry("🍔", "hamburger", "burger food"),
            EmojiEntry("🍟", "french fries", "food"), EmojiEntry("🌭", "hot dog", "food"),
            EmojiEntry("🌮", "taco", "food"), EmojiEntry("🍿", "popcorn", "food"),
            EmojiEntry("🍩", "doughnut", "food sweet"), EmojiEntry("🍪", "cookie", "food sweet"),
            EmojiEntry("🎂", "birthday cake", "cake party"), EmojiEntry("☕", "hot beverage", "coffee tea"),
            EmojiEntry("🍺", "beer mug", "drink"), EmojiEntry("🍷", "wine glass", "drink")
        ),
        EmojiCategory.TRAVEL to listOf(
            EmojiEntry("🚗", "automobile", "car"), EmojiEntry("🚕", "taxi", "car"),
            EmojiEntry("🚌", "bus", "travel"), EmojiEntry("🚓", "police car", "car"),
            EmojiEntry("🚑", "ambulance", "car"), EmojiEntry("🚒", "fire engine", "fire truck"),
            EmojiEntry("✈️", "airplane", "travel flight"), EmojiEntry("🚀", "rocket", "space"),
            EmojiEntry("🚲", "bicycle", "bike"), EmojiEntry("⛵", "sailboat", "boat"),
            EmojiEntry("🏠", "house", "home"), EmojiEntry("🏢", "office building", "work"),
            EmojiEntry("🏥", "hospital", "doctor"), EmojiEntry("🏫", "school", "education"),
            EmojiEntry("⛪", "church", "building"), EmojiEntry("🗽", "Statue of Liberty", "travel"),
            EmojiEntry("🗺️", "world map", "map travel"), EmojiEntry("🌍", "globe showing Europe Africa", "world earth")
        ),
        EmojiCategory.ACTIVITIES to listOf(
            EmojiEntry("⚽", "soccer ball", "sport football"), EmojiEntry("🏀", "basketball", "sport"),
            EmojiEntry("🏈", "american football", "sport"), EmojiEntry("⚾", "baseball", "sport"),
            EmojiEntry("🎾", "tennis", "sport"), EmojiEntry("🏆", "trophy", "win prize"),
            EmojiEntry("🎉", "party popper", "party celebrate"), EmojiEntry("🎊", "confetti ball", "party"),
            EmojiEntry("🎈", "balloon", "party"), EmojiEntry("🎁", "wrapped gift", "present"),
            EmojiEntry("🎵", "musical note", "music"), EmojiEntry("🎶", "musical notes", "music"),
            EmojiEntry("🎸", "guitar", "music"), EmojiEntry("🎮", "video game", "game"),
            EmojiEntry("🎯", "bullseye", "target game")
        ),
        EmojiCategory.OBJECTS to listOf(
            EmojiEntry("💡", "light bulb", "idea"), EmojiEntry("📱", "mobile phone", "phone"),
            EmojiEntry("💻", "laptop", "computer"), EmojiEntry("⌨️", "keyboard", "computer"),
            EmojiEntry("📷", "camera", "photo"), EmojiEntry("🔑", "key", "lock"),
            EmojiEntry("🔒", "locked", "security"), EmojiEntry("🔓", "unlocked", "security"),
            EmojiEntry("📌", "pushpin", "pin"), EmojiEntry("📎", "paperclip", "office"),
            EmojiEntry("✏️", "pencil", "write"), EmojiEntry("📝", "memo", "write note"),
            EmojiEntry("📚", "books", "read"), EmojiEntry("⏰", "alarm clock", "time"),
            EmojiEntry("🎁", "wrapped gift", "present"), EmojiEntry("💰", "money bag", "money"),
            EmojiEntry("🔔", "bell", "alert"), EmojiEntry("❤️‍🔥", "heart on fire", "heart love fire")
        ),
        EmojiCategory.SYMBOLS to listOf(
            EmojiEntry("❤️", "red heart", "heart love"), EmojiEntry("🩷", "pink heart", "heart love"),
            EmojiEntry("💔", "broken heart", "heart sad"), EmojiEntry("❣️", "heart exclamation", "heart"),
            EmojiEntry("💖", "sparkling heart", "heart love"), EmojiEntry("💢", "anger symbol", "angry"),
            EmojiEntry("❗", "red exclamation mark", "important"), EmojiEntry("❓", "red question mark", "question"),
            EmojiEntry("‼️", "double exclamation mark", "important"), EmojiEntry("⁉️", "exclamation question mark", "question"),
            EmojiEntry("⭕", "hollow red circle", "circle"), EmojiEntry("❌", "cross mark", "no wrong"),
            EmojiEntry("➕", "plus", "add"), EmojiEntry("➖", "minus", "subtract"),
            EmojiEntry("♻️", "recycling symbol", "recycle"), EmojiEntry("⚠️", "warning", "alert"),
            EmojiEntry("🔴", "red circle", "red"), EmojiEntry("🔵", "blue circle", "blue"),
            EmojiEntry("⭐", "star", "favorite"), EmojiEntry("🌟", "glowing star", "star")
        ),
        EmojiCategory.FLAGS to listOf(
            EmojiEntry("🏳️", "white flag", "flag"), EmojiEntry("🏴", "black flag", "flag"),
            EmojiEntry("🏁", "chequered flag", "race"), EmojiEntry("🚩", "triangular flag", "flag"),
            EmojiEntry("🇺🇸", "flag United States", "usa america"), EmojiEntry("🇬🇧", "flag United Kingdom", "uk britain"),
            EmojiEntry("🇨🇦", "flag Canada", "canada"), EmojiEntry("🇦🇺", "flag Australia", "australia"),
            EmojiEntry("🇩🇪", "flag Germany", "germany"), EmojiEntry("🇫🇷", "flag France", "france"),
            EmojiEntry("🇮🇹", "flag Italy", "italy"), EmojiEntry("🇪🇸", "flag Spain", "spain"),
            EmojiEntry("🇷🇺", "flag Russia", "russia"), EmojiEntry("🇺🇦", "flag Ukraine", "ukraine"),
            EmojiEntry("🇯🇵", "flag Japan", "japan"), EmojiEntry("🇨🇳", "flag China", "china")
        )
    )

    fun entries(category: EmojiCategory, context: android.content.Context): List<EmojiEntry> {
        if (category == EmojiCategory.RECENT) {
            val byEmoji = allEntries().associateBy { it.emoji }
            return EmojiHistory.recent(context).mapNotNull { byEmoji[it] }
        }
        return data[category].orEmpty()
    }

    fun search(query: String): List<EmojiEntry> {
        val wanted = query.trim().lowercase(Locale.ROOT)
        if (wanted.isEmpty()) return emptyList()
        return allEntries().filter {
            it.name.lowercase(Locale.ROOT).contains(wanted) ||
                it.keywords.lowercase(Locale.ROOT).split(' ').any { word -> word.startsWith(wanted) }
        }.distinctBy { it.emoji }
    }

    fun allEntries(): List<EmojiEntry> = data.values.flatten()
}
