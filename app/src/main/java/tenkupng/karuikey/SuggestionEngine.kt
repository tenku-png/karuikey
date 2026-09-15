package tenkupng.karuikey

/**
 * Small project-owned starter vocabulary for the first offline suggestion path.
 *
 * A full dictionary is intentionally deferred until its data license and compact format are
 * settled. Keeping this list in code also avoids a database, native library, or startup I/O.
 */
object SuggestionEngine {
    private val ENGLISH = arrayOf(
        "the", "and", "you", "that", "this", "hello", "world", "please", "thanks", "where",
        "what", "with", "from", "for", "not", "are", "have", "can", "will", "good"
    )
    private val RUSSIAN = arrayOf(
        "это", "как", "что", "при", "привет", "пример", "приложение", "просто", "проверка",
        "русский", "работа", "спасибо", "пожалуйста", "можно", "будет", "клавиатура"
    )

    /** Fills [out] with at most three deterministic prefix matches. */
    fun fill(locale: String, prefix: CharSequence, out: MutableList<String>) {
        out.clear()
        if (prefix.isEmpty()) return
        val vocabulary = if (locale.startsWith("ru", ignoreCase = true)) RUSSIAN else ENGLISH
        for (word in vocabulary) {
            if (hasPrefix(word, prefix)) {
                out.add(word)
                if (out.size == 3) return
            }
        }
    }

    private fun hasPrefix(word: String, prefix: CharSequence): Boolean {
        if (prefix.length > word.length) return false
        for (index in prefix.indices) {
            if (!word[index].equals(prefix[index], ignoreCase = true)) return false
        }
        return true
    }
}
