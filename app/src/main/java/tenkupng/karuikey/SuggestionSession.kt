package tenkupng.karuikey

/** Transient suggestion state owned by one active editor session. */
internal class SuggestionSession {
    private val recentWords = arrayOfNulls<String>(3)
    val prefix = StringBuilder(20)
    private var recentWordCount = 0
    val previousWord: String?
        get() = recentWords[0]
    val hasAutomaticSpace: Boolean
        get() = automaticSpace
    private var automaticSpace = false

    fun recentWord(index: Int): String? =
        if (index in 0 until recentWordCount) recentWords[index] else null

    fun append(text: CharSequence) {
        prefix.append(text)
    }

    fun deleteLastCodePoint(): Int {
        if (prefix.isEmpty()) return 0
        val codePoint = Character.codePointBefore(prefix, prefix.length)
        val charCount = Character.charCount(codePoint)
        prefix.setLength(prefix.length - charCount)
        return charCount
    }

    fun completeCurrentWord() {
        if (prefix.isEmpty()) return
        rememberCompletedWord(prefix.toString())
        prefix.setLength(0)
    }

    fun completeWord(word: String) {
        if (word.isBlank()) return
        rememberCompletedWord(word)
        prefix.setLength(0)
    }

    fun clearCurrentWord() {
        prefix.setLength(0)
    }

    fun clear() {
        prefix.setLength(0)
        for (index in recentWords.indices) recentWords[index] = null
        recentWordCount = 0
        automaticSpace = false
    }

    /** Resumes a word already in the editor, e.g. after Backspace reached its last letter. */
    fun restore(word: String, previousWords: List<String>) {
        prefix.setLength(0)
        prefix.append(word)
        for (index in recentWords.indices) recentWords[index] = previousWords.getOrNull(index)
        recentWordCount = previousWords.size.coerceAtMost(recentWords.size)
        automaticSpace = false
    }

    fun markAutomaticSpace() {
        automaticSpace = true
    }

    fun clearAutomaticSpace() {
        automaticSpace = false
    }

    private fun rememberCompletedWord(word: String) {
        recentWords[2] = recentWords[1]
        recentWords[1] = recentWords[0]
        recentWords[0] = word
        if (recentWordCount < recentWords.size) recentWordCount++
    }

    /** The word ending at the cursor and up to three words before it, nearest first. */
    data class TextContext(val word: String, val previousWords: List<String>)

    companion object {
        private const val MAX_CONTEXT_WORDS = 3

        private fun isWordChar(char: Char) =
            char.isLetterOrDigit() || char == '\'' || char == '\u2019'

        fun parseBeforeCursor(text: CharSequence): TextContext {
            var end = text.length
            var start = end
            while (start > 0 && isWordChar(text[start - 1])) start--
            val word = text.substring(start, end).trimStart('\'', '\u2019')
            val previous = ArrayList<String>(MAX_CONTEXT_WORDS)
            end = start
            while (previous.size < MAX_CONTEXT_WORDS) {
                // Context only spans plain spaces; punctuation or a line break starts afresh.
                var gapStart = end
                while (gapStart > 0 && text[gapStart - 1] == ' ') gapStart--
                if (gapStart == end || gapStart == 0) break
                var wordStart = gapStart
                while (wordStart > 0 && isWordChar(text[wordStart - 1])) wordStart--
                if (wordStart == gapStart) break
                previous.add(text.substring(wordStart, gapStart))
                end = wordStart
            }
            return TextContext(word, previous)
        }
    }
}
