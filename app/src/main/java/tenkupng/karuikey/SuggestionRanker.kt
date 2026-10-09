/*
 * Ranking and auto-correction rules adapted from HeliBoard's Suggest.kt and AutoCorrectionUtils
 * (Copyright (C) 2008 The Android Open Source Project, modified; Apache-2.0 AND GPL-3.0-only).
 */
package tenkupng.karuikey

import java.util.Locale
import kotlin.math.pow

/** Turns raw decoder candidates into the strip order: best first, auto-correction marked. */
internal object SuggestionRanker {
    data class ScoredWord(
        val word: String,
        val score: Int,
        val whitelisted: Boolean = false,
        val appropriateForAutoCorrection: Boolean = true
    )

    data class Ranked(val words: List<String>, val autoCorrection: String?)

    // HeliBoard's default auto-correct confidence (0.24) mapped through its threshold formula.
    private const val DEFAULT_CONFIDENCE = 0.24
    val DEFAULT_THRESHOLD = (0.5 - 0.5 * DEFAULT_CONFIDENCE.pow(0.33)).toFloat()
    private const val SUGGEST_INTERFACE_OUTPUT_SCALE = 1_000_000f
    private const val SUPPRESS_SUGGEST_THRESHOLD = -2_000_000_000

    private enum class CapsMode { NONE, FIRST, ALL }

    fun rank(
        typed: String,
        candidates: List<ScoredWord>,
        typedWordValid: Boolean,
        locale: Locale,
        maxResults: Int = 3,
        threshold: Float = DEFAULT_THRESHOLD
    ): Ranked {
        val capsMode = capsMode(typed)
        val seen = HashSet<String>()
        val ordered = ArrayList<ScoredWord>(candidates.size)
        for (candidate in candidates.sortedByDescending { it.score }) {
            if (candidate.score < SUPPRESS_SUGGEST_THRESHOLD) continue
            val word = capitalize(candidate.word, capsMode, locale)
            if (seen.add(word.lowercase(locale))) ordered.add(candidate.copy(word = word))
        }
        val typedKey = typed.lowercase(locale)
        // The typed word itself is never offered as a separate slot unless it is being overridden.
        ordered.removeAll { it.word.lowercase(locale) == typedKey }

        val first = ordered.firstOrNull()
        val autoCorrection = first?.takeIf {
            shouldAutoCorrect(typed, it, typedWordValid, threshold)
        }?.word
        val words = ArrayList<String>(maxResults)
        if (autoCorrection != null) {
            // Keep the literal input one tap away so the correction can be refused.
            words.add(autoCorrection)
            words.add(typed)
            ordered.drop(1).forEach { if (words.size < maxResults) words.add(it.word) }
        } else {
            ordered.forEach { if (words.size < maxResults) words.add(it.word) }
        }
        return Ranked(words, autoCorrection)
    }

    private fun shouldAutoCorrect(
        typed: String,
        first: ScoredWord,
        typedWordValid: Boolean,
        threshold: Float
    ): Boolean {
        val considered = typed.trimEnd('\'')
        if (considered.length < 2 || typedWordValid && !first.whitelisted) return false
        if (typed.any(Char::isDigit)) return false
        // Mostly-caps input is deliberate; full caps is still corrected.
        val upper = typed.count(Char::isUpperCase)
        if (upper > 1 && upper != typed.length) return false
        // Do not turn addresses or URLs into something without their separators.
        if ('@' in typed && '@' !in first.word) return false
        if ('.' in typed && '.' !in first.word) return false
        if (' ' in first.word) return false
        if (first.whitelisted) return true
        if (!first.appropriateForAutoCorrection) return false
        return normalizedScore(considered, first.word, first.score) >= threshold
    }

    /** AOSP AutocorrectionThresholdUtils.calcNormalizedScore for the suggest interface path. */
    fun normalizedScore(before: String, after: String, score: Int): Float {
        if (before.isEmpty() || after.isEmpty() || after.isBlank()) return 0f
        val distance = editDistance(before.lowercase(Locale.ROOT), after.lowercase(Locale.ROOT))
        if (score <= 0 || distance >= after.length) return 0f
        val weight = 1f - distance.toFloat() / after.length
        return score / SUGGEST_INTERFACE_OUTPUT_SCALE * weight
    }

    /** Damerau-Levenshtein distance with adjacent transpositions, as in the AOSP decoder. */
    fun editDistance(a: String, b: String): Int {
        val rows = a.length + 1
        val columns = b.length + 1
        val d = Array(rows) { IntArray(columns) }
        for (i in 0 until rows) d[i][0] = i
        for (j in 0 until columns) d[0][j] = j
        for (i in 1 until rows) {
            for (j in 1 until columns) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + cost)
                }
            }
        }
        return d[a.length][b.length]
    }

    private fun capsMode(typed: String): CapsMode = when {
        typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } &&
            typed.any(Char::isLetter) -> CapsMode.ALL
        typed.firstOrNull()?.isUpperCase() == true -> CapsMode.FIRST
        else -> CapsMode.NONE
    }

    private fun capitalize(word: String, mode: CapsMode, locale: Locale): String = when (mode) {
        CapsMode.NONE -> word
        CapsMode.ALL -> word.uppercase(locale)
        CapsMode.FIRST -> word.replaceFirstChar { it.titlecase(locale) }
    }
}
