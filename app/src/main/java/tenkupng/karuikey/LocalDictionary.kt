package tenkupng.karuikey

import java.nio.charset.StandardCharsets

/**
 * Read-only compact dictionary index. Words are sorted for prefix lookup; records retain the
 * source frequency and up to three next-word ids. The byte array is released when the session ends.
 */
internal class LocalDictionary(private val data: ByteArray) {
    private val topCount: Int
    val wordCount: Int
    private val recordsOffset: Int
    private val offsetsOffset: Int

    init {
        require(data.size >= 16 && data[0] == 'K'.code.toByte() &&
            data[1] == 'R'.code.toByte() && data[2] == 'D'.code.toByte() &&
            data[3] == '1'.code.toByte() && data[4].toInt() == 1)
        topCount = readU16(6)
        wordCount = readInt(8)
        recordsOffset = readInt(12)
        offsetsOffset = 16 + topCount * 4
        require(wordCount > 0 && topCount > 0)
        require(offsetsOffset >= 16 &&
            offsetsOffset + (wordCount + 1) * 4 <= data.size)
        require(recordsOffset >= offsetsOffset + (wordCount + 1) * 4)
        require(recordsOffset <= data.size)
    }

    fun fill(
        previousWord: String?,
        secondPreviousWord: String?,
        thirdPreviousWord: String?,
        prefix: CharSequence,
        out: MutableList<String>
    ) {
        out.clear()
        if (prefix.isEmpty()) {
            var contextCount = appendContext(previousWord, prefix, out)
            if (contextCount == 0) contextCount = appendContext(secondPreviousWord, prefix, out)
            if (contextCount == 0) appendContext(thirdPreviousWord, prefix, out)
            if (out.size < 3) appendTopWords(out)
            return
        }

        var contextCount = appendContext(previousWord, prefix, out)
        if (contextCount == 0) contextCount = appendContext(secondPreviousWord, prefix, out)
        if (contextCount == 0) appendContext(thirdPreviousWord, prefix, out)
        if (out.size < 3) appendPrefixMatches(prefix, out)
    }

    fun findGestureCandidate(sequence: CharSequence): String? {
        if (sequence.isEmpty()) return null
        findWordId(sequence).takeIf { it >= 0 }?.let { return wordAt(it) }

        // The existing decoder deliberately accepts one repeated-letter omission. Recreate those
        // candidates and use the indexed exact lookup instead of scanning the whole dictionary.
        var index = 0
        while (index < sequence.length) {
            val codePoint = Character.codePointAt(sequence, index)
            val nextIndex = index + Character.charCount(codePoint)
            val candidate = StringBuilder(sequence.length + Character.charCount(codePoint))
                .append(sequence, 0, nextIndex)
                .appendCodePoint(codePoint)
                .append(sequence, nextIndex, sequence.length)
            findWordId(candidate).takeIf { it >= 0 }?.let { return wordAt(it) }
            index = nextIndex
        }
        return null
    }

    private fun appendContext(
        previousWord: String?,
        prefix: CharSequence,
        out: MutableList<String>
    ): Int {
        if (previousWord.isNullOrBlank()) return 0
        val id = findWordId(previousWord)
        if (id < 0) return 0
        val record = recordStart(id)
        val wordLength = readU16(record)
        val nextCount = data[record + 3].toInt() and 0xff
        var nextOffset = record + 4 + wordLength
        val initialSize = out.size
        for (index in 0 until nextCount) {
            val nextId = readInt(nextOffset)
            nextOffset += 4
            if (nextId < 0 || nextId >= wordCount || !matchesPrefix(nextId, prefix)) continue
            if (!containsWord(out, nextId)) out.add(wordAt(nextId))
            if (out.size == 3) break
        }
        return out.size - initialSize
    }

    private fun appendTopWords(out: MutableList<String>) {
        for (index in 0 until topCount) {
            if (out.size == 3) return
            val id = readInt(16 + index * 4)
            if (id >= 0 && id < wordCount && !containsWord(out, id)) out.add(wordAt(id))
        }
    }

    private fun appendPrefixMatches(prefix: CharSequence, out: MutableList<String>) {
        var id = lowerBound(prefix)
        var best0 = -1
        var best1 = -1
        var best2 = -1
        var frequency0 = -1
        var frequency1 = -1
        var frequency2 = -1
        while (id < wordCount && matchesPrefix(id, prefix)) {
            val frequency = frequency(id)
            if (frequency > frequency0) {
                best2 = best1
                frequency2 = frequency1
                best1 = best0
                frequency1 = frequency0
                best0 = id
                frequency0 = frequency
            } else if (frequency > frequency1) {
                best2 = best1
                frequency2 = frequency1
                best1 = id
                frequency1 = frequency
            } else if (frequency > frequency2) {
                best2 = id
                frequency2 = frequency
            }
            id++
        }
        if (best0 >= 0 && !containsWord(out, best0)) out.add(wordAt(best0))
        if (best1 >= 0 && out.size < 3 && !containsWord(out, best1)) out.add(wordAt(best1))
        if (best2 >= 0 && out.size < 3 && !containsWord(out, best2)) out.add(wordAt(best2))
    }

    private fun lowerBound(target: CharSequence): Int {
        var low = 0
        var high = wordCount
        while (low < high) {
            val middle = (low + high) ushr 1
            if (compareWord(middle, target) < 0) low = middle + 1 else high = middle
        }
        return low
    }

    private fun findWordId(target: CharSequence): Int {
        var low = 0
        var high = wordCount - 1
        while (low <= high) {
            val middle = (low + high) ushr 1
            when {
                compareWord(middle, target) < 0 -> low = middle + 1
                compareWord(middle, target) > 0 -> high = middle - 1
                else -> return middle
            }
        }
        return -1
    }

    private fun compareWord(id: Int, target: CharSequence): Int {
        val start = wordStart(id)
        val end = start + readU16(recordStart(id))
        var position = start
        var targetIndex = 0
        while (position < end && targetIndex < target.length) {
            val firstByte = data[position].toInt() and 0xff
            val wordCodePoint = Character.toLowerCase(readCodePoint(position))
            position += codePointByteCount(firstByte)
            val targetCodePoint = Character.toLowerCase(
                Character.codePointAt(target, targetIndex)
            )
            targetIndex += Character.charCount(targetCodePoint)
            if (wordCodePoint != targetCodePoint) return wordCodePoint - targetCodePoint
        }
        return when {
            position < end -> 1
            targetIndex < target.length -> -1
            else -> 0
        }
    }

    private fun matchesPrefix(id: Int, prefix: CharSequence): Boolean {
        if (prefix.isEmpty()) return true
        val record = recordStart(id)
        val start = record + 4
        val end = start + readU16(record)
        var position = start
        var prefixIndex = 0
        while (position < end && prefixIndex < prefix.length) {
            val firstByte = data[position].toInt() and 0xff
            val word = readCodePoint(position)
            position += codePointByteCount(firstByte)
            val wanted = Character.codePointAt(prefix, prefixIndex)
            prefixIndex += Character.charCount(wanted)
            if (Character.toLowerCase(word) != Character.toLowerCase(wanted)) return false
        }
        return prefixIndex == prefix.length
    }

    private fun containsWord(out: List<String>, id: Int): Boolean {
        for (word in out) {
            if (compareWord(id, word) == 0) return true
        }
        return false
    }

    private fun wordAt(id: Int): String {
        val record = recordStart(id)
        return String(
            data,
            record + 4,
            readU16(record),
            StandardCharsets.UTF_8
        )
    }

    private fun wordStart(id: Int): Int = recordStart(id) + 4

    private fun recordStart(id: Int): Int = recordsOffset + readInt(offsetsOffset + id * 4)

    private fun frequency(id: Int): Int = data[recordStart(id) + 2].toInt() and 0xff

    private fun readCodePoint(position: Int): Int {
        val first = data[position].toInt() and 0xff
        return when {
            first < 0x80 -> first
            first < 0xe0 -> ((first and 0x1f) shl 6) or
                (data[position + 1].toInt() and 0x3f)
            first < 0xf0 -> ((first and 0x0f) shl 12) or
                ((data[position + 1].toInt() and 0x3f) shl 6) or
                (data[position + 2].toInt() and 0x3f)
            else -> ((first and 0x07) shl 18) or
                ((data[position + 1].toInt() and 0x3f) shl 12) or
                ((data[position + 2].toInt() and 0x3f) shl 6) or
                (data[position + 3].toInt() and 0x3f)
        }
    }

    private fun codePointByteCount(firstByte: Int): Int = when {
        firstByte < 0x80 -> 1
        firstByte < 0xe0 -> 2
        firstByte < 0xf0 -> 3
        else -> 4
    }

    private fun readU16(position: Int): Int =
        (data[position].toInt() and 0xff) or
            ((data[position + 1].toInt() and 0xff) shl 8)

    private fun readInt(position: Int): Int =
        (data[position].toInt() and 0xff) or
            ((data[position + 1].toInt() and 0xff) shl 8) or
            ((data[position + 2].toInt() and 0xff) shl 16) or
            ((data[position + 3].toInt() and 0xff) shl 24)
}
