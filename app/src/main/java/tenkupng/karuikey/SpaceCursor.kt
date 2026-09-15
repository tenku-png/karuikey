package tenkupng.karuikey

internal object SpaceCursor {
    fun triggerReached(distance: Int, threshold: Int): Boolean =
        kotlin.math.abs(distance) >= threshold

    fun stepCount(distance: Int, step: Int): Int {
        if (step <= 0) return 0
        return distance / step
    }

    fun remainder(distance: Int, steps: Int, step: Int): Int = distance - steps * step

    fun lastCodePointWidth(text: CharSequence): Int {
        if (text.isEmpty()) return 0
        return Character.charCount(Character.codePointBefore(text, text.length))
    }

    fun firstCodePointWidth(text: CharSequence): Int {
        if (text.isEmpty()) return 0
        return Character.charCount(Character.codePointAt(text, 0))
    }
}
