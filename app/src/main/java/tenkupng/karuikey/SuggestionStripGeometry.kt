package tenkupng.karuikey

/** Fixed-width geometry for the candidate and utility regions of the IME toolbar. */
internal object SuggestionStripGeometry {
    const val CANDIDATE_COUNT = 3
    const val UTILITY_COUNT = 4

    fun utilityWidth(totalWidth: Int, buttonWidth: Int): Int =
        (buttonWidth * UTILITY_COUNT).coerceAtMost(totalWidth.coerceAtLeast(0))

    fun candidateRegionWidth(totalWidth: Int, buttonWidth: Int): Int =
        (totalWidth - utilityWidth(totalWidth, buttonWidth)).coerceAtLeast(0)

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
