package tenkupng.karuikey

import com.android.inputmethod.keyboard.Keyboard
import com.android.inputmethod.latin.common.InputPointers

/** Small, ranked, project-owned offline lexicons for the first suggestion path. */
object SuggestionEngine {
    private val ENGLISH = arrayOf(
        "the", "that", "this", "there", "their", "then", "they", "these", "them", "themselves",
        "and", "are", "about", "after", "again", "all", "also", "always", "another", "any",
        "around", "because", "been", "before", "being", "between", "both", "but", "can",
        "come", "could", "day", "did", "different", "do", "does", "done", "down", "each",
        "even", "every", "example", "first", "find", "for", "from", "get", "give", "go",
        "good", "great", "has", "have", "hello", "help", "here", "how", "if", "important",
        "into", "is", "its", "just", "know", "last", "like", "little", "look", "make", "many",
        "may", "more", "most", "much", "must", "need", "never", "new", "next", "not",
        "now", "off", "often", "only", "other", "our", "out", "over", "people", "please",
        "right", "same", "say", "see", "should", "since", "small", "some", "something", "still",
        "such", "sure", "take", "than", "thank", "thanks", "that", "their", "them", "thing",
        "think", "this", "those", "through", "time", "today", "together", "too", "try", "under",
        "use", "very", "want", "was", "way", "well", "were", "what", "when", "where", "which",
        "while", "who", "will", "with", "without", "work", "world", "would", "year", "you", "your"
    )
    private val RUSSIAN = arrayOf(
        "это", "как", "что", "при", "привет", "пример", "приложение", "просто", "проверка",
        "приветствие", "привести", "причина", "принять", "приходить", "пришло", "так", "там",
        "тоже", "только", "тогда", "теперь", "тебе", "тебя", "текст", "три", "тут", "ты",
        "будет", "быть", "были", "быстро", "было", "все", "всегда", "вот", "время", "второй",
        "вы", "где", "да", "даже", "два", "для", "до", "дома", "есть", "еще", "если",
        "жизнь", "задача", "здесь", "знать", "значит", "и", "или", "иметь", "их", "как",
        "какой", "когда", "клавиатура", "который", "кроме", "кто", "лучше", "много", "можно",
        "мой", "мы", "мир", "надо", "наша", "наш", "не", "небольшой", "него", "нет", "новый",
        "нужно", "один", "она", "они", "оно", "они", "ответ", "первый", "пожалуйста", "почему",
        "после", "потом", "проверить", "работа", "работать", "раз", "разный", "русский", "сам",
        "сейчас", "сказать", "себя", "система", "снова", "спасибо", "спокойно", "слово", "смотреть",
        "сообщение", "стать", "суть", "такой", "твой", "текущий", "тут", "уже", "хорошо", "хотеть",
        "часть", "человек", "через", "чтобы", "читать", "этот", "я", "дела", "большое",
        "за"
    )

    // Deliberately small, project-owned fixtures until a redistributable n-gram source is chosen.
    // Keeping these separate makes the context signal auditable and easy to replace.
    private val ENGLISH_CONTEXT_PREVIOUS = arrayOf("thank", "how", "what", "good", "see")
    private val ENGLISH_CONTEXT_NEXT = arrayOf(
        arrayOf("you", "the", "very"),
        arrayOf("are", "do", "you"),
        arrayOf("is", "you", "when"),
        arrayOf("morning", "work", "to"),
        arrayOf("you", "the", "that")
    )
    private val RUSSIAN_CONTEXT_PREVIOUS = arrayOf("спасибо", "привет", "как", "добрый")
    private val RUSSIAN_CONTEXT_NEXT = arrayOf(
        arrayOf("тебе", "за", "большое"),
        arrayOf("мир", "как", "тебе"),
        arrayOf("дела", "это", "ты"),
        arrayOf("день", "вечер", "утро")
    )

    /** Fills [out] with at most three ranked prefix matches without allocating during the scan. */
    fun fill(locale: String, prefix: CharSequence, out: MutableList<String>) {
        fill(locale, null, prefix, out)
    }

    /**
     * Fills prefix matches, putting a matching next-word fixture first when [previousWord] is
     * available in the current in-memory session context.
     */
    fun fill(
        locale: String,
        previousWord: String?,
        prefix: CharSequence,
        out: MutableList<String>
    ) {
        out.clear()
        val vocabulary = when {
            locale.startsWith("ru", ignoreCase = true) -> RUSSIAN
            locale.startsWith("en", ignoreCase = true) -> ENGLISH
            else -> return
        }

        val context = contextCandidates(locale, previousWord)
        if (prefix.isEmpty()) {
            if (context == null) return
            for (word in context) {
                if (containsWord(vocabulary, word)) out.add(word)
                if (out.size == 3) return
            }
            return
        }

        if (context != null) {
            for (word in context) {
                if (hasPrefix(word, prefix) && containsWord(vocabulary, word)) {
                    out.add(word)
                    if (out.size == 3) return
                }
            }
        }
        for (word in vocabulary) {
            if (hasPrefix(word, prefix) && !containsWord(out, word)) {
                out.add(word)
                if (out.size == 3) return
            }
        }
    }

    fun hasDictionary(locale: String): Boolean =
        locale.startsWith("en", ignoreCase = true) || locale.startsWith("ru", ignoreCase = true)

    private fun contextCandidates(locale: String, previousWord: String?): Array<String>? {
        if (previousWord.isNullOrBlank()) return null
        val previous = if (locale.startsWith("ru", ignoreCase = true)) {
            RUSSIAN_CONTEXT_PREVIOUS
        } else if (locale.startsWith("en", ignoreCase = true)) {
            ENGLISH_CONTEXT_PREVIOUS
        } else {
            return null
        }
        val next = if (locale.startsWith("ru", ignoreCase = true)) {
            RUSSIAN_CONTEXT_NEXT
        } else {
            ENGLISH_CONTEXT_NEXT
        }
        for (index in previous.indices) {
            if (previous[index].equals(previousWord, ignoreCase = true)) return next[index]
        }
        return null
    }

    /** Returns only an exact key sequence match; uncertain gestures are left uncommitted. */
    fun findGestureCandidate(locale: String, sequence: CharSequence): String? {
        if (sequence.isEmpty()) return null
        val vocabulary = when {
            locale.startsWith("ru", ignoreCase = true) -> RUSSIAN
            locale.startsWith("en", ignoreCase = true) -> ENGLISH
            else -> return null
        }
        for (word in vocabulary) {
            if (word.contentEquals(sequence, ignoreCase = true)) return word
            if (word.length != sequence.length + 1) continue
            for (index in 1 until word.length) {
                if (word[index] != word[index - 1]) continue
                if (word.removeRange(index, index + 1).contentEquals(sequence, true)) {
                    return word
                }
            }
        }
        return null
    }

    fun findGestureCandidate(locale: String, keyboard: Keyboard?, points: InputPointers): String? {
        return findGestureCandidate(locale, keyboard, points, null)
    }

    fun findGestureCandidate(
        locale: String,
        keyboard: Keyboard?,
        points: InputPointers,
        previousWord: String?
    ): String? {
        val vocabulary = when {
            locale.startsWith("ru", ignoreCase = true) -> RUSSIAN
            locale.startsWith("en", ignoreCase = true) -> ENGLISH
            else -> return null
        }
        return GestureDecoder.findBestCandidate(
            keyboard, points, vocabulary, contextCandidates(locale, previousWord)
        )
    }

    private fun hasPrefix(word: String, prefix: CharSequence): Boolean {
        if (prefix.length > word.length) return false
        for (index in prefix.indices) {
            if (!word[index].equals(prefix[index], ignoreCase = true)) return false
        }
        return true
    }

    private fun containsWord(words: Array<String>, candidate: String): Boolean {
        for (word in words) if (word.equals(candidate, ignoreCase = true)) return true
        return false
    }

    private fun containsWord(words: MutableList<String>, candidate: String): Boolean {
        for (word in words) if (word.equals(candidate, ignoreCase = true)) return true
        return false
    }
}
