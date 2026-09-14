package tenkupng.karuikey

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyHitTestingTest {
    private val widths = floatArrayOf(1.5f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1.5f)

    @Test
    fun ignoresTouchesOutsideTheKeyRange() {
        val bounds = FloatArray(widths.size * 2)
        calculateKeyBounds(320f, 0.5f, 4f, widths, bounds)

        assertEquals(-1, keyColumnAt(bounds[0] - 1f, bounds))
        assertEquals(-1, keyColumnAt(320f, bounds))
        assertEquals(-1, keyColumnAt(bounds[1] + 0.001f, bounds))
        assertEquals(0, keyColumnAt(bounds[0], bounds))
        assertEquals(8, keyColumnAt(bounds[16] + 0.001f, bounds))
    }

    @Test
    fun calculatedBoundsStayInsideAvailableWidth() {
        val bounds = FloatArray(widths.size * 2)
        calculateKeyBounds(320f, 0.5f, 4f, widths, bounds)

        for (column in widths.indices) {
            val left = bounds[column * 2]
            val right = bounds[column * 2 + 1]
            assertTrue(left >= 0f)
            assertTrue(right <= 320f)
            assertTrue(left <= right)
        }
    }
}
