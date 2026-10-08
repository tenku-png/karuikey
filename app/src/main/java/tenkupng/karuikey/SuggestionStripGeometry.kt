package tenkupng.karuikey

/**
 * Fixed-width geometry for the IME toolbar. Candidates and utility buttons each take the
 * full strip width and replace each other, so typing never reflows either region.
 */
internal object SuggestionStripGeometry {
    const val CANDIDATE_COUNT = 3
    const val UTILITY_COUNT = 4

    fun candidateSlotWidth(regionWidth: Int, index: Int): Int {
        require(index in 0 until CANDIDATE_COUNT)
        val base = regionWidth.coerceAtLeast(0) / CANDIDATE_COUNT
        return if (index == CANDIDATE_COUNT - 1) {
            regionWidth.coerceAtLeast(0) - base * (CANDIDATE_COUNT - 1)
        } else {
            base
        }
    }
}
