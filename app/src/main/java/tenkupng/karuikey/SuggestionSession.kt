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
    /** True when the next word starts a sentence, so the decoder can favour sentence openers. */
    var atSentenceStart = false
        private set
    // Keyboard-relative touch point of each prefix code point; -1 means use the key center.
    private var xCoordinates = IntArray(INITIAL_COORDINATES)
    private var yCoordinates = IntArray(INITIAL_COORDINATES)
    var coordinateCount = 0
        private set

    fun xCoordinates(): IntArray = xCoordinates.copyOf(coordinateCount)

    fun yCoordinates(): IntArray = yCoordinates.copyOf(coordinateCount)

    fun recentWord(index: Int): String? =
        if (index in 0 until recentWordCount) recentWords[index] else null

    fun append(text: CharSequence, x: Int = NOT_A_COORDINATE, y: Int = NOT_A_COORDINATE) {
        prefix.append(text)
        val codePoints = Character.codePointCount(text, 0, text.length)
        for (index in 0 until codePoints) {
            // Only a single typed code point carries its own touch point.
            addCoordinate(if (codePoints == 1) x else NOT_A_COORDINATE,
                if (codePoints == 1) y else NOT_A_COORDINATE)
        }
    }

    private fun addCoordinate(x: Int, y: Int) {
        if (coordinateCount == xCoordinates.size) {
            xCoordinates = xCoordinates.copyOf(coordinateCount * 2)
            yCoordinates = yCoordinates.copyOf(coordinateCount * 2)
        }
        xCoordinates[coordinateCount] = x
        yCoordinates[coordinateCount] = y
        coordinateCount++
    }

    fun deleteLastCodePoint(): Int {
        if (prefix.isEmpty()) return 0
        val codePoint = Character.codePointBefore(prefix, prefix.length)
        val charCount = Character.charCount(codePoint)
        prefix.setLength(prefix.length - charCount)
        if (coordinateCount > 0) coordinateCount--
        return charCount
    }

    fun completeCurrentWord() {
        if (prefix.isEmpty()) return
        rememberCompletedWord(prefix.toString())
        clearPrefix()
    }

    fun completeWord(word: String) {
        if (word.isBlank()) return
        rememberCompletedWord(word)
        clearPrefix()
    }

    fun clearCurrentWord() {
        clearPrefix()
    }

    fun clear() {
        clearPrefix()
        for (index in recentWords.indices) recentWords[index] = null
        recentWordCount = 0
        automaticSpace = false
        atSentenceStart = false
    }

    /** Sentence punctuation or a new line: earlier words no longer predict the next one. */
    fun markSentenceEnd() {
        clear()
        atSentenceStart = true
    }

    /** Resumes a word already in the editor, e.g. after Backspace reached its last letter. */
    fun restore(word: String, previousWords: List<String>, sentenceStart: Boolean = false) {
        clearPrefix()
        append(word)
        for (index in recentWords.indices) recentWords[index] = previousWords.getOrNull(index)
        recentWordCount = previousWords.size.coerceAtMost(recentWords.size)
        automaticSpace = false
        atSentenceStart = sentenceStart && recentWordCount == 0
    }

    private fun clearPrefix() {
        prefix.setLength(0)
        coordinateCount = 0
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
        atSentenceStart = false
    }

    /** The word ending at the cursor and up to three words before it, nearest first. */
    data class TextContext(
        val word: String,
        val previousWords: List<String>,
        val sentenceStart: Boolean = false
    )

    companion object {
        private const val MAX_CONTEXT_WORDS = 3
        private const val INITIAL_COORDINATES = 32
        const val NOT_A_COORDINATE = -1

        private fun isSentenceBoundary(text: CharSequence, end: Int): Boolean {
            var index = end
            while (index > 0 && text[index - 1] == ' ') index--
            if (index == 0) return true
            return when (text[index - 1]) {
                '.', '!', '?', '\n' -> true
                else -> false
            }
        }

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
            return TextContext(word, previous,
                previous.isEmpty() && isSentenceBoundary(text, start))
        }
    }
}
