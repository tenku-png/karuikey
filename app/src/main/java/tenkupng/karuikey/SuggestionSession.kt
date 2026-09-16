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
}
