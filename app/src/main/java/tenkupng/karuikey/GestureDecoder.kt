package tenkupng.karuikey

import com.android.inputmethod.keyboard.Key
import com.android.inputmethod.keyboard.KeyDetector
import com.android.inputmethod.keyboard.Keyboard
import com.android.inputmethod.latin.common.InputPointers

/** Converts an AOSP sampled gesture path into a compact key sequence. */
object GestureDecoder {
    fun decode(keyboard: Keyboard?, points: InputPointers): String {
        if (keyboard == null || points.pointerSize == 0) return ""
        val detector = KeyDetector(0f, 0f).also { it.setKeyboard(keyboard, 0f, 0f) }
        val xs = points.xCoordinates
        val ys = points.yCoordinates
        val result = StringBuilder()
        var previousCode = 0
        for (index in 0 until points.pointerSize) {
            val key = detector.detectHitKey(xs[index], ys[index]) ?: nearestLetterKey(keyboard, xs[index], ys[index])
            val code = key?.code ?: 0
            if (code <= 0 || !Character.isLetter(code) || code == previousCode) continue
            result.appendCodePoint(Character.toLowerCase(code))
            previousCode = code
        }
        return result.toString()
    }

    /** Finds the best candidate whose key-center path is visited in order by [points]. */
    fun findBestCandidate(
        keyboard: Keyboard?,
        points: InputPointers,
        candidates: Array<String>,
        contextCandidates: Array<String>? = null
    ): String? {
        if (keyboard == null || points.pointerSize == 0) return null
        val decodedLength = decode(keyboard, points).length
        val pathLength = pathLength(points)
        var bestCandidate: String? = null
        var bestScore = Float.MAX_VALUE
        for ((index, candidate) in candidates.withIndex()) {
            val geometryScore = scoreCandidate(
                keyboard, points, candidate, decodedLength, pathLength
            )
            if (geometryScore < 0f) continue
            var score = geometryScore
            val contextIndex = contextCandidates?.indexOfFirst {
                it.equals(candidate, ignoreCase = true)
            } ?: -1
            if (contextIndex >= 0) {
                // Context is a small reranking signal; it cannot rescue a geometrically invalid
                // word and remains weaker than the path/coverage score.
                score -= ((contextCandidates?.size ?: 0) - contextIndex) * 0.35f
            }
            score += index * 0.01f
            if (score < bestScore) {
                bestCandidate = candidate
                bestScore = score
            }
        }
        return bestCandidate
    }

    private fun scoreCandidate(
        keyboard: Keyboard,
        points: InputPointers,
        word: String,
        decodedLength: Int,
        pathLength: Float
    ): Float {
        if (word.isEmpty()) return -1f
        var pointIndex = 0
        var score = 0f
        var characterIndex = 0
        val firstPointLimit = (points.pointerSize / 6).coerceAtLeast(1)
        for (character in word) {
            val key = keyboard.getKey(character.code)
                ?: keyboard.getKey(character.uppercaseChar().code)
                ?: return -1f
            val centerX = key.x + key.width / 2
            val centerY = key.y + key.height / 2
            val radius = (maxOf(key.width, key.height) * 0.7f).coerceAtLeast(18f)
            val radiusSquared = radius * radius
            var found = false
            val pointLimit = if (characterIndex == 0) firstPointLimit else points.pointerSize
            while (pointIndex < pointLimit) {
                val dx = points.xCoordinates[pointIndex] - centerX
                val dy = points.yCoordinates[pointIndex] - centerY
                val distanceSquared = (dx * dx + dy * dy).toFloat()
                if (distanceSquared <= radiusSquared) {
                    score += distanceSquared / radiusSquared
                    found = true
                    break
                }
                pointIndex++
            }
            if (!found) return -1f
            characterIndex++
        }
        // A short prefix can match the beginning of a long swipe. Penalize that explicitly so
        // coverage and candidate length matter as much as local key-center distance.
        val remainingPoints = points.pointerSize - pointIndex - 1
        score += remainingPoints.coerceAtLeast(0) * 1.5f / points.pointerSize
        score += kotlin.math.abs(word.length - decodedLength) * 1.25f
        if (pathLength > 0f) {
            val lastKey = keyboard.getKey(word.last().code)
                ?: keyboard.getKey(word.last().uppercaseChar().code)
            if (lastKey != null) {
                val endX = lastKey.x + lastKey.width / 2
                val endY = lastKey.y + lastKey.height / 2
                val lastPoint = points.pointerSize - 1
                val dx = points.xCoordinates[lastPoint] - endX
                val dy = points.yCoordinates[lastPoint] - endY
                score += ((dx * dx + dy * dy).toFloat() / pathLength).coerceAtMost(1.5f)
            }
        }
        return score
    }

    private fun pathLength(points: InputPointers): Float {
        var length = 0f
        for (index in 1 until points.pointerSize) {
            val dx = points.xCoordinates[index] - points.xCoordinates[index - 1]
            val dy = points.yCoordinates[index] - points.yCoordinates[index - 1]
            length += kotlin.math.sqrt((dx * dx + dy * dy).toFloat())
        }
        return length
    }

    private fun nearestLetterKey(keyboard: Keyboard, x: Int, y: Int): Key? {
        var best: Key? = null
        var bestDistance = Int.MAX_VALUE
        for (key in keyboard.sortedKeys) {
            val code = key.code
            if (key.isSpacer || !Character.isLetter(code)) continue
            val dx = x - (key.x + key.width / 2)
            val dy = y - (key.y + key.height / 2)
            val distance = dx * dx + dy * dy
            if (distance < bestDistance) {
                bestDistance = distance
                best = key
            }
        }
        return best
    }
}
