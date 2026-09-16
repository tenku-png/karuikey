package tenkupng.karuikey

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDictionaryTest {
    @Test
    fun ranksPrefixContextAndFallbackCandidatesWithoutChangingTheLexicon() {
        val dictionary = dictionary(
            Entry("apple", 100, intArrayOf(4, 3)),
            Entry("apricot", 70, intArrayOf()),
            Entry("banana", 90, intArrayOf()),
            Entry("cake", 50, intArrayOf()),
            Entry("car", 60, intArrayOf())
        )
        val suggestions = mutableListOf<String>()

        dictionary.fill(null, null, null, "ap", suggestions)
        assertEquals(listOf("apple", "apricot"), suggestions)

        dictionary.fill("apple", null, null, "ca", suggestions)
        assertEquals(listOf("car", "cake"), suggestions)

        dictionary.fill("unknown", null, null, "", suggestions)
        assertEquals(3, suggestions.size)
        assertEquals("apple", suggestions.first())
    }

    @Test
    fun resolvesExactAndSingleRepeatedLetterGestureCandidates() {
        val dictionary = dictionary(
            Entry("apple", 100, intArrayOf()),
            Entry("banana", 90, intArrayOf())
        )

        assertEquals("apple", dictionary.findGestureCandidate("apple"))
        assertEquals("apple", dictionary.findGestureCandidate("aple"))
        assertEquals(null, dictionary.findGestureCandidate("apxle"))
        assertTrue(dictionary.findGestureCandidate("") == null)
    }

    private data class Entry(val word: String, val frequency: Int, val next: IntArray)

    private fun dictionary(vararg entries: Entry): LocalDictionary {
        val topCount = minOf(3, entries.size)
        val records = entries.map { entry ->
            val word = entry.word.toByteArray(StandardCharsets.UTF_8)
            ByteBuffer.allocate(4 + word.size + entry.next.size * 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putShort(word.size.toShort())
                .put(entry.frequency.toByte())
                .put(entry.next.size.toByte())
                .put(word)
                .apply { entry.next.forEach(::putInt) }
                .array()
        }
        val offsetsOffset = 16 + topCount * 4
        val recordsOffset = offsetsOffset + (entries.size + 1) * 4
        val totalSize = recordsOffset + records.sumOf { it.size }
        val result = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
        result.put(byteArrayOf(
            'K'.code.toByte(), 'R'.code.toByte(), 'D'.code.toByte(), '1'.code.toByte()
        ))
        result.put(1).put(0).putShort(topCount.toShort())
        result.putInt(entries.size).putInt(recordsOffset)
        for (id in 0 until topCount) result.putInt(id)
        var offset = 0
        entries.indices.forEach { id ->
            result.putInt(offsetsOffset + id * 4, offset)
            offset += records[id].size
        }
        result.putInt(offsetsOffset + entries.size * 4, offset)
        result.position(recordsOffset)
        records.forEach(result::put)
        return LocalDictionary(result.array())
    }
}
