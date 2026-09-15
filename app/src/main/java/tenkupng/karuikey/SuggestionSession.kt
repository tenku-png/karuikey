package tenkupng.karuikey

/** Transient suggestion state owned by one active editor session. */
internal class SuggestionSession {
    val prefix = StringBuilder(20)
    var previousWord: String? = null
        private set

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
        previousWord = prefix.toString()
        prefix.setLength(0)
    }

    fun completeWord(word: String) {
        if (word.isBlank()) return
        previousWord = word
        prefix.setLength(0)
    }

    fun clearCurrentWord() {
        prefix.setLength(0)
    }

    fun clear() {
        prefix.setLength(0)
        previousWord = null
    }
}
